#include "scan.h"
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <limits>
#include <stdexcept>
#include <thread>
#include <vector>

#if defined(__ARM_NEON)
#include <arm_neon.h>
struct V4 { float32x4_t v; };
static V4 load4(const float* p) { return {vld1q_f32(p)}; }
static void store4(float* p, V4 a) { vst1q_f32(p, a.v); }
static V4 splat(float x) { return {vdupq_n_f32(x)}; }
static V4 mul(V4 a,V4 b) { return {vmulq_f32(a.v,b.v)}; }
static V4 add(V4 a,V4 b) { return {vaddq_f32(a.v,b.v)}; }
#elif defined(__SSE2__)
#include <emmintrin.h>
struct V4 { __m128 v; };
static V4 load4(const float* p) { return {_mm_loadu_ps(p)}; }
static void store4(float* p, V4 a) { _mm_storeu_ps(p,a.v); }
static V4 splat(float x) { return {_mm_set1_ps(x)}; }
static V4 mul(V4 a,V4 b) { return {_mm_mul_ps(a.v,b.v)}; }
static V4 add(V4 a,V4 b) { return {_mm_add_ps(a.v,b.v)}; }
#else
struct V4 { float v[4]; };
static V4 load4(const float* p) { return {{p[0],p[1],p[2],p[3]}}; }
static void store4(float* p,V4 a) { for(int k=0;k<4;++k)p[k]=a.v[k]; }
static V4 splat(float x) { return {{x,x,x,x}}; }
static V4 mul(V4 a,V4 b) { V4 c;for(int k=0;k<4;++k)c.v[k]=a.v[k]*b.v[k];return c; }
static V4 add(V4 a,V4 b) { V4 c;for(int k=0;k<4;++k)c.v[k]=a.v[k]+b.v[k];return c; }
#endif

namespace {
constexpr size_t max_state = 256;
// No approximate exponential: libm per lane is intentionally retained.
V4 exp4(V4 a) {
    float p[4]; store4(p,a);
    for(float& v:p) v=std::exp(v);
    return load4(p);
}
float step_delta(float raw,float bias,bool softplus) {
    const float x=raw+bias;
    return softplus && x<=20.f ? std::log1p(std::exp(x)) : x;
}
size_t product(std::initializer_list<int64_t> dims) {
    size_t n=1;
    for(int64_t d:dims) {
        if(d<=0 || static_cast<uint64_t>(d)>std::numeric_limits<size_t>::max()/sizeof(float)/n)
            throw std::invalid_argument("invalid dimension or byte-size overflow");
        n*=static_cast<size_t>(d);
    }
    return n;
}
struct Task { size_t b,g,d,lanes; };

void run_impl(const ScanConfig& c,const float* u,const float* delta,const float* A,
              const float* B,const float* C,const float* skip,const float* bias,
              const float* h0,float* y,float* hf) {
    product({c.batch,c.channels,c.length});
    product({c.channels,c.state});
    product({c.batch,c.channels,c.state});
    const size_t bc_size=product({c.batch,c.groups,c.state,c.length});
    if(c.channels%c.groups || c.state>static_cast<int64_t>(max_state))
        throw std::invalid_argument("groups must divide channels; state must be <=256");
    if(c.variant<0 || c.variant>2 || c.threads<1 || c.threads>64 || (c.softplus!=0 && c.softplus!=1))
        throw std::invalid_argument("invalid variant/threads/softplus option");
    if(!u || !delta || !A || !B || !C || !y)
        throw std::invalid_argument("null required tensor pointer");
    if(y==u || y==delta || y==A || y==B || y==C || y==h0 || (hf && y==hf))
        throw std::invalid_argument("overlapping buffers are unsupported");
    const size_t nb=c.batch,nd=c.channels,nl=c.length,ns=c.state,ng=c.groups,dc=nd/ng;
    std::vector<float> bp,cp;
    if(c.variant!=0) {
        // Store B/C once per GROUP, never expand them across 1024 channels.
        bp.resize(bc_size);cp.resize(bc_size);
        for(size_t b=0;b<nb;++b) for(size_t g=0;g<ng;++g)
            for(size_t t=0;t<nl;++t) for(size_t n=0;n<ns;++n) {
                const size_t src=((b*ng+g)*ns+n)*nl+t;
                const size_t dst=((b*ng+g)*nl+t)*ns+n;
                bp[dst]=B[src];cp[dst]=C[src];
            }
    }
    std::vector<Task> tasks;
    for(size_t b=0;b<nb;++b) for(size_t g=0;g<ng;++g) {
        for(size_t k=0;k<dc;) {
            const size_t lanes=c.variant==2 && k+4<=dc ? 4 : 1;
            tasks.push_back({b,g,g*dc+k,lanes});k+=lanes;
        }
    }
    auto work=[&](size_t begin,size_t end) {
        for(size_t q=begin;q<end;++q) {
            const auto task=tasks[q];
            const size_t b=task.b,g=task.g,d=task.d,off=(b*nd+d)*nl;
            if(task.lanes==1) {
                float h[max_state];
                for(size_t n=0;n<ns;++n) h[n]=h0?h0[(b*nd+d)*ns+n]:0.f;
                for(size_t t=0;t<nl;++t) {
                    const float dt=step_delta(delta[off+t],bias?bias[d]:0.f,c.softplus);
                    const float du=dt*u[off+t];
                    float sum=0.f;
                    for(size_t n=0;n<ns;++n) {
                        const size_t ix=c.variant==0?((b*ng+g)*ns+n)*nl+t:((b*ng+g)*nl+t)*ns+n;
                        const float bv=c.variant==0?B[ix]:bp[ix];
                        const float cv=c.variant==0?C[ix]:cp[ix];
                        h[n]=std::exp(dt*A[d*ns+n])*h[n]+du*bv;
                        sum=sum+h[n]*cv;
                    }
                    y[off+t]=sum+(skip?skip[d]*u[off+t]:0.f);
                }
                if(hf) for(size_t n=0;n<ns;++n) hf[(b*nd+d)*ns+n]=h[n];
            } else {
                // SIMD across INDEPENDENT CHANNELS. Never change the time scan or
                // the order of the state sum within one output channel.
                V4 h[max_state],av[max_state]; float tmp[4];
                for(size_t n=0;n<ns;++n) {
                    for(size_t k=0;k<4;++k) tmp[k]=h0?h0[(b*nd+d+k)*ns+n]:0.f;
                    h[n]=load4(tmp);
                    for(size_t k=0;k<4;++k) tmp[k]=A[(d+k)*ns+n];
                    av[n]=load4(tmp);
                }
                V4 dv=skip?load4(skip+d):splat(0.f);
                for(size_t t=0;t<nl;++t) {
                    for(size_t k=0;k<4;++k) tmp[k]=u[off+k*nl+t];
                    const V4 uv=load4(tmp);
                    for(size_t k=0;k<4;++k) tmp[k]=step_delta(delta[off+k*nl+t],bias?bias[d+k]:0.f,c.softplus);
                    const V4 dt=load4(tmp),du=mul(dt,uv);
                    V4 sum=splat(0.f);
                    const size_t base=((b*ng+g)*nl+t)*ns;
                    for(size_t n=0;n<ns;++n) {
                        h[n]=add(mul(exp4(mul(dt,av[n])),h[n]),mul(du,splat(bp[base+n])));
                        sum=add(sum,mul(h[n],splat(cp[base+n])));
                    }
                    store4(tmp,add(sum,mul(dv,uv)));
                    for(size_t k=0;k<4;++k)y[off+k*nl+t]=tmp[k];
                }
                if(hf) for(size_t n=0;n<ns;++n) {
                    store4(tmp,h[n]);
                    for(size_t k=0;k<4;++k)hf[(b*nd+d+k)*ns+n]=tmp[k];
                }
            }
        }
    };
    const size_t nt=std::min<size_t>(c.threads,tasks.size());
    // Explicit joining even when thread construction fails.
    std::vector<std::thread> pool;pool.reserve(nt-1);
    try {
        for(size_t t=1;t<nt;++t) pool.emplace_back(work,tasks.size()*t/nt,tasks.size()*(t+1)/nt);
        work(0,tasks.size()/nt);
    } catch(...) {for(auto& t:pool)t.join();throw;}
    for(auto& t:pool)t.join();
}
}

extern "C" int scan_run(const ScanConfig* c,const float* u,const float* delta,const float* A,
                        const float* B,const float* C,const float* skip,const float* bias,
                        const float* h0,float* y,float* hf,char* err,size_t capacity) {
    try {
        if(!c)throw std::invalid_argument("null config");
        run_impl(*c,u,delta,A,B,C,skip,bias,h0,y,hf);
        if(err && capacity)err[0]='\0';
        return 0;
    } catch(const std::exception& e) {if(err && capacity)std::snprintf(err,capacity,"%s",e.what());return 1;}
    catch(...) {if(err && capacity)std::snprintf(err,capacity,"unknown error");return 2;}
}
extern "C" const char* scan_simd_backend(void) {
#if defined(__ARM_NEON)
    return "ARM_NEON";
#elif defined(__SSE2__)
    return "x86_SSE2";
#else
    return "portable_lanes";
#endif
}
