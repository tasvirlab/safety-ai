#include "scan.h"
#include <cmath>
#include <cstdio>
#include <vector>
int main() {
    // Exact hand case: A=0, dt=1, B=2, C=3, skip=4 => [10,26,48].
    ScanConfig cfg{1,4,3,1,1,0,2,2};
    const float A[4]={0,0,0,0}, B[3]={2,2,2}, C[3]={3,3,3};
    const float skip[4]={4,4,4,4};
    std::vector<float> u(12),delta(12,1),y(12),h(4);
    for(int i=0;i<12;++i)u[i]=float(i%3+1);
    char error[256];
    for(int v=0;v<3;++v) {
        cfg.variant=v;
        if(scan_run(&cfg,u.data(),delta.data(),A,B,C,skip,nullptr,nullptr,y.data(),h.data(),error,sizeof(error))) {
            std::fprintf(stderr,"%s\n",error);return 1;
        }
        const float expected[3]={10,26,48};
        for(int i=0;i<12;++i)if(y[i]!=expected[i%3])return 2;
        for(float x:h)if(x!=12.f)return 3;
    }
    std::printf("hand-computable smoke PASS; SIMD=%s\n",scan_simd_backend());
    return 0;
}
