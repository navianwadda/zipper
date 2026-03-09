#include <jni.h>
#include <string>
#include <vector>
#include <set>
#include <map>
#include <mutex>
#include <cstdio>
#include <cstring>
#include <cstdlib>
#include <unistd.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/stat.h>
#include <elf.h>

static std::mutex g_mutex;

static const uint8_t kX  = 0xA7;
static const uint8_t kX2 = 0x5C;

static inline std::string xd(const uint8_t* b, size_t n) {
    std::string s; s.reserve(n);
    for (size_t i = 0; i < n; i++) s += (char)(b[i] ^ kX);
    return s;
}
static std::string ds(const uint8_t* b, size_t n) { return xd(b,n); }

static const uint8_t _s_frida[]       = {0xc1,0xd5,0xce,0xc3,0xc6};
static const uint8_t _s_gumjs[]       = {0xc0,0xd2,0xca,0x8a,0xcd,0xd4,0x8a,0xcb,0xc8,0xc8,0xd7};
static const uint8_t _s_gmain[]       = {0xc0,0xca,0xc6,0xce,0xc9};
static const uint8_t _s_linjector[]   = {0xcb,0xce,0xc9,0xcd,0xc2,0xc4,0xd3,0xc8,0xd5};
static const uint8_t _s_fridaagent[]  = {0xc1,0xd5,0xce,0xc3,0xc6,0x8a,0xc6,0xc0,0xc2,0xc9,0xd3};
static const uint8_t _s_fridagadget[] = {0xc1,0xd5,0xce,0xc3,0xc6,0x8a,0xc0,0xc6,0xc3,0xc0,0xc2,0xd3};
static const uint8_t _s_refrida[]     = {0xd5,0xc2,0x89,0xc1,0xd5,0xce,0xc3,0xc6};
static const uint8_t _s_fridahelper[] = {0xc1,0xd5,0xce,0xc3,0xc6,0x8a,0xcf,0xc2,0xcb,0xd7,0xc2,0xd5};
static const uint8_t _s_fridanode[]   = {0xc1,0xd5,0xce,0xc3,0xc6,0x8a,0xc9,0xc8,0xc3,0xc2};
static const uint8_t _s_maps[]        = {0x88,0xd7,0xd5,0xc8,0xc4,0x88,0xd4,0xc2,0xcb,0xc1,0x88,0xca,0xc6,0xd7,0xd4};
static const uint8_t _s_status[]      = {0x88,0xd7,0xd5,0xc8,0xc4,0x88,0xd4,0xc2,0xcb,0xc1,0x88,0xd4,0xd3,0xc6,0xd3,0xd2,0xd4};
static const uint8_t _s_tracerpid[]   = {0xf3,0xd5,0xc6,0xc4,0xc2,0xd5,0xf7,0xce,0xc3,0x9d};
static const uint8_t _s_fridasrv[]    = {0x88,0xc3,0xc6,0xd3,0xc6,0x88,0xcb,0xc8,0xc4,0xc6,0xcb,0x88,0xd3,0xca,0xd7,0x88,0xc1,0xd5,0xce,0xc3,0xc6,0x8a,0xd4,0xc2,0xd5,0xd1,0xc2,0xd5};
static const uint8_t _s_refridasrv[]  = {0x88,0xc3,0xc6,0xd3,0xc6,0x88,0xcb,0xc8,0xc4,0xc6,0xcb,0x88,0xd3,0xca,0xd7,0x88,0xd5,0xc2,0x89,0xc1,0xd5,0xce,0xc3,0xc6,0x89,0xd4,0xc2,0xd5,0xd1,0xc2,0xd5};
static const uint8_t _s_unix[]        = {0x88,0xd7,0xd5,0xc8,0xc4,0x88,0xc9,0xc2,0xd3,0x88,0xd2,0xc9,0xce,0xdf};
static const uint8_t _s_data[]        = {0x85,0xc3,0xc6,0xd3,0xc6,0x85};
static const uint8_t _s_contact[]     = {0xc4,0xc8,0xc9,0xd3,0xc6,0xc4,0xd3,0xf8,0xd2,0xd5,0xcb};
static const uint8_t _s_cric[]        = {0xc4,0xd5,0xce,0xc4,0xf8,0xcb,0xce,0xd1,0xc2,0xf8,0xd2,0xd5,0xcb};
static const uint8_t _s_foot[]        = {0xc1,0xc8,0xc8,0xd3,0xf8,0xcb,0xce,0xd1,0xc2,0xf8,0xd2,0xd5,0xcb};
static const uint8_t _s_email[]       = {0xc2,0xca,0xc6,0xce,0xcb,0xf8,0xd2,0xd4};
static const uint8_t _s_web[]         = {0xd0,0xc2,0xc5,0xf8,0xd2,0xd5,0xcb};
static const uint8_t _s_message[]     = {0xca,0xc2,0xd4,0xd4,0xc6,0xc0,0xc2};
static const uint8_t _s_messageurl[]  = {0xca,0xc2,0xd4,0xd4,0xc6,0xc0,0xc2,0xf8,0xd2,0xd5,0xcb};
static const uint8_t _s_appver[]      = {0xc6,0xd7,0xd7,0xf8,0xd1,0xc2,0xd5,0xd4,0xce,0xc8,0xc9};
static const uint8_t _s_dlurl[]       = {0xc3,0xc8,0xd0,0xc9,0xcb,0xc8,0xc6,0xc3,0xf8,0xd2,0xd5,0xcb};
static const uint8_t _pt_home[]       = {0xcf,0xc8,0xca,0xc2};
static const uint8_t _pt_channels[]   = {0xc4,0xcf,0xc6,0xc9,0xc9,0xc2,0xcb,0xd4};
static const uint8_t _pt_live[]       = {0xcb,0xce,0xd1,0xc2,0xf8,0xc2,0xd1,0xc2,0xc9,0xd3,0xd4};
static const uint8_t _pt_sports[]     = {0xd4,0xd7,0xc8,0xd5,0xd3,0xd4};

static uint32_t g_self_crc  = 0;
static bool     g_crc_ready = false;

static uint32_t crc32_buf(const uint8_t* buf, size_t len) {
    uint32_t crc = 0xFFFFFFFFu;
    for (size_t i = 0; i < len; i++) {
        crc ^= buf[i];
        for (int j = 0; j < 8; j++) crc = (crc >> 1) ^ (0xEDB88320u & -(crc & 1));
    }
    return crc ^ 0xFFFFFFFFu;
}

static bool walkElf(const uint8_t* base, uint32_t* out_crc) {
    if (!base || base[0]!=0x7f||base[1]!='E'||base[2]!='L'||base[3]!='F') return false;
#if defined(__LP64__)
    const Elf64_Ehdr* ehdr = (const Elf64_Ehdr*)base;
    if (!ehdr->e_shoff || !ehdr->e_shnum || ehdr->e_shentsize < sizeof(Elf64_Shdr)) return false;
    const Elf64_Shdr* shdr = (const Elf64_Shdr*)(base + ehdr->e_shoff);
    for (int i = 0; i < ehdr->e_shnum; i++) {
        if (shdr[i].sh_type == SHT_PROGBITS && (shdr[i].sh_flags & SHF_EXECINSTR) && shdr[i].sh_size > 0 && shdr[i].sh_offset > 0) {
            *out_crc = crc32_buf(base + shdr[i].sh_offset, (size_t)shdr[i].sh_size);
            return true;
        }
    }
#else
    const Elf32_Ehdr* ehdr = (const Elf32_Ehdr*)base;
    if (!ehdr->e_shoff || !ehdr->e_shnum || ehdr->e_shentsize < sizeof(Elf32_Shdr)) return false;
    const Elf32_Shdr* shdr = (const Elf32_Shdr*)(base + ehdr->e_shoff);
    for (int i = 0; i < ehdr->e_shnum; i++) {
        if (shdr[i].sh_type == SHT_PROGBITS && (shdr[i].sh_flags & SHF_EXECINSTR) && shdr[i].sh_size > 0 && shdr[i].sh_offset > 0) {
            *out_crc = crc32_buf(base + shdr[i].sh_offset, (size_t)shdr[i].sh_size);
            return true;
        }
    }
#endif
    return false;
}

static void computeSelfCrc() {
    Dl_info di;
    if (!dladdr((void*)&computeSelfCrc, &di) || !di.dli_fbase) return;
    uint32_t c = 0;
    if (walkElf((const uint8_t*)di.dli_fbase, &c)) { g_self_crc = c; g_crc_ready = true; }
}

static bool isSelfPatched() {
    if (!g_crc_ready) return false;
    Dl_info di;
    if (!dladdr((void*)&computeSelfCrc, &di) || !di.dli_fbase) return true;
    uint32_t c = 0;
    if (!walkElf((const uint8_t*)di.dli_fbase, &c)) return true;
    return c != g_self_crc;
}

static bool isFridaPortOpen() {
    int sock = socket(AF_INET, SOCK_STREAM, 0); if (sock < 0) return false;
    struct timeval tv{0, 200000};
    setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
    struct sockaddr_in addr{}; addr.sin_family=AF_INET; addr.sin_port=htons(27042);
    addr.sin_addr.s_addr=inet_addr("127.0.0.1");
    int r = connect(sock,(struct sockaddr*)&addr,sizeof(addr)); close(sock); return r==0;
}

static bool isFridaInMaps() {
    std::string path = xd(_s_maps, sizeof(_s_maps));
    FILE* f = fopen(path.c_str(), "r"); if (!f) return false;
    char line[512]; bool found = false;
    const std::string m[] = {
        xd(_s_frida,sizeof(_s_frida)), xd(_s_gumjs,sizeof(_s_gumjs)),
        xd(_s_gmain,sizeof(_s_gmain)), xd(_s_linjector,sizeof(_s_linjector)),
        xd(_s_fridaagent,sizeof(_s_fridaagent)), xd(_s_fridagadget,sizeof(_s_fridagadget)),
        xd(_s_refrida,sizeof(_s_refrida)), xd(_s_fridahelper,sizeof(_s_fridahelper)),
        xd(_s_fridanode,sizeof(_s_fridanode)),
    };
    while (fgets(line, sizeof(line), f)) {
        for (const auto& s : m) if (strstr(line, s.c_str())) { found=true; break; }
        if (found) break;
    }
    fclose(f); return found;
}

static bool isFridaPipePresent() {
    std::string p1=xd(_s_fridasrv,sizeof(_s_fridasrv));
    std::string p2=xd(_s_refridasrv,sizeof(_s_refridasrv));
    std::string p3=xd(_s_unix,sizeof(_s_unix));
    std::string fr=xd(_s_frida,sizeof(_s_frida)), li=xd(_s_linjector,sizeof(_s_linjector));
    for (const char* p : {p1.c_str(), p2.c_str(), p3.c_str()}) {
        if (access(p, F_OK) == 0) {
            if (strcmp(p, p3.c_str()) == 0) {
                FILE* f = fopen(p, "r"); if (!f) continue;
                char line[256]; bool found = false;
                while (fgets(line,sizeof(line),f))
                    if (strstr(line,fr.c_str())||strstr(line,li.c_str())) { found=true; break; }
                fclose(f); if (found) return true;
            } else return true;
        }
    }
    return false;
}

static bool isTracerPidNonZero() {
    std::string sp = xd(_s_status,sizeof(_s_status));
    std::string tk = xd(_s_tracerpid,sizeof(_s_tracerpid));
    FILE* f = fopen(sp.c_str(),"r"); if(!f) return false;
    char line[128]; bool t=false;
    while(fgets(line,sizeof(line),f)) {
        if(strncmp(line,tk.c_str(),tk.size())==0) {
            char* end=nullptr; long v=strtol(line+tk.size(),&end,10);
            t=(end!=line+tk.size()&&v!=0); break;
        }
    }
    fclose(f); return t;
}

__attribute__((noinline)) static bool _op1() {
    volatile uint32_t x=(uint32_t)(uintptr_t)&g_mutex;
    return ((x^(x>>16))|1)!=0;
}

static bool isTampered() {
    if(!_op1()) return false;
    if(isSelfPatched())      return true;
    if(isFridaPortOpen())    return true;
    if(isFridaInMaps())      return true;
    if(isFridaPipePresent()) return true;
    if(isTracerPidNonZero()) return true;
    return false;
}

static const size_t kP2Size = 256;
static const size_t kP4Size = 65;

struct z9 {
    bool p1; uint8_t p2[kP2Size]; uint8_t p2s[kP2Size]; size_t p2_len; uint8_t p4[kP4Size];
    bool p5; bool p1s; bool locked;
    std::string p6,p7,p8,p9,p10,p11,p12,p13,p14;
} static g1 = {false,{},{},0,{},false,false,false,"","","","","","","","",""};

static std::map<std::string,long> g2;
static long g2_total=0;
static std::set<std::string> g_allowed;
struct q7 { std::string d; bool ok; } static g3 = {"",false};
static std::string g4=""; static bool g5=false;
static uint8_t g6[32]={0}; static size_t g6_len=0; static bool g7=false,g8=false;
static uint32_t g9=0;

static inline void ws(const std::string& s,uint8_t* out,size_t mx,size_t* lenout=nullptr){
    if(!mx)return; size_t n=s.size()<(mx-1)?s.size():(mx-1);
    for(size_t i=0;i<n;i++) out[i]=(uint8_t)s[i]^kX; out[n]=0;
    if(lenout)*lenout=n;
}
static inline std::string rs(const uint8_t* in,size_t len){
    std::string o; o.reserve(len);
    for(size_t i=0;i<len;i++) o+=(char)(in[i]^kX); return o;
}
static inline void ws2(const std::string& s,uint8_t* out,size_t mx){
    if(!mx)return; size_t n=s.size()<(mx-1)?s.size():(mx-1);
    for(size_t i=0;i<n;i++) out[i]=(uint8_t)s[i]^kX2; out[n]=0;
}
static inline std::string rs2(const uint8_t* in,size_t len){
    std::string o; o.reserve(len);
    for(size_t i=0;i<len;i++) o+=(char)(in[i]^kX2); return o;
}

static uint32_t im32(uint32_t a,uint32_t b){return(uint32_t)((uint64_t)a*b);}
static const char* KC="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+!@#$%&=";
static const size_t KL=70;

static void dkiv(const std::string& pw,uint8_t key[16],uint8_t iv[16]){
    const uint8_t* d=(const uint8_t*)pw.c_str(); size_t n=pw.size();
    uint32_t u=0x811c9dc5u;
    for(size_t i=0;i<n;i++) u=im32(u^d[i],0x1000193u);
    for(int i=0;i<16;i++){uint8_t b=d[i%n];u=im32(u,0x1fu)+(uint32_t)(i^b);key[i]=(uint8_t)KC[u%KL];}
    u=0x811c832au;
    for(size_t i=0;i<n;i++) u=im32(u^d[i],0x1000193u);
    uint32_t idx=0,acc=0;
    while(idx!=0x30u){uint8_t b=d[idx%n];u=im32(u,0x1du)+(acc^(uint32_t)b);iv[idx/3]=(uint8_t)KC[u%KL];idx+=3;acc=(acc+7u)&0xFFFFFFFFu;}
}

static std::vector<uint8_t> b64d(const std::string& in){
    static const char* ch="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::vector<uint8_t> o; int val=0,valb=-8; bool valid=true;
    for(unsigned char c:in){
        if(c=='=')break; const char* p=strchr(ch,(char)c);
        if(!p){valid=false;break;} val=(val<<6)+(int)(p-ch); valb+=6;
        if(valb>=0){o.push_back((uint8_t)((val>>valb)&0xFF));valb-=8;}
    }
    if(!valid)return{}; return o;
}

static const uint8_t isb[256]={
    0x52,0x09,0x6a,0xd5,0x30,0x36,0xa5,0x38,0xbf,0x40,0xa3,0x9e,0x81,0xf3,0xd7,0xfb,
    0x7c,0xe3,0x39,0x82,0x9b,0x2f,0xff,0x87,0x34,0x8e,0x43,0x44,0xc4,0xde,0xe9,0xcb,
    0x54,0x7b,0x94,0x32,0xa6,0xc2,0x23,0x3d,0xee,0x4c,0x95,0x0b,0x42,0xfa,0xc3,0x4e,
    0x08,0x2e,0xa1,0x66,0x28,0xd9,0x24,0xb2,0x76,0x5b,0xa2,0x49,0x6d,0x8b,0xd1,0x25,
    0x72,0xf8,0xf6,0x64,0x86,0x68,0x98,0x16,0xd4,0xa4,0x5c,0xcc,0x5d,0x65,0xb6,0x92,
    0x6c,0x70,0x48,0x50,0xfd,0xed,0xb9,0xda,0x5e,0x15,0x46,0x57,0xa7,0x8d,0x9d,0x84,
    0x90,0xd8,0xab,0x00,0x8c,0xbc,0xd3,0x0a,0xf7,0xe4,0x58,0x05,0xb8,0xb3,0x45,0x06,
    0xd0,0x2c,0x1e,0x8f,0xca,0x3f,0x0f,0x02,0xc1,0xaf,0xbd,0x03,0x01,0x13,0x8a,0x6b,
    0x3a,0x91,0x11,0x41,0x4f,0x67,0xdc,0xea,0x97,0xf2,0xcf,0xce,0xf0,0xb4,0xe6,0x73,
    0x96,0xac,0x74,0x22,0xe7,0xad,0x35,0x85,0xe2,0xf9,0x37,0xe8,0x1c,0x75,0xdf,0x6e,
    0x47,0xf1,0x1a,0x71,0x1d,0x29,0xc5,0x89,0x6f,0xb7,0x62,0x0e,0xaa,0x18,0xbe,0x1b,
    0xfc,0x56,0x3e,0x4b,0xc6,0xd2,0x79,0x20,0x9a,0xdb,0xc0,0xfe,0x78,0xcd,0x5a,0xf4,
    0x1f,0xdd,0xa8,0x33,0x88,0x07,0xc7,0x31,0xb1,0x12,0x10,0x59,0x27,0x80,0xec,0x5f,
    0x60,0x51,0x7f,0xa9,0x19,0xb5,0x4a,0x0d,0x2d,0xe5,0x7a,0x9f,0x93,0xc9,0x9c,0xef,
    0xa0,0xe0,0x3b,0x4d,0xae,0x2a,0xf5,0xb0,0xc8,0xeb,0xbb,0x3c,0x83,0x53,0x99,0x61,
    0x17,0x2b,0x04,0x7e,0xba,0x77,0xd6,0x26,0xe1,0x69,0x14,0x63,0x55,0x21,0x0c,0x7d
};
static const uint8_t fsb[256]={
    0x63,0x7c,0x77,0x7b,0xf2,0x6b,0x6f,0xc5,0x30,0x01,0x67,0x2b,0xfe,0xd7,0xab,0x76,
    0xca,0x82,0xc9,0x7d,0xfa,0x59,0x47,0xf0,0xad,0xd4,0xa2,0xaf,0x9c,0xa4,0x72,0xc0,
    0xb7,0xfd,0x93,0x26,0x36,0x3f,0xf7,0xcc,0x34,0xa5,0xe5,0xf1,0x71,0xd8,0x31,0x15,
    0x04,0xc7,0x23,0xc3,0x18,0x96,0x05,0x9a,0x07,0x12,0x80,0xe2,0xeb,0x27,0xb2,0x75,
    0x09,0x83,0x2c,0x1a,0x1b,0x6e,0x5a,0xa0,0x52,0x3b,0xd6,0xb3,0x29,0xe3,0x2f,0x84,
    0x53,0xd1,0x00,0xed,0x20,0xfc,0xb1,0x5b,0x6a,0xcb,0xbe,0x39,0x4a,0x4c,0x58,0xcf,
    0xd0,0xef,0xaa,0xfb,0x43,0x4d,0x33,0x85,0x45,0xf9,0x02,0x7f,0x50,0x3c,0x9f,0xa8,
    0x51,0xa3,0x40,0x8f,0x92,0x9d,0x38,0xf5,0xbc,0xb6,0xda,0x21,0x10,0xff,0xf3,0xd2,
    0xcd,0x0c,0x13,0xec,0x5f,0x97,0x44,0x17,0xc4,0xa7,0x7e,0x3d,0x64,0x5d,0x19,0x73,
    0x60,0x81,0x4f,0xdc,0x22,0x2a,0x90,0x88,0x46,0xee,0xb8,0x14,0xde,0x5e,0x0b,0xdb,
    0xe0,0x32,0x3a,0x0a,0x49,0x06,0x24,0x5c,0xc2,0xd3,0xac,0x62,0x91,0x95,0xe4,0x79,
    0xe7,0xc8,0x37,0x6d,0x8d,0xd5,0x4e,0xa9,0x6c,0x56,0xf4,0xea,0x65,0x7a,0xae,0x08,
    0xba,0x78,0x25,0x2e,0x1c,0xa6,0xb4,0xc6,0xe8,0xdd,0x74,0x1f,0x4b,0xbd,0x8b,0x8a,
    0x70,0x3e,0xb5,0x66,0x48,0x03,0xf6,0x0e,0x61,0x35,0x57,0xb9,0x86,0xc1,0x1d,0x9e,
    0xe1,0xf8,0x98,0x11,0x69,0xd9,0x8e,0x94,0x9b,0x1e,0x87,0xe9,0xce,0x55,0x28,0xdf,
    0x8c,0xa1,0x89,0x0d,0xbf,0xe6,0x42,0x68,0x41,0x99,0x2d,0x0f,0xb0,0x54,0xbb,0x16
};
static uint8_t gm(uint8_t a,uint8_t b){uint8_t p=0;for(int i=0;i<8;i++){if(b&1)p^=a;bool h=(a&0x80)!=0;a<<=1;if(h)a^=0x1b;b>>=1;}return p;}
static void ake(const uint8_t k[16],uint8_t rk[11][16]){
    static const uint8_t rc[10]={0x01,0x02,0x04,0x08,0x10,0x20,0x40,0x80,0x1b,0x36};
    memcpy(rk[0],k,16);
    for(int r=1;r<=10;r++){uint8_t t[4];memcpy(t,rk[r-1]+12,4);uint8_t t0=t[0];t[0]=t[1];t[1]=t[2];t[2]=t[3];t[3]=t0;for(int i=0;i<4;i++)t[i]=fsb[t[i]];t[0]^=rc[r-1];for(int i=0;i<16;i++)rk[r][i]=rk[r-1][i]^(i<4?t[i]:rk[r][i-4]);}
}
static void adb(const uint8_t in[16],uint8_t out[16],const uint8_t rk[11][16]){
    uint8_t s[16];memcpy(s,in,16);
    for(int i=0;i<16;i++)s[i]^=rk[10][i];
    for(int r=9;r>=1;r--){
        uint8_t t;
        t=s[13];s[13]=s[9];s[9]=s[5];s[5]=s[1];s[1]=t;
        t=s[10];s[10]=s[2];s[2]=t;t=s[14];s[14]=s[6];s[6]=t;
        t=s[3];s[3]=s[7];s[7]=s[11];s[11]=s[15];s[15]=t;
        for(int i=0;i<16;i++)s[i]=isb[s[i]];
        for(int i=0;i<16;i++)s[i]^=rk[r][i];
        for(int c=0;c<4;c++){uint8_t s0=s[c*4],s1=s[c*4+1],s2=s[c*4+2],s3=s[c*4+3];s[c*4+0]=gm(0x0e,s0)^gm(0x0b,s1)^gm(0x0d,s2)^gm(0x09,s3);s[c*4+1]=gm(0x09,s0)^gm(0x0e,s1)^gm(0x0b,s2)^gm(0x0d,s3);s[c*4+2]=gm(0x0d,s0)^gm(0x09,s1)^gm(0x0e,s2)^gm(0x0b,s3);s[c*4+3]=gm(0x0b,s0)^gm(0x0d,s1)^gm(0x09,s2)^gm(0x0e,s3);}
    }
    uint8_t t;
    t=s[13];s[13]=s[9];s[9]=s[5];s[5]=s[1];s[1]=t;
    t=s[10];s[10]=s[2];s[2]=t;t=s[14];s[14]=s[6];s[6]=t;
    t=s[3];s[3]=s[7];s[7]=s[11];s[11]=s[15];s[15]=t;
    for(int i=0;i<16;i++)s[i]=isb[s[i]];
    for(int i=0;i<16;i++)s[i]^=rk[0][i];
    memcpy(out,s,16);
}
static std::string adec(const std::string& b64ct,const std::string& pw){
    if(pw.empty())return"";
    try{
        std::vector<uint8_t> ct=b64d(b64ct);
        if(ct.empty()||ct.size()%16!=0)return"";
        uint8_t key[16],iv[16];dkiv(pw,key,iv);
        uint8_t rk[11][16];ake(key,rk);
        std::vector<uint8_t> pt(ct.size());
        uint8_t prev[16];memcpy(prev,iv,16);
        for(size_t blk=0;blk<ct.size()/16;blk++){uint8_t dec[16];adb(ct.data()+blk*16,dec,rk);for(int i=0;i<16;i++)pt[blk*16+i]=dec[i]^prev[i];memcpy(prev,ct.data()+blk*16,16);}
        if(pt.empty())return"";
        uint8_t pad=pt.back();
        if(pad<1||pad>16||(size_t)pad>pt.size())return"";
        for(size_t i=pt.size()-pad;i<pt.size();i++)if(pt[i]!=pad)return"";
        pt.resize(pt.size()-pad);
        return std::string(pt.begin(),pt.end());
    }catch(...){return"";}
}
static std::string xenc(const std::string& b64ct){
    if(!g7)return"";
    std::string pw;pw.reserve(g6_len);
    for(size_t i=0;i<g6_len;i++)pw+=(char)(g6[i]^0x3F);
    return adec(b64ct,pw);
}

static std::string epd(const std::string& json){
    std::string key=xd(_s_data,sizeof(_s_data));
    size_t dp=json.find(key);if(dp==std::string::npos)return"";
    size_t cp=json.find(':',dp);if(cp==std::string::npos)return"";
    size_t vs=cp+1;while(vs<json.size()&&(json[vs]==' '||json[vs]=='\t'||json[vs]=='\n'))vs++;
    if(vs>=json.size()||json[vs]!='"')return"";
    size_t es=vs+1,ee=es;
    while(ee<json.size()){if(json[ee]=='\\'){ee+=2;continue;}if(json[ee]=='"')break;ee++;}
    if(ee>=json.size())return"";
    return json.substr(es,ee-es);
}
static std::string exa(const std::string& json,const std::string& key){
    try{
        std::string sk="\""+key+"\"";size_t kp=json.find(sk);if(kp==std::string::npos)return"[]";
        size_t sp=json.find('[',kp);if(sp==std::string::npos)return"[]";
        int bc=1;size_t ep=sp+1;bool ins=false;
        while(ep<json.length()&&bc>0){char c=json[ep];if(ins){if(c=='\\'){ep+=2;continue;}if(c=='"')ins=false;}else{if(c=='"')ins=true;else if(c=='[')bc++;else if(c==']')bc--;}ep++;}
        if(bc!=0)return"[]";
        return json.substr(sp,ep-sp);
    }catch(...){return"[]";}
}
static std::string exs(const std::string& json,size_t from,const std::string& field){
    size_t fp=json.find("\""+field+"\"",from);if(fp==std::string::npos)return"";
    size_t cp=json.find(':',fp);if(cp==std::string::npos)return"";
    size_t qs=json.find('"',cp);if(qs==std::string::npos)return"";
    qs++;size_t qe=qs;
    while(qe<json.size()){if(json[qe]=='\\'){qe+=2;continue;}if(json[qe]=='"')break;qe++;}
    if(qe>=json.size())return"";
    return json.substr(qs,qe-qs);
}
static void elc(const std::string& json){
    try{
        static const uint8_t f1[]={0xcb,0xce,0xd4,0xd3,0xc2,0xc9,0xc2,0xd5,0xf8,0xc4,0xc8,0xc9,0xc1,0xce,0xc0};
        static const uint8_t f2[]={0xc2,0xc9,0xc6,0xc5,0xcb,0xc2,0xf8,0xc3,0xce,0xd5,0xc2,0xc4,0xd3,0xf8,0xcb,0xce,0xc9,0xcc};
        static const uint8_t f3[]={0xc3,0xce,0xd5,0xc2,0xc4,0xd3,0xf8,0xcb,0xce,0xc9,0xcc,0xf8,0xd2,0xd5,0xcb};
        static const uint8_t f4[]={0xc6,0xcb,0xcb,0xc8,0xd0,0xc2,0xc3,0xf8,0xd7,0xc6,0xc0,0xc2,0xd4};
        std::string lc=xd(f1,sizeof(f1));
        size_t cp=json.find("\""+lc+"\"");if(cp==std::string::npos)return;
        size_t ob=json.find('{',cp);if(ob==std::string::npos)return;
        size_t obj_end=ob+1;int depth=1;bool ins=false;
        while(obj_end<json.size()&&depth>0){
            char c=json[obj_end];
            if(ins){if(c=='\\'){ obj_end+=2;continue;}if(c=='"')ins=false;}
            else{if(c=='"')ins=true;else if(c=='{')depth++;else if(c=='}')depth--;}
            obj_end++;
        }
        if(depth!=0)return;
        std::string obj=json.substr(ob,obj_end-ob);
        std::string edl=xd(f2,sizeof(f2));
        size_t ep2=obj.find("\""+edl+"\"");
        if(ep2!=std::string::npos){
            size_t col=obj.find(':',ep2+edl.size()+2);
            if(col!=std::string::npos){
                size_t vs=col+1;
                while(vs<obj.size()&&(obj[vs]==' '||obj[vs]=='\t'||obj[vs]=='\n'))vs++;
                if(vs+4<=obj.size()&&obj.substr(vs,4)=="true")g1.p1=true;
                else if(vs+5<=obj.size()&&obj.substr(vs,5)=="false")g1.p1=false;
            }
        }
        std::string dlu=xd(f3,sizeof(f3));
        std::string urlval=exs(obj,0,dlu);
        ws(urlval,g1.p2,kP2Size,&g1.p2_len); ws2(urlval,g1.p2s,kP2Size);
        g1.p4[0]=0;
        std::string apf=xd(f4,sizeof(f4));
        size_t pp=obj.find("\""+apf+"\"");
        if(pp!=std::string::npos){
            size_t as=obj.find('[',pp);
            if(as!=std::string::npos){
                size_t ae=as+1;int adepth=1;bool ains=false;
                while(ae<obj.size()&&adepth>0){char ac=obj[ae];if(ains){if(ac=='\\'){ae+=2;continue;}if(ac=='"')ains=false;}else{if(ac=='"')ains=true;else if(ac=='[')adepth++;else if(ac==']')adepth--;}ae++;}
                if(adepth==0&&ae>as+1){
                    std::string pa=obj.substr(as+1,ae-as-2);
                    size_t pos=0,pi=0;
                    const size_t kMaxP4=kP4Size-1;
                    while(pos<pa.length()&&pi<kMaxP4){
                        size_t qs=pa.find('"',pos);if(qs==std::string::npos)break;
                        size_t qe=pa.find('"',qs+1);if(qe==std::string::npos)break;
                        std::string pn=pa.substr(qs+1,qe-qs-1);
                        for(size_t ci=0;ci<pn.size()&&pi<kMaxP4-1;ci++)g1.p4[pi++]=(uint8_t)pn[ci]^kX;
                        if(pi<kMaxP4)g1.p4[pi++]=0xFF;
                        pos=qe+1;
                    }
                    if(pi<kP4Size)g1.p4[pi]=0;
                }
            }
        }
        g1.p5=true;g1.p1s=g1.p1;
        g1.p6 =exs(obj,0,xd(_s_contact,   sizeof(_s_contact)));
        g1.p7 =exs(obj,0,xd(_s_cric,       sizeof(_s_cric)));
        g1.p8 =exs(obj,0,xd(_s_foot,       sizeof(_s_foot)));
        g1.p9 =exs(obj,0,xd(_s_email,      sizeof(_s_email)));
        g1.p10=exs(obj,0,xd(_s_web,        sizeof(_s_web)));
        g1.p11=exs(obj,0,xd(_s_message,    sizeof(_s_message)));
        g1.p12=exs(obj,0,xd(_s_messageurl, sizeof(_s_messageurl)));
        g1.p13=exs(obj,0,xd(_s_appver,     sizeof(_s_appver)));
        g1.p14=exs(obj,0,xd(_s_dlurl,      sizeof(_s_dlurl)));
    }catch(...){}
}

static inline bool isPageAllowed(const std::string& pt){
    if(!g1.p5)return false;
    if(!g1.p1)return true;
    return g_allowed.find(pt)!=g_allowed.end();
}

static jboolean impl_validateIntegrity(JNIEnv*,jobject){return isTampered()?JNI_FALSE:JNI_TRUE;}

static jstring impl_getConfigKey(JNIEnv* env,jobject){
    static const uint8_t k[]={0xc3,0xc6,0xd3,0xc6,0xf8,0xc8,0xc5,0xcd,0xc2,0xc4,0xd3,0xf8,0xd2,0xd5,0xcb};
    return env->NewStringUTF(xd(k,sizeof(k)).c_str());
}

static void impl_updateConfig(JNIEnv* env,jobject obj,jstring key){
    static const uint8_t cn[]={0xe9,0xc6,0xd3,0xce,0xd1,0xc2,0xe3,0xc6,0xd3,0xc6,0xf5,0xc2,0xd7,0xc8,0xd4,0xce,0xd3,0xc8,0xd5,0xde};
    std::string expected=xd(cn,sizeof(cn));
    jclass cls=env->GetObjectClass(obj);
    jclass clsCls=env->FindClass("java/lang/Class");
    if(!clsCls){env->ExceptionClear();env->DeleteLocalRef(cls);return;}
    jmethodID gsn=env->GetMethodID(clsCls,"getSimpleName","()Ljava/lang/String;");
    if(!gsn){env->ExceptionClear();env->DeleteLocalRef(clsCls);env->DeleteLocalRef(cls);return;}
    jstring jname=(jstring)env->CallObjectMethod(cls,gsn);
    if(env->ExceptionCheck()){env->ExceptionClear();env->DeleteLocalRef(clsCls);env->DeleteLocalRef(cls);return;}
    bool valid=false;
    if(jname){
        const char* cname=env->GetStringUTFChars(jname,nullptr);
        valid=(cname&&std::string(cname)==expected);
        if(cname)env->ReleaseStringUTFChars(jname,cname);
        env->DeleteLocalRef(jname);
    }
    env->DeleteLocalRef(clsCls);
    env->DeleteLocalRef(cls);
    if(!valid){std::lock_guard<std::mutex> lk(g_mutex);g7=false;g8=false;g6_len=0;memset(g6,0,sizeof(g6));return;}
    if(!key)return;
    const char* ks=env->GetStringUTFChars(key,nullptr);
    if(ks){
        size_t n=strlen(ks);if(n>31)n=31;
        uint8_t tmp[32]={0};
        for(size_t i=0;i<n;i++)tmp[i]=(uint8_t)ks[i]^0x3F;
        uint32_t hash=(uint32_t)(n*0x9e3779b9^0x6c62272e);
        env->ReleaseStringUTFChars(key,ks);
        std::lock_guard<std::mutex> lk(g_mutex);
        memset(g6,0,sizeof(g6));memcpy(g6,tmp,n);g6_len=n;
        g7=true;g8=true;g9=hash;
    }
}

static jboolean impl_storeData(JNIEnv* env,jobject,jstring jsonData){
    if(!jsonData)return JNI_FALSE;
    std::lock_guard<std::mutex> lk(g_mutex);
    const char* js=env->GetStringUTFChars(jsonData,nullptr);if(!js)return JNI_FALSE;
    std::string raw(js);env->ReleaseStringUTFChars(jsonData,js);
    try{
        std::string json=raw,ep=epd(raw);
        
        
        if(!ep.empty()&&g7&&g8){std::string dec=xenc(ep);if(!dec.empty())json=dec;}
        g3.d=json;g3.ok=true;if(!g1.locked){elc(g3.d);if(!g1.p5)elc(raw);}return JNI_TRUE;
    }catch(...){return JNI_FALSE;}
}

static jstring impl_getCategories(JNIEnv* env,jobject){
    std::lock_guard<std::mutex> lk(g_mutex);
    if(!g3.ok)return env->NewStringUTF("[]");
    if(!isPageAllowed(xd(_pt_home,sizeof(_pt_home))))return env->NewStringUTF("[]");
    return env->NewStringUTF(exa(g3.d,"categories").c_str());
}
static jstring impl_getChannels(JNIEnv* env,jobject){
    std::lock_guard<std::mutex> lk(g_mutex);
    if(!g3.ok)return env->NewStringUTF("[]");
    if(!isPageAllowed(xd(_pt_channels,sizeof(_pt_channels))))return env->NewStringUTF("[]");
    return env->NewStringUTF(exa(g3.d,"channels").c_str());
}
static jstring impl_getLiveEvents(JNIEnv* env,jobject){
    std::lock_guard<std::mutex> lk(g_mutex);
    if(!g3.ok)return env->NewStringUTF("[]");
    if(!isPageAllowed(xd(_pt_live,sizeof(_pt_live))))return env->NewStringUTF("[]");
    std::string r=exa(g3.d,"live_events");if(r=="[]")r=exa(g3.d,"liveEvents");
    return env->NewStringUTF(r.c_str());
}
static jstring impl_getExternalLiveEvents(JNIEnv* env,jobject){
    std::lock_guard<std::mutex> lk(g_mutex);
    if(!g3.ok)return env->NewStringUTF("[]");
    return env->NewStringUTF(exa(g3.d,"external_live_events").c_str());
}
static jstring impl_getEventCategories(JNIEnv* env,jobject){
    std::lock_guard<std::mutex> lk(g_mutex);
    if(!g3.ok)return env->NewStringUTF("[]");
    if(!isPageAllowed(xd(_pt_live,sizeof(_pt_live))))return env->NewStringUTF("[]");
    std::string r=exa(g3.d,"event_categories");if(r=="[]")r=exa(g3.d,"eventCategories");
    return env->NewStringUTF(r.c_str());
}
static jstring impl_getSports(JNIEnv* env,jobject){
    std::lock_guard<std::mutex> lk(g_mutex);
    if(!g3.ok)return env->NewStringUTF("[]");
    if(!isPageAllowed(xd(_pt_sports,sizeof(_pt_sports))))return env->NewStringUTF("[]");
    std::string r=exa(g3.d,"sports_slug");if(r=="[]")r=exa(g3.d,"sports");
    return env->NewStringUTF(r.c_str());
}
static jboolean impl_isDataLoaded(JNIEnv* env,jobject){
    std::lock_guard<std::mutex> lk(g_mutex);
    return g3.ok?JNI_TRUE:JNI_FALSE;
}
static void impl_storeConfigUrl(JNIEnv* env,jobject,jstring url){
    if(!url)return;
    const char* us=env->GetStringUTFChars(url,nullptr);
    if(us){std::lock_guard<std::mutex> lk(g_mutex);g4=std::string(us);g5=true;env->ReleaseStringUTFChars(url,us);}
}
static jstring impl_getConfigUrl(JNIEnv* env,jobject){
    std::lock_guard<std::mutex> lk(g_mutex);return env->NewStringUTF(g5?g4.c_str():"");
}

static jboolean impl_shouldShowLink(JNIEnv* env,jobject,jstring pageType,jstring,jlong maxPerPage,jlong maxTotal){
    if(isTampered()){
        std::lock_guard<std::mutex> lk(g_mutex);
        g1.locked=true;g1.p1=false;g1.p1s=false;g1.p5=false;memset(g1.p2,0,sizeof(g1.p2));memset(g1.p2s,0,sizeof(g1.p2s));g1.p2_len=0;
        return JNI_FALSE;
    }
    std::lock_guard<std::mutex> lk(g_mutex);
    if(g1.locked)return JNI_FALSE;
    if(!g8||!g7){return JNI_FALSE;}
    if(g1.p1!=g1.p1s){g1.locked=true;g1.p1=false;g1.p1s=false;g1.p5=false;return JNI_FALSE;}
    std::string u1=rs(g1.p2,g1.p2_len),u2=rs2(g1.p2s,g1.p2_len);
    if(u1!=u2){g1.locked=true;g1.p1=false;g1.p1s=false;g1.p5=false;memset(g1.p2,0,sizeof(g1.p2));memset(g1.p2s,0,sizeof(g1.p2s));g1.p2_len=0;return JNI_FALSE;}
    if(!g1.p5)return JNI_FALSE;
    if(!pageType)return JNI_FALSE;
    const char* pts=env->GetStringUTFChars(pageType,nullptr);if(!pts)return JNI_FALSE;
    std::string pt(pts);env->ReleaseStringUTFChars(pageType,pts);
    size_t i=0;bool found=false;std::string cur;
    while(i<kP4Size&&g1.p4[i]!=0){
        if(g1.p4[i]==0xFF){if(cur==pt){found=true;break;}cur.clear();}
        else cur+=(char)(g1.p4[i]^kX);
        i++;
    }
    if(!found&&!cur.empty()&&cur==pt)found=true;
    if(!found)return JNI_FALSE;
    g_allowed.insert(pt);
    if(!g1.p1)return JNI_FALSE;
    if(maxTotal>0&&(jlong)g2_total>=maxTotal)return JNI_FALSE;
    long pageCount=g2.count(pt)?g2[pt]:0;
    if(maxPerPage>0&&(jlong)pageCount>=maxPerPage)return JNI_FALSE;
    g2[pt]=pageCount+1;g2_total++;return JNI_TRUE;
}
static jstring impl_getDirectLinkUrl(JNIEnv* env,jobject){
    std::lock_guard<std::mutex> lk(g_mutex);
    if(g1.locked)return env->NewStringUTF("");
    std::string u1=rs(g1.p2,g1.p2_len),u2=rs2(g1.p2s,g1.p2_len);
    if(u1!=u2){g1.locked=true;g1.p1=false;g1.p1s=false;g1.p5=false;memset(g1.p2,0,sizeof(g1.p2));memset(g1.p2s,0,sizeof(g1.p2s));g1.p2_len=0;return env->NewStringUTF("");}
    return env->NewStringUTF(u1.c_str());
}
static void impl_resetSessions(JNIEnv*,jobject){
    std::lock_guard<std::mutex> lk(g_mutex);g2.clear();g2_total=0;g_allowed.clear();
}
static jboolean impl_isConfigValid(JNIEnv*,jobject){
    if(isTampered()){
        std::lock_guard<std::mutex> lk(g_mutex);
        g1.locked=true;g1.p1=false;g1.p1s=false;g1.p5=false;return JNI_FALSE;
    }
    std::lock_guard<std::mutex> lk(g_mutex);
    return g1.p5?JNI_TRUE:JNI_FALSE;
}
static jstring impl_getContactUrl (JNIEnv* e,jobject){std::lock_guard<std::mutex> lk(g_mutex);return e->NewStringUTF(g1.p6.c_str());}
static jstring impl_getCricLiveUrl(JNIEnv* e,jobject){std::lock_guard<std::mutex> lk(g_mutex);return e->NewStringUTF(g1.p7.c_str());}
static jstring impl_getFootLiveUrl(JNIEnv* e,jobject){std::lock_guard<std::mutex> lk(g_mutex);return e->NewStringUTF(g1.p8.c_str());}
static jstring impl_getEmailUs   (JNIEnv* e,jobject){std::lock_guard<std::mutex> lk(g_mutex);return e->NewStringUTF(g1.p9.c_str());}
static jstring impl_getWebUrl    (JNIEnv* e,jobject){std::lock_guard<std::mutex> lk(g_mutex);return e->NewStringUTF(g1.p10.c_str());}
static jstring impl_getMessage   (JNIEnv* e,jobject){std::lock_guard<std::mutex> lk(g_mutex);return e->NewStringUTF(g1.p11.c_str());}
static jstring impl_getMessageUrl(JNIEnv* e,jobject){std::lock_guard<std::mutex> lk(g_mutex);return e->NewStringUTF(g1.p12.c_str());}
static jstring impl_getAppVersion(JNIEnv* e,jobject){std::lock_guard<std::mutex> lk(g_mutex);return e->NewStringUTF(g1.p13.c_str());}
static jstring impl_getDownloadUrl(JNIEnv* e,jobject){std::lock_guard<std::mutex> lk(g_mutex);return e->NewStringUTF(g1.p14.c_str());}

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    computeSelfCrc();
    JNIEnv* env=nullptr;
    if(vm->GetEnv((void**)&env,JNI_VERSION_1_6)!=JNI_OK)return JNI_ERR;

    
    static const uint8_t mn_vi[]  ={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xf1,0xc6,0xcb,0xce,0xc3,0xc6,0xd3,0xc2,0xee,0xc9,0xd3,0xc2,0xc0,0xd5,0xce,0xd3,0xde};
    static const uint8_t ms_z[]   ={0x8f,0x8e,0xfd};
    static const uint8_t mn_gck[] ={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xe0,0xc2,0xd3,0xe4,0xc8,0xc9,0xc1,0xce,0xc0,0xec,0xc2,0xde};
    static const uint8_t ms_str[] ={0x8f,0x8e,0xeb,0xcd,0xc6,0xd1,0xc6,0x88,0xcb,0xc6,0xc9,0xc0,0x88,0xf4,0xd3,0xd5,0xce,0xc9,0xc0,0x9c};
    static const uint8_t mn_uc[]  ={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xf2,0xd7,0xc3,0xc6,0xd3,0xc2,0xe4,0xc8,0xc9,0xc1,0xce,0xc0};
    static const uint8_t ms_sv[]  ={0x8f,0xeb,0xcd,0xc6,0xd1,0xc6,0x88,0xcb,0xc6,0xc9,0xc0,0x88,0xf4,0xd3,0xd5,0xce,0xc9,0xc0,0x9c,0x8e,0xf1};
    static const uint8_t mn_scu[] ={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xf4,0xd3,0xc8,0xd5,0xc2,0xe4,0xc8,0xc9,0xc1,0xce,0xc0,0xf2,0xd5,0xcb};
    static const uint8_t mn_gcu[] ={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xe0,0xc2,0xd3,0xe4,0xc8,0xc9,0xc1,0xce,0xc0,0xf2,0xd5,0xcb};
    static const uint8_t mn_sd[]  ={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xf4,0xd3,0xc8,0xd5,0xc2,0xe3,0xc6,0xd3,0xc6};
    static const uint8_t ms_sz[]  ={0x8f,0xeb,0xcd,0xc6,0xd1,0xc6,0x88,0xcb,0xc6,0xc9,0xc0,0x88,0xf4,0xd3,0xd5,0xce,0xc9,0xc0,0x9c,0x8e,0xfd};
    static const uint8_t mn_gc[]  ={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xe0,0xc2,0xd3,0xe4,0xc6,0xd3,0xc2,0xc0,0xc8,0xd5,0xce,0xc2,0xd4};
    static const uint8_t mn_gch[] ={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xe0,0xc2,0xd3,0xe4,0xcf,0xc6,0xc9,0xc9,0xc2,0xcb,0xd4};
    static const uint8_t mn_gle[] ={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xe0,0xc2,0xd3,0xeb,0xce,0xd1,0xc2,0xe2,0xd1,0xc2,0xc9,0xd3,0xd4};
    static const uint8_t mn_idl[] ={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xee,0xd4,0xe3,0xc6,0xd3,0xc6,0xeb,0xc8,0xc6,0xc3,0xc2,0xc3};
    static const uint8_t mn_gec[] ={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xe0,0xc2,0xd3,0xe2,0xd1,0xc2,0xc9,0xd3,0xe4,0xc6,0xd3,0xc2,0xc0,0xc8,0xd5,0xce,0xc2,0xd4};
    static const uint8_t mn_gsp[] ={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xe0,0xc2,0xd3,0xf4,0xd7,0xc8,0xd5,0xd3,0xd4};
    static const uint8_t mn_gele[]={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xe0,0xc2,0xd3,0xe2,0xdf,0xd3,0xc2,0xd5,0xc9,0xc6,0xcb,0xeb,0xce,0xd1,0xc2,0xe2,0xd1,0xc2,0xc9,0xd3,0xd4};
    static const uint8_t mn_ssl[] ={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xf4,0xcf,0xc8,0xd2,0xcb,0xc3,0xf4,0xcf,0xc8,0xd0,0xeb,0xce,0xc9,0xcc};
    static const uint8_t ms_ssl[] ={0x8f,0xeb,0xcd,0xc6,0xd1,0xc6,0x88,0xcb,0xc6,0xc9,0xc0,0x88,0xf4,0xd3,0xd5,0xce,0xc9,0xc0,0x9c,0xeb,0xcd,0xc6,0xd1,0xc6,0x88,0xcb,0xc6,0xc9,0xc0,0x88,0xf4,0xd3,0xd5,0xce,0xc9,0xc0,0x9c,0xed,0xed,0x8e,0xfd};
    static const uint8_t mn_gdlu[]={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xe0,0xc2,0xd3,0xe3,0xce,0xd5,0xc2,0xc4,0xd3,0xeb,0xce,0xc9,0xcc,0xf2,0xd5,0xcb};
    static const uint8_t mn_rs[]  ={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xf5,0xc2,0xd4,0xc2,0xd3,0xf4,0xc2,0xd4,0xd4,0xce,0xc8,0xc9,0xd4};
    static const uint8_t ms_v[]   ={0x8f,0x8e,0xf1};
    static const uint8_t mn_icv[] ={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xee,0xd4,0xe4,0xc8,0xc9,0xc1,0xce,0xc0,0xf1,0xc6,0xcb,0xce,0xc3};
    static const uint8_t mn_gco[] ={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xe0,0xc2,0xd3,0xe4,0xc8,0xc9,0xd3,0xc6,0xc4,0xd3,0xf2,0xd5,0xcb};
    static const uint8_t mn_gcr[] ={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xe0,0xc2,0xd3,0xe4,0xd5,0xce,0xc4,0xeb,0xce,0xd1,0xc2,0xf2,0xd5,0xcb};
    static const uint8_t mn_gfl[] ={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xe0,0xc2,0xd3,0xe1,0xc8,0xc8,0xd3,0xeb,0xce,0xd1,0xc2,0xf2,0xd5,0xcb};
    static const uint8_t mn_geu[] ={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xe0,0xc2,0xd3,0xe2,0xca,0xc6,0xce,0xcb,0xf2,0xd4};
    static const uint8_t mn_gwu[] ={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xe0,0xc2,0xd3,0xf0,0xc2,0xc5,0xf2,0xd5,0xcb};
    static const uint8_t mn_gm[]  ={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xe0,0xc2,0xd3,0xea,0xc2,0xd4,0xd4,0xc6,0xc0,0xc2};
    static const uint8_t mn_gmu[] ={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xe0,0xc2,0xd3,0xea,0xc2,0xd4,0xd4,0xc6,0xc0,0xc2,0xf2,0xd5,0xcb};
    static const uint8_t mn_gav[] ={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xe0,0xc2,0xd3,0xe6,0xd7,0xd7,0xf1,0xc2,0xd5,0xd4,0xce,0xc8,0xc9};
    static const uint8_t mn_gdu[] ={0xc9,0xc6,0xd3,0xce,0xd1,0xc2,0xe0,0xc2,0xd3,0xe3,0xc8,0xd0,0xc9,0xcb,0xc8,0xc6,0xc3,0xf2,0xd5,0xcb};

    jclass clsRepo=env->FindClass("com/livetvpro/app/data/repository/NativeDataRepository");
    jclass clsMgr =env->FindClass("com/livetvpro/app/utils/NativeListenerManager");
    if(!clsRepo||!clsMgr){if(clsRepo)env->DeleteLocalRef(clsRepo);return JNI_ERR;}

    
    std::string n_vi=ds(mn_vi,sizeof(mn_vi)),   s_z=ds(ms_z,sizeof(ms_z));
    std::string n_gck=ds(mn_gck,sizeof(mn_gck)), s_str=ds(ms_str,sizeof(ms_str));
    std::string n_uc=ds(mn_uc,sizeof(mn_uc)),    s_sv=ds(ms_sv,sizeof(ms_sv));
    std::string n_scu=ds(mn_scu,sizeof(mn_scu));
    std::string n_gcu=ds(mn_gcu,sizeof(mn_gcu));
    std::string n_sd=ds(mn_sd,sizeof(mn_sd)),    s_sz=ds(ms_sz,sizeof(ms_sz));
    std::string n_gc=ds(mn_gc,sizeof(mn_gc));
    std::string n_gch=ds(mn_gch,sizeof(mn_gch));
    std::string n_gle=ds(mn_gle,sizeof(mn_gle));
    std::string n_idl=ds(mn_idl,sizeof(mn_idl));
    std::string n_gec=ds(mn_gec,sizeof(mn_gec));
    std::string n_gsp=ds(mn_gsp,sizeof(mn_gsp));
    std::string n_gele=ds(mn_gele,sizeof(mn_gele));
    std::string n_ssl=ds(mn_ssl,sizeof(mn_ssl)),  s_ssl=ds(ms_ssl,sizeof(ms_ssl));
    std::string n_gdlu=ds(mn_gdlu,sizeof(mn_gdlu));
    std::string n_rs=ds(mn_rs,sizeof(mn_rs)),    s_v=ds(ms_v,sizeof(ms_v));
    std::string n_icv=ds(mn_icv,sizeof(mn_icv));
    std::string n_gco=ds(mn_gco,sizeof(mn_gco));
    std::string n_gcr=ds(mn_gcr,sizeof(mn_gcr));
    std::string n_gfl=ds(mn_gfl,sizeof(mn_gfl));
    std::string n_geu=ds(mn_geu,sizeof(mn_geu));
    std::string n_gwu=ds(mn_gwu,sizeof(mn_gwu));
    std::string n_gm=ds(mn_gm,sizeof(mn_gm));
    std::string n_gmu=ds(mn_gmu,sizeof(mn_gmu));
    std::string n_gav=ds(mn_gav,sizeof(mn_gav));
    std::string n_gdu=ds(mn_gdu,sizeof(mn_gdu));

    JNINativeMethod repoMethods[]={
        {n_vi.c_str(),   s_z.c_str(),   (void*)impl_validateIntegrity},
        {n_gck.c_str(),  s_str.c_str(), (void*)impl_getConfigKey},
        {n_uc.c_str(),   s_sv.c_str(),  (void*)impl_updateConfig},
        {n_scu.c_str(),  s_sv.c_str(),  (void*)impl_storeConfigUrl},
        {n_gcu.c_str(),  s_str.c_str(), (void*)impl_getConfigUrl},
        {n_sd.c_str(),   s_sz.c_str(),  (void*)impl_storeData},
        {n_gc.c_str(),   s_str.c_str(), (void*)impl_getCategories},
        {n_gch.c_str(),  s_str.c_str(), (void*)impl_getChannels},
        {n_gle.c_str(),  s_str.c_str(), (void*)impl_getLiveEvents},
        {n_idl.c_str(),  s_z.c_str(),   (void*)impl_isDataLoaded},
        {n_gec.c_str(),  s_str.c_str(), (void*)impl_getEventCategories},
        {n_gsp.c_str(),  s_str.c_str(), (void*)impl_getSports},
        {n_gele.c_str(), s_str.c_str(), (void*)impl_getExternalLiveEvents},
    };
    if(env->RegisterNatives(clsRepo,repoMethods,13)!=0){env->DeleteLocalRef(clsRepo);env->DeleteLocalRef(clsMgr);return JNI_ERR;}

    JNINativeMethod mgrMethods[]={
        {n_ssl.c_str(),  s_ssl.c_str(), (void*)impl_shouldShowLink},
        {n_gdlu.c_str(), s_str.c_str(), (void*)impl_getDirectLinkUrl},
        {n_rs.c_str(),   s_v.c_str(),   (void*)impl_resetSessions},
        {n_icv.c_str(),  s_z.c_str(),   (void*)impl_isConfigValid},
        {n_gco.c_str(),  s_str.c_str(), (void*)impl_getContactUrl},
        {n_gcr.c_str(),  s_str.c_str(), (void*)impl_getCricLiveUrl},
        {n_gfl.c_str(),  s_str.c_str(), (void*)impl_getFootLiveUrl},
        {n_geu.c_str(),  s_str.c_str(), (void*)impl_getEmailUs},
        {n_gwu.c_str(),  s_str.c_str(), (void*)impl_getWebUrl},
        {n_gm.c_str(),   s_str.c_str(), (void*)impl_getMessage},
        {n_gmu.c_str(),  s_str.c_str(), (void*)impl_getMessageUrl},
        {n_gav.c_str(),  s_str.c_str(), (void*)impl_getAppVersion},
        {n_gdu.c_str(),  s_str.c_str(), (void*)impl_getDownloadUrl},
    };
    if(env->RegisterNatives(clsMgr,mgrMethods,13)!=0){env->DeleteLocalRef(clsRepo);env->DeleteLocalRef(clsMgr);return JNI_ERR;}

    env->DeleteLocalRef(clsRepo);
    env->DeleteLocalRef(clsMgr);
    return JNI_VERSION_1_6;
}
