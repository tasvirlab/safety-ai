// Native host/Android CLI; no Python, CUDA, ORT, or Android UI required.
#include "scan.h"
#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>
#include <sys/resource.h>
#include <vector>
using Clock=std::chrono::steady_clock;
static double ms(Clock::time_point t) {return std::chrono::duration<double,std::milli>(Clock::now()-t).count();}
static uint64_t read_int(std::istream& f,int bytes) {
    uint64_t v=0;
    for(int i=0;i<bytes;++i) {int c=f.get();if(c<0)throw std::runtime_error("truncated fixture");v|=uint64_t(c)<<(8*i);}
    return v;
}
static void read_floats(std::istream& f,std::vector<float>& v) {
    f.read(reinterpret_cast<char*>(v.data()),static_cast<std::streamsize>(v.size()*sizeof(float)));
    if(!f)throw std::runtime_error("truncated tensor");
    for(float x:v)if(!std::isfinite(x))throw std::runtime_error("nonfinite fixture value");
}
static double compare(const std::vector<float>& a,const std::vector<float>& b,double atol,double rtol) {
    double maxerr=0.;
    for(size_t i=0;i<a.size();++i) {
        double e=std::abs(double(a[i])-double(b[i]));
        if(!std::isfinite(a[i]) || e>atol+rtol*std::abs(double(b[i])))
            throw std::runtime_error("parity FAILED; do not integrate");
        maxerr=std::max(maxerr,e);
    }
    return maxerr;
}
int main(int argc,char** argv) {
    try {
        ScanConfig cfg{1,1024,5103,16,4,1,4,2};
        int iterations=10;std::string fixture;double atol=2e-5,rtol=2e-4;
        for(int i=1;i<argc;++i) {
            std::string k=argv[i];
            if(i+1>=argc)throw std::runtime_error("option requires a value");
            std::string v=argv[++i];
            if(k=="--threads")cfg.threads=std::stoi(v);
            else if(k=="--variant")cfg.variant=std::stoi(v);
            else if(k=="--iterations")iterations=std::stoi(v);
            else if(k=="--fixture")fixture=v;
            else if(k=="--atol")atol=std::stod(v);
            else if(k=="--rtol")rtol=std::stod(v);
            else throw std::runtime_error("unknown option: "+k);
        }
        if(iterations<1 || iterations>10000 || atol<0 || rtol<0 || !std::isfinite(atol) || !std::isfinite(rtol))
            throw std::runtime_error("invalid iterations or tolerance");
        const uint32_t endian=1;
        if(*reinterpret_cast<const unsigned char*>(&endian)!=1 || sizeof(float)!=4)
            throw std::runtime_error("runner requires little-endian FP32 host");
        auto loading=Clock::now();std::ifstream f;bool supplied_reference=true;
        if(!fixture.empty()) {
            f.open(fixture,std::ios::binary);char magic[8];f.read(magic,8);
            if(!f || (std::string(magic,8)!="PSCAN001" && std::string(magic,8)!="PSCAN002"))throw std::runtime_error("bad fixture header");
            const bool v2=std::string(magic,8)=="PSCAN002";
            int64_t* dims[]={&cfg.batch,&cfg.channels,&cfg.length,&cfg.state,&cfg.groups};
            for(auto p:dims) {
                auto v=read_int(f,8);
                if(v<1 || v>1048576)throw std::runtime_error("fixture dimension outside runner bounds");
                *p=static_cast<int64_t>(v);
            }
            cfg.softplus=static_cast<int32_t>(read_int(f,4));
            if(v2) {
                auto flag=read_int(f,4);
                if(flag>1)throw std::runtime_error("invalid reference flag");
                supplied_reference=flag!=0;
            }
        }
        auto count=[](int64_t a,int64_t b,int64_t c,int64_t d=1) {
            const uint64_t cap=128ULL*1024*1024;uint64_t v=1;
            for(auto x:{a,b,c,d}) {if(x<1 || uint64_t(x)>cap/v)throw std::runtime_error("runner tensor too large");v*=uint64_t(x);}
            return static_cast<size_t>(v);
        };
        auto nu=count(cfg.batch,cfg.channels,cfg.length),na=count(cfg.channels,cfg.state,1);
        auto nbc=count(cfg.batch,cfg.groups,cfg.state,cfg.length);
        std::vector<float> u(nu),delta(nu),A(na),B(nbc),C(nbc),skip(cfg.channels),bias(cfg.channels),y(nu),gold(nu),ref;
        if(f.is_open()) {
            for(auto p:{&u,&delta,&A,&B,&C,&skip,&bias,&gold})read_floats(f,*p);
            if(supplied_reference) {ref.resize(nu);read_floats(f,ref);}
            if(f.peek()!=std::char_traits<char>::eof())throw std::runtime_error("trailing fixture bytes");
        } else {
            uint32_t state=20260918;
            auto fill=[&](std::vector<float>& v,float lo,float hi) {
                for(float& x:v) {state=1664525U*state+1013904223U;x=lo+(hi-lo)*float(state>>8)/16777216.f;}
            };
            fill(u,-.5f,.5f);fill(delta,-6.f,2.f);fill(A,-2.f,-.05f);
            fill(B,-.2f,.2f);fill(C,-.2f,.2f);fill(skip,-.1f,.1f);fill(bias,-.3f,.3f);
        }
        double input_ms=ms(loading);
        auto invoke=[&](const ScanConfig& c,std::vector<float>& out) {
            char error[512];
            if(scan_run(&c,u.data(),delta.data(),A.data(),B.data(),C.data(),skip.data(),bias.data(),nullptr,
                        out.data(),nullptr,error,sizeof(error)))throw std::runtime_error(error);
        };
        auto t=Clock::now();invoke(cfg,y);double first=ms(t);
        if(fixture.empty()) {ScanConfig scalar=cfg;scalar.variant=0;scalar.threads=1;invoke(scalar,gold);}
        double maxerr=compare(y,gold,atol,rtol);
        double referr=ref.empty()?0.:compare(y,ref,atol,rtol);
        struct rusage usage{};getrusage(RUSAGE_SELF,&usage);
        std::cout<<"{\"kind\":\"setup\",\"backend\":\""<<scan_simd_backend()
                 <<"\",\"supplied_fixture\":"<<(!fixture.empty()?"true":"false")
                 <<",\"input_load_or_generation_ms\":"<<input_ms<<",\"first_scan_ms\":"<<first
                 <<",\"max_abs_target\":"<<maxerr<<",\"max_abs_supplied_reference\":"
                 <<(ref.empty()?"null":std::to_string(referr))
                 <<",\"threads\":"<<cfg.threads<<",\"variant\":"<<cfg.variant
                 <<",\"shape\":["<<cfg.batch<<","<<cfg.channels<<","<<cfg.length<<","<<cfg.state<<","<<cfg.groups
                 <<"],\"peak_process_rss_kib\":"<<usage.ru_maxrss<<"}"<<std::endl;
        for(int i=0;i<iterations;++i) {
            t=Clock::now();invoke(cfg,y);double duration=ms(t);
            compare(y,gold,atol,rtol);
            getrusage(RUSAGE_SELF,&usage);
            std::cout<<"{\"kind\":\"warm_scan\",\"iteration\":"<<i<<",\"ms\":"<<duration
                     <<",\"peak_process_rss_kib\":"<<usage.ru_maxrss<<"}"<<std::endl;
        }
        return 0;
    }catch(const std::exception& e) {std::cerr<<e.what()<<std::endl;return 1;}
}
