// Integration candidate: requires matching ORT headers/runtime; NOT compiled in
// the delivery environment. The standalone kernel is independently tested.
#define ORT_API_MANUAL_INIT
#include <onnxruntime_cxx_api.h>
#include "scan.h"
#include <array>
#include <limits>
#include <mutex>
#include <stdexcept>
#include <vector>

namespace {
struct ScanKernel {
    const OrtApi& api;
    int32_t softplus, threads, variant;
    ScanKernel(const OrtApi& a, const OrtKernelInfo* info): api(a) {
        auto attr=[&](const char* name) {
            int64_t v=0;
            Ort::ThrowOnError(api.KernelInfoGetAttribute_int64(info,name,&v));
            if(v<std::numeric_limits<int32_t>::min() || v>std::numeric_limits<int32_t>::max())
                throw std::invalid_argument("attribute outside int32 range");
            return static_cast<int32_t>(v);
        };
        softplus=attr("delta_softplus");threads=attr("threads");variant=attr("variant");
        if((softplus!=0 && softplus!=1) || threads<1 || threads>64 || variant<0 || variant>2)
            throw std::invalid_argument("invalid selective scan attributes");
    }
    OrtStatusPtr ComputeV2(OrtKernelContext* raw) noexcept {
        try {
            Ort::KernelContext ctx(raw);
            if(ctx.GetInputCount()!=7 || ctx.GetOutputCount()!=1)
                throw std::invalid_argument("SelectiveScanFP32 requires 7 inputs and 1 output");
            std::array<const float*,7> ptr{};
            std::array<std::vector<int64_t>,7> shapes;
            for(size_t i=0;i<7;++i) {
                auto tensor=ctx.GetInput(i);
                auto ti=tensor.GetTensorTypeAndShapeInfo();
                if(ti.GetElementType()!=ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT)
                    throw std::invalid_argument("only FP32 tensors are supported");
                shapes[i]=ti.GetShape();
                for(auto d:shapes[i]) if(d<=0) throw std::invalid_argument("empty or invalid tensor");
                ptr[i]=tensor.GetTensorData<float>();
            }
            const auto& u=shapes[0];const auto& a=shapes[2];const auto& b=shapes[3];
            if(u.size()!=3 || a.size()!=2 || b.size()!=4)
                throw std::invalid_argument("expected u:BDL, A:DN, B:BGNL");
            const int64_t nb=u[0],nd=u[1],nl=u[2],ns=a[1],ng=b[1];
            if(shapes[1]!=u || a[0]!=nd || b!=std::vector<int64_t>{nb,ng,ns,nl} ||
               shapes[4]!=b || shapes[5]!=std::vector<int64_t>{nd} || shapes[6]!=shapes[5] ||
               nd%ng || ns>256)
                throw std::invalid_argument("inconsistent selective scan tensor shapes");
            auto output=ctx.GetOutput(0,u);
            ScanConfig cfg{nb,nd,nl,ns,ng,softplus,threads,variant};
            char error[512]{};
            const int rc=scan_run(&cfg,ptr[0],ptr[1],ptr[2],ptr[3],ptr[4],ptr[5],ptr[6],
                                  nullptr,output.GetTensorMutableData<float>(),nullptr,error,sizeof(error));
            if(rc) return api.CreateStatus(ORT_FAIL,error);
            return nullptr;
        } catch(const Ort::Exception& e) { return api.CreateStatus(e.GetOrtErrorCode(),e.what()); }
        catch(const std::exception& e) { return api.CreateStatus(ORT_FAIL,e.what()); }
        catch(...) { return api.CreateStatus(ORT_FAIL,"unknown selective scan exception"); }
    }
};

struct ScanOp: Ort::CustomOpBase<ScanOp,ScanKernel,true> {
    const char* GetName() const { return "SelectiveScanFP32"; }
    const char* GetExecutionProviderType() const { return "CPUExecutionProvider"; }
    size_t GetInputTypeCount() const { return 7; }
    size_t GetOutputTypeCount() const { return 1; }
    ONNXTensorElementDataType GetInputType(size_t) const { return ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT; }
    ONNXTensorElementDataType GetOutputType(size_t) const { return ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT; }
    OrtStatusPtr CreateKernelV2(const OrtApi& api,const OrtKernelInfo* info,void** out) const noexcept {
        *out=nullptr;
        try { *out=new ScanKernel(api,info);return nullptr; }
        catch(const Ort::Exception& e) { return api.CreateStatus(e.GetOrtErrorCode(),e.what()); }
        catch(const std::exception& e) { return api.CreateStatus(ORT_FAIL,e.what()); }
        catch(...) { return api.CreateStatus(ORT_FAIL,"kernel construction failed"); }
    }
};
}

// A single op/domain is intentionally retained for the library's process
// lifetime. Do not unload this library while an ORT session can use the op.
extern "C" __attribute__((visibility("default"))) OrtStatus* ORT_API_CALL
RegisterCustomOps(OrtSessionOptions* options,const OrtApiBase* base) {
    const OrtApi* api=base->GetApi(ORT_API_VERSION);
    if(!api) {
        const OrtApi* oldest=base->GetApi(1);
        return oldest->CreateStatus(ORT_FAIL,"ORT runtime older than custom-op headers");
    }
    try {
        static std::mutex lock;
        const std::lock_guard<std::mutex> guard(lock);
        static const OrtApi* initialized_api=nullptr;
        if(initialized_api && initialized_api!=api)
            return api->CreateStatus(ORT_FAIL,"multiple ORT runtime instances unsupported");
        if(!initialized_api) {Ort::InitApi(api);initialized_api=api;}
        static ScanOp op;
        static OrtCustomOpDomain* domain=nullptr;
        if(!domain) {
            OrtCustomOpDomain* pending=nullptr;
            Ort::ThrowOnError(api->CreateCustomOpDomain("org.posemamba.candidate",&pending));
            OrtStatus* status=api->CustomOpDomain_Add(pending,&op);
            if(status) {api->ReleaseCustomOpDomain(pending);return status;}
            domain=pending;
        }
        return api->AddCustomOpDomain(options,domain);
    } catch(const Ort::Exception& e) { return api->CreateStatus(e.GetOrtErrorCode(),e.what()); }
    catch(const std::exception& e) { return api->CreateStatus(ORT_FAIL,e.what()); }
    catch(...) { return api->CreateStatus(ORT_FAIL,"custom-op registration failed"); }
}
