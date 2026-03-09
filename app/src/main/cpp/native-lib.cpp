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
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/stat.h>

static std::mutex g_mutex;

static bool isFridaPortOpen() {
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) return false;
    struct timeval tv; tv.tv_sec = 0; tv.tv_usec = 200000;
    setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
    struct sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(27042);
    addr.sin_addr.s_addr = inet_addr("127.0.0.1");
    int r = connect(sock, (struct sockaddr*)&addr, sizeof(addr));
    close(sock);
    return r == 0;
}

static bool isFridaInMaps() {
    FILE* f = fopen("/proc/self/maps", "r");
    if (!f) return false;
    char line[512]; bool found = false;
    const char* m[] = {"frida","gum-js-loop","gmain","linjector","frida-agent","frida-gadget",
                       "re.frida","frida-helper","frida-node",nullptr};
    while (fgets(line, sizeof(line), f)) {
        for (int i = 0; m[i]; i++) if (strstr(line, m[i])) { found = true; break; }
        if (found) break;
    }
    fclose(f); return found;
}

static bool isFridaPipePresent() {
    const char* pipes[] = {"/data/local/tmp/frida-server","/data/local/tmp/re.frida.server","/proc/net/unix"};
    for (const char* p : pipes) {
        if (access(p, F_OK) == 0) {
            if (strstr(p, "/proc/net/unix")) {
                FILE* f = fopen(p, "r"); if (!f) continue;
                char line[256]; bool found = false;
                while (fgets(line, sizeof(line), f))
                    if (strstr(line, "frida") || strstr(line, "linjector")) { found = true; break; }
                fclose(f); if (found) return true;
            } else return true;
        }
    }
    return false;
}

static bool isTracerPidNonZero() {
    FILE* f = fopen("/proc/self/status", "r"); if (!f) return false;
    char line[128]; bool t = false;
    while (fgets(line, sizeof(line), f)) {
        if (strncmp(line, "TracerPid:", 10) == 0) {
            char* end = nullptr;
            long val = strtol(line + 10, &end, 10);
            t = (end != line + 10 && val != 0);
            break;
        }
    }
    fclose(f); return t;
}

static bool isTampered() {
    return isFridaPortOpen() || isFridaInMaps() || isFridaPipePresent() || isTracerPidNonZero();
}

static const uint8_t kX = 0xA7;

static inline std::string xd(const uint8_t* b, size_t n) {
    std::string s; s.reserve(n);
    for (size_t i = 0; i < n; i++) s += (char)(b[i] ^ kX);
    return s;
}

static const size_t kP2Size = 256;
static const size_t kP4Size = 65;

struct z9 {
    bool p1;
    uint8_t p2[kP2Size];
    uint8_t p2s[kP2Size];
    size_t p2_len;
    uint8_t p4[kP4Size];
    bool p5;
    bool p1s;
    bool locked;
    std::string p6,p7,p8,p9,p10,p11,p12,p13,p14;
} static g1 = {false,{},{},0,{},false,false,false,"","","","","","","","",""};

static std::map<std::string,long> g2;
static long g2_total=0;
static std::set<std::string> g_allowed;

static inline bool isPageAllowed(const std::string& pt);

struct q7 { std::string d; bool ok; } static g3 = {"", false};

static std::string g4 = "";
static bool g5 = false;
static uint8_t g6[32] = {0};
static size_t g6_len = 0;
static bool g7 = false;
static bool g8 = false;
static uint32_t g9 = 0;

static inline void ws(const std::string& s, uint8_t* out, size_t mx, size_t* lenout=nullptr) {
    if(mx == 0) return;
    size_t n = s.size() < (mx - 1) ? s.size() : (mx - 1);
    for (size_t i = 0; i < n; i++) out[i] = (uint8_t)s[i] ^ kX;
    out[n] = 0;
    if(lenout) *lenout = n;
}

static inline std::string rs(const uint8_t* in, size_t len) {
    std::string o; o.reserve(len);
    for (size_t i = 0; i < len; i++) o += (char)(in[i] ^ kX);
    return o;
}

static const uint8_t kX2 = 0x5C;

static inline void ws2(const std::string& s, uint8_t* out, size_t mx) {
    if(mx == 0) return;
    size_t n = s.size() < (mx - 1) ? s.size() : (mx - 1);
    for (size_t i = 0; i < n; i++) out[i] = (uint8_t)s[i] ^ kX2;
    out[n] = 0;
}

static inline std::string rs2(const uint8_t* in, size_t len) {
    std::string o; o.reserve(len);
    for (size_t i = 0; i < len; i++) o += (char)(in[i] ^ kX2);
    return o;
}

static uint32_t im32(uint32_t a, uint32_t b) { return (uint32_t)((uint64_t)a*b); }

static uint32_t fnv32(const std::string& s) {
    uint32_t h = 0x811c9dc5u;
    for (unsigned char c : s) h = im32(h ^ c, 0x1000193u);
    return h;
}

static const char* KC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+!@#$%&=";
static const size_t KL = 70;

static void dkiv(const std::string& pw, uint8_t key[16], uint8_t iv[16]) {
    const uint8_t* d = (const uint8_t*)pw.c_str(); size_t n = pw.size();
    uint32_t u = 0x811c9dc5u;
    for (size_t i=0;i<n;i++) u=im32(u^d[i],0x1000193u);
    for (int i=0;i<16;i++) { uint8_t b=d[i%n]; u=im32(u,0x1fu)+(uint32_t)(i^b); key[i]=(uint8_t)KC[u%KL]; }
    u = 0x811c832au;
    for (size_t i=0;i<n;i++) u=im32(u^d[i],0x1000193u);
    uint32_t idx=0,acc=0;
    while (idx!=0x30u) { uint8_t b=d[idx%n]; u=im32(u,0x1du)+(acc^(uint32_t)b); iv[idx/3]=(uint8_t)KC[u%KL]; idx+=3; acc=(acc+7u)&0xFFFFFFFFu; }
}

static std::vector<uint8_t> b64d(const std::string& in) {
    static const char* ch="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::vector<uint8_t> o; int val=0,valb=-8; bool valid=true;
    for (unsigned char c:in) {
        if(c=='=') break;
        const char* p=strchr(ch,(char)c);
        if(!p) { valid=false; break; }
        val=(val<<6)+(int)(p-ch); valb+=6;
        if(valb>=0){o.push_back((uint8_t)((val>>valb)&0xFF));valb-=8;}
    }
    if(!valid) return {};
    return o;
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
    uint8_t s[16]; memcpy(s,in,16);
    for(int i=0;i<16;i++) s[i]^=rk[10][i];
    for(int r=9;r>=1;r--){
        uint8_t t;
        t=s[13];s[13]=s[9];s[9]=s[5];s[5]=s[1];s[1]=t;
        t=s[10];s[10]=s[2];s[2]=t;t=s[14];s[14]=s[6];s[6]=t;
        t=s[3];s[3]=s[7];s[7]=s[11];s[11]=s[15];s[15]=t;
        for(int i=0;i<16;i++) s[i]=isb[s[i]];
        for(int i=0;i<16;i++) s[i]^=rk[r][i];
        for(int c=0;c<4;c++){uint8_t s0=s[c*4],s1=s[c*4+1],s2=s[c*4+2],s3=s[c*4+3];s[c*4+0]=gm(0x0e,s0)^gm(0x0b,s1)^gm(0x0d,s2)^gm(0x09,s3);s[c*4+1]=gm(0x09,s0)^gm(0x0e,s1)^gm(0x0b,s2)^gm(0x0d,s3);s[c*4+2]=gm(0x0d,s0)^gm(0x09,s1)^gm(0x0e,s2)^gm(0x0b,s3);s[c*4+3]=gm(0x0b,s0)^gm(0x0d,s1)^gm(0x09,s2)^gm(0x0e,s3);}
    }
    uint8_t t;
    t=s[13];s[13]=s[9];s[9]=s[5];s[5]=s[1];s[1]=t;
    t=s[10];s[10]=s[2];s[2]=t;t=s[14];s[14]=s[6];s[6]=t;
    t=s[3];s[3]=s[7];s[7]=s[11];s[11]=s[15];s[15]=t;
    for(int i=0;i<16;i++) s[i]=isb[s[i]];
    for(int i=0;i<16;i++) s[i]^=rk[0][i];
    memcpy(out,s,16);
}

static std::string adec(const std::string& b64ct,const std::string& pw){
    if(pw.empty()) return "";
    try{
        std::vector<uint8_t> ct=b64d(b64ct);
        if(ct.empty()||ct.size()%16!=0) return "";
        uint8_t key[16],iv[16]; dkiv(pw,key,iv);
        uint8_t rk[11][16]; ake(key,rk);
        std::vector<uint8_t> pt(ct.size());
        uint8_t prev[16]; memcpy(prev,iv,16);
        for(size_t blk=0;blk<ct.size()/16;blk++){uint8_t dec[16];adb(ct.data()+blk*16,dec,rk);for(int i=0;i<16;i++)pt[blk*16+i]=dec[i]^prev[i];memcpy(prev,ct.data()+blk*16,16);}
        if(pt.empty()) return "";
        uint8_t pad=pt.back();
        if(pad<1||pad>16||(size_t)pad>pt.size()) return "";
        uint8_t expected_pad=pad;
        for(size_t i=pt.size()-pad;i<pt.size();i++) if(pt[i]!=expected_pad) return "";
        pt.resize(pt.size()-pad);
        return std::string(pt.begin(),pt.end());
    }catch(...){return "";}
}

static std::string xenc(const std::string& b64ct){
    if(!g7) return "";
    std::string pw; pw.reserve(g6_len);
    for(size_t i=0; i<g6_len; i++) pw += (char)(g6[i]^0x3F);
    return adec(b64ct,pw);
}

static std::string epd(const std::string& json){
    size_t dp=json.find("\"data\""); if(dp==std::string::npos) return "";
    size_t cp=json.find(':',dp); if(cp==std::string::npos) return "";
    size_t vs=cp+1; while(vs<json.size()&&(json[vs]==' '||json[vs]=='\t'||json[vs]=='\n')) vs++;
    if(vs>=json.size()||json[vs]!='"') return "";
    size_t es=vs+1;
    size_t ee=es;
    while(ee<json.size()) {
        if(json[ee]=='\\') { ee+=2; continue; }
        if(json[ee]=='"') break;
        ee++;
    }
    if(ee>=json.size()) return "";
    return json.substr(es,ee-es);
}

static std::string exa(const std::string& json,const std::string& key){
    try{
        std::string sk="\""+key+"\""; size_t kp=json.find(sk); if(kp==std::string::npos) return "[]";
        size_t sp=json.find('[',kp); if(sp==std::string::npos) return "[]";
        int bc=1; size_t ep=sp+1;
        bool in_str=false;
        while(ep<json.length()&&bc>0){
            char c=json[ep];
            if(in_str) {
                if(c=='\\') { ep+=2; continue; }
                if(c=='"') in_str=false;
            } else {
                if(c=='"') in_str=true;
                else if(c=='[') bc++;
                else if(c==']') bc--;
            }
            ep++;
        }
        if(bc!=0) return "[]";
        return json.substr(sp,ep-sp);
    }catch(...){return "[]";}
}

static std::string exs(const std::string& json,size_t from,const std::string& field){
    size_t fp=json.find("\""+field+"\"",from); if(fp==std::string::npos) return "";
    size_t cp=json.find(':',fp); if(cp==std::string::npos) return "";
    size_t qs=json.find('"',cp); if(qs==std::string::npos) return "";
    qs++;
    size_t qe=qs;
    while(qe<json.size()) {
        if(json[qe]=='\\') { qe+=2; continue; }
        if(json[qe]=='"') break;
        qe++;
    }
    if(qe>=json.size()) return "";
    return json.substr(qs,qe-qs);
}

static void elc(const std::string& json){
    try{
        static const uint8_t f1[]={0xcb,0xce,0xd4,0xd3,0xc2,0xc9,0xc2,0xd5,0xf8,0xc4,0xc8,0xc9,0xc1,0xce,0xc0};
        static const uint8_t f2[]={0xc2,0xc9,0xc6,0xc5,0xcb,0xc2,0xf8,0xc3,0xce,0xd5,0xc2,0xc4,0xd3,0xf8,0xcb,0xce,0xc9,0xcc};
        static const uint8_t f3[]={0xc3,0xce,0xd5,0xc2,0xc4,0xd3,0xf8,0xcb,0xce,0xc9,0xcc,0xf8,0xd2,0xd5,0xcb};
        static const uint8_t f4[]={0xc6,0xcb,0xcb,0xc8,0xd0,0xc2,0xc3,0xf8,0xd7,0xc6,0xc0,0xc2,0xd4};
        static const uint8_t _s_contact[]    = {0xc4,0xc8,0xc9,0xd3,0xc6,0xc4,0xd3,0xf8,0xd2,0xd5,0xcb};
        static const uint8_t _s_cric[]       = {0xc4,0xd5,0xce,0xc4,0xf8,0xcb,0xce,0xd1,0xc2,0xf8,0xd2,0xd5,0xcb};
        static const uint8_t _s_foot[]       = {0xc1,0xc8,0xc8,0xd3,0xf8,0xcb,0xce,0xd1,0xc2,0xf8,0xd2,0xd5,0xcb};
        static const uint8_t _s_email[]      = {0xc2,0xca,0xc6,0xce,0xcb,0xf8,0xd2,0xd4};
        static const uint8_t _s_web[]        = {0xd0,0xc2,0xc5,0xf8,0xd2,0xd5,0xcb};
        static const uint8_t _s_message[]    = {0xca,0xc2,0xd4,0xd4,0xc6,0xc0,0xc2};
        static const uint8_t _s_messageurl[] = {0xca,0xc2,0xd4,0xd4,0xc6,0xc0,0xc2,0xf8,0xd2,0xd5,0xcb};
        static const uint8_t _s_appver[]     = {0xc6,0xd7,0xd7,0xf8,0xd1,0xc2,0xd5,0xd4,0xce,0xc8,0xc9};
        static const uint8_t _s_dlurl[]      = {0xc3,0xc8,0xd0,0xc9,0xcb,0xc8,0xc6,0xc3,0xf8,0xd2,0xd5,0xcb};

        std::string lc=xd(f1,sizeof(f1));
        size_t cp=json.find("\""+lc+"\"");
        if(cp==std::string::npos) return;
        size_t ob=json.find('{',cp);
        if(ob==std::string::npos) return;
        size_t obj_end=ob+1; int depth=1; bool ins=false;
        while(obj_end<json.size()&&depth>0){
            char c=json[obj_end];
            if(ins){if(c=='\\'){ obj_end+=2;continue;}if(c=='"')ins=false;}
            else{if(c=='"')ins=true;else if(c=='{')depth++;else if(c=='}')depth--;}
            obj_end++;
        }
        if(depth!=0) return;
        std::string obj=json.substr(ob,obj_end-ob);

        std::string edl=xd(f2,sizeof(f2));
        size_t ep2=obj.find("\""+edl+"\"");
        if(ep2!=std::string::npos){
            size_t col=obj.find(':',ep2+edl.size()+2);
            if(col!=std::string::npos){
                size_t vs=col+1;
                while(vs<obj.size()&&(obj[vs]==' '||obj[vs]=='\t'||obj[vs]=='\n'))vs++;
                if(vs+4<=obj.size()&&obj.substr(vs,4)=="true") g1.p1=true;
                else if(vs+5<=obj.size()&&obj.substr(vs,5)=="false") g1.p1=false;
            }
        }

        std::string dlu=xd(f3,sizeof(f3));
        std::string urlval=exs(obj,0,dlu);
        ws(urlval,g1.p2,kP2Size,&g1.p2_len);
        ws2(urlval,g1.p2s,kP2Size);

        g1.p4[0]=0;
        std::string apf=xd(f4,sizeof(f4));
        size_t pp=obj.find("\""+apf+"\"");
        if(pp!=std::string::npos){
            size_t as=obj.find('[',pp);
            if(as!=std::string::npos){
                size_t ae=as+1; int adepth=1; bool ains=false;
                while(ae<obj.size()&&adepth>0){char ac=obj[ae];if(ains){if(ac=='\\'){ae+=2;continue;}if(ac=='"')ains=false;}else{if(ac=='"')ains=true;else if(ac=='[')adepth++;else if(ac==']')adepth--;}ae++;}
                if(adepth==0&&ae>as+1){
                    std::string pa=obj.substr(as+1,ae-as-2);
                    size_t pos=0,pi=0;
                    const size_t kMaxP4=kP4Size-1;
                    while(pos<pa.length()&&pi<kMaxP4){
                        size_t qs=pa.find('"',pos); if(qs==std::string::npos) break;
                        size_t qe=pa.find('"',qs+1); if(qe==std::string::npos) break;
                        std::string pn=pa.substr(qs+1,qe-qs-1);
                        for(size_t ci=0;ci<pn.size()&&pi<kMaxP4-1;ci++) g1.p4[pi++]=(uint8_t)pn[ci]^kX;
                        if(pi<kMaxP4) g1.p4[pi++]=0xFF;
                        pos=qe+1;
                    }
                    if(pi<kP4Size) g1.p4[pi]=0;
                }
            }
        }

        g1.p5=true;
        g1.p1s=g1.p1;
        g1.locked=false;
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

extern "C" JNIEXPORT jboolean JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeValidateIntegrity(JNIEnv*,jobject){
    return isTampered()?JNI_FALSE:JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeGetConfigKey(JNIEnv* env,jobject){
    static const uint8_t k[]={0xc3,0xc6,0xd3,0xc6,0xf8,0xc8,0xc5,0xcd,0xc2,0xc4,0xd3,0xf8,0xd2,0xd5,0xcb};
    return env->NewStringUTF(xd(k,sizeof(k)).c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeUpdateConfig(JNIEnv* env,jobject obj,jstring key){
    static const uint8_t cn[]={0xe9,0xc6,0xd3,0xce,0xd1,0xc2,0xe3,0xc6,0xd3,0xc6,0xf5,0xc2,0xd7,0xc8,0xd4,0xce,0xd3,0xc8,0xd5,0xde};
    std::string expected=xd(cn,sizeof(cn));
    jclass cls=env->GetObjectClass(obj);
    jclass clsCls=env->FindClass("java/lang/Class");
    jmethodID getSimpleName=env->GetMethodID(clsCls,"getSimpleName","()Ljava/lang/String;");
    jstring jname=(jstring)env->CallObjectMethod(cls,getSimpleName);
    const char* cname=env->GetStringUTFChars(jname,nullptr);
    bool valid=(cname&&std::string(cname)==expected);
    if(cname) env->ReleaseStringUTFChars(jname,cname);
    if(!valid){
        std::lock_guard<std::mutex> lk(g_mutex);
        g7=false;g8=false;g6_len=0;memset(g6,0,sizeof(g6));return;
    }
    if(!key) return;
    const char* ks=env->GetStringUTFChars(key,nullptr);
    if(ks){
        size_t n=strlen(ks);
        if(n>31) n=31;
        uint8_t tmp[32]={0};
        for(size_t i=0;i<n;i++) tmp[i]=(uint8_t)ks[i]^0x3F;
        uint32_t hash=(uint32_t)(n*0x9e3779b9^0x6c62272e);
        env->ReleaseStringUTFChars(key,ks);
        std::lock_guard<std::mutex> lk(g_mutex);
        memset(g6,0,sizeof(g6));
        memcpy(g6,tmp,n);
        g6_len=n;
        g7=true;g8=true;
        g9=hash;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeStoreData(JNIEnv* env,jobject,jstring jsonData){
    std::lock_guard<std::mutex> lk(g_mutex);
    if(!g7||!g8){g3.ok=false;g3.d="";g1.p1=false;g1.p5=false;return JNI_FALSE;}
    const char* js=env->GetStringUTFChars(jsonData,nullptr);
    if(!js) return JNI_FALSE;
    std::string raw(js); env->ReleaseStringUTFChars(jsonData,js);
    try{
        std::string json=raw;
        std::string ep=epd(raw);
        if(!ep.empty()){
            std::string dec=xenc(ep);
            if(!dec.empty()) json=dec;
        }
        g3.d=json; g3.ok=true; elc(g3.d);
        if(!g1.p5) elc(raw);
        return JNI_TRUE;
    }catch(...){return JNI_FALSE;}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeGetCategories(JNIEnv* env,jobject){
    std::lock_guard<std::mutex> lk(g_mutex);
    if(!g3.ok||!g8) return env->NewStringUTF("[]");
    static const uint8_t pt[]={0xcf,0xc8,0xca,0xc2};
    if(!isPageAllowed(xd(pt,sizeof(pt)))) return env->NewStringUTF("[]");
    return env->NewStringUTF(exa(g3.d,"categories").c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeGetChannels(JNIEnv* env,jobject){
    std::lock_guard<std::mutex> lk(g_mutex);
    if(!g3.ok||!g8) return env->NewStringUTF("[]");
    static const uint8_t pt[]={0xc4,0xcf,0xc6,0xc9,0xc9,0xc2,0xcb,0xd4};
    if(!isPageAllowed(xd(pt,sizeof(pt)))) return env->NewStringUTF("[]");
    return env->NewStringUTF(exa(g3.d,"channels").c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeGetLiveEvents(JNIEnv* env,jobject){
    std::lock_guard<std::mutex> lk(g_mutex);
    if(!g3.ok||!g8) return env->NewStringUTF("[]");
    static const uint8_t pt[]={0xcb,0xce,0xd1,0xc2,0xf8,0xc2,0xd1,0xc2,0xc9,0xd3,0xd4};
    if(!isPageAllowed(xd(pt,sizeof(pt)))) return env->NewStringUTF("[]");
    std::string r=exa(g3.d,"live_events");
    if(r=="[]") r=exa(g3.d,"liveEvents");
    return env->NewStringUTF(r.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeGetExternalLiveEvents(JNIEnv* env,jobject){
    std::lock_guard<std::mutex> lk(g_mutex);
    if(!g3.ok) return env->NewStringUTF("[]");
    return env->NewStringUTF(exa(g3.d,"external_live_events").c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeGetEventCategories(JNIEnv* env,jobject){
    std::lock_guard<std::mutex> lk(g_mutex);
    if(!g3.ok||!g8) return env->NewStringUTF("[]");
    static const uint8_t pt[]={0xcb,0xce,0xd1,0xc2,0xf8,0xc2,0xd1,0xc2,0xc9,0xd3,0xd4};
    if(!isPageAllowed(xd(pt,sizeof(pt)))) return env->NewStringUTF("[]");
    std::string r=exa(g3.d,"event_categories");
    if(r=="[]") r=exa(g3.d,"eventCategories");
    return env->NewStringUTF(r.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeGetSports(JNIEnv* env,jobject){
    std::lock_guard<std::mutex> lk(g_mutex);
    if(!g3.ok||!g8) return env->NewStringUTF("[]");
    static const uint8_t pt[]={0xd4,0xd7,0xc8,0xd5,0xd3,0xd4};
    if(!isPageAllowed(xd(pt,sizeof(pt)))) return env->NewStringUTF("[]");
    std::string r=exa(g3.d,"sports_slug");
    if(r=="[]") r=exa(g3.d,"sports");
    return env->NewStringUTF(r.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeIsDataLoaded(JNIEnv* env,jobject){
    std::lock_guard<std::mutex> lk(g_mutex);
    if(!g8) return JNI_FALSE;
    return g3.ok?JNI_TRUE:JNI_FALSE;
}

static inline bool isPageAllowed(const std::string& pt) {
    if(!g1.p5) return false;
    if(!g1.p1) return true;
    return g_allowed.find(pt) != g_allowed.end();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeShouldShowLink(JNIEnv* env,jobject,jstring pageType,jstring,jlong maxPerPage,jlong maxTotal){
    if(isTampered()){
        std::lock_guard<std::mutex> lk(g_mutex);
        g1.locked=true;g1.p1=false;g1.p1s=false;g1.p5=false;memset(g1.p2,0,sizeof(g1.p2));memset(g1.p2s,0,sizeof(g1.p2s));g1.p2_len=0;
        return JNI_FALSE;
    }
    std::lock_guard<std::mutex> lk(g_mutex);
    if(g1.locked) return JNI_FALSE;
    if(!g8||!g7) return JNI_FALSE;
    if(g1.p1!=g1.p1s){g1.locked=true;g1.p1=false;g1.p1s=false;g1.p5=false;return JNI_FALSE;}
    std::string u1=rs(g1.p2,g1.p2_len),u2=rs2(g1.p2s,g1.p2_len);
    if(u1!=u2){g1.locked=true;g1.p1=false;g1.p1s=false;g1.p5=false;memset(g1.p2,0,sizeof(g1.p2));memset(g1.p2s,0,sizeof(g1.p2s));g1.p2_len=0;return JNI_FALSE;}
    if(!g1.p5) return JNI_FALSE;
    if(!pageType) return JNI_FALSE;
    const char* pts=env->GetStringUTFChars(pageType,nullptr); if(!pts) return JNI_FALSE;
    std::string pt(pts); env->ReleaseStringUTFChars(pageType,pts);
    size_t i=0; bool found=false; std::string cur;
    while(i<kP4Size&&g1.p4[i]!=0){
        if(g1.p4[i]==0xFF){if(cur==pt){found=true;break;}cur.clear();}
        else cur+=(char)(g1.p4[i]^kX);
        i++;
    }
    if(!found&&!cur.empty()&&cur==pt) found=true;
    if(!found) return JNI_FALSE;
    g_allowed.insert(pt);
    if(!g1.p1) return JNI_FALSE;
    if(maxTotal>0&&(jlong)g2_total>=maxTotal) return JNI_FALSE;
    long pageCount=g2.count(pt)?g2[pt]:0;
    if(maxPerPage>0&&(jlong)pageCount>=maxPerPage) return JNI_FALSE;
    g2[pt]=pageCount+1; g2_total++; return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetDirectLinkUrl(JNIEnv* env,jobject){
    std::lock_guard<std::mutex> lk(g_mutex);
    if(g1.locked||!g8) return env->NewStringUTF("");
    std::string u1=rs(g1.p2,g1.p2_len),u2=rs2(g1.p2s,g1.p2_len);
    if(u1!=u2){g1.locked=true;g1.p1=false;g1.p1s=false;g1.p5=false;memset(g1.p2,0,sizeof(g1.p2));memset(g1.p2s,0,sizeof(g1.p2s));g1.p2_len=0;return env->NewStringUTF("");}
    return env->NewStringUTF(u1.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeResetSessions(JNIEnv* env,jobject){
    std::lock_guard<std::mutex> lk(g_mutex);
    g2.clear(); g2_total=0; g_allowed.clear();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeIsConfigValid(JNIEnv* env,jobject){
    std::lock_guard<std::mutex> lk(g_mutex);
    if(isTampered()){g1.locked=true;g1.p1=false;g1.p5=false;return JNI_FALSE;}
    if(!g8||!g7){g1.locked=true;return JNI_FALSE;}
    return g1.p5?JNI_TRUE:JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeStoreConfigUrl(JNIEnv* env,jobject,jstring url){
    const char* us=env->GetStringUTFChars(url,nullptr);
    if(us){
        std::lock_guard<std::mutex> lk(g_mutex);
        g4=std::string(us);g5=true;
        env->ReleaseStringUTFChars(url,us);
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeGetConfigUrl(JNIEnv* env,jobject){
    std::lock_guard<std::mutex> lk(g_mutex);
    return env->NewStringUTF(g5?g4.c_str():"");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetContactUrl(JNIEnv* env,jobject){std::lock_guard<std::mutex> lk(g_mutex);return env->NewStringUTF(g1.p6.c_str());}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetCricLiveUrl(JNIEnv* env,jobject){std::lock_guard<std::mutex> lk(g_mutex);return env->NewStringUTF(g1.p7.c_str());}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetFootLiveUrl(JNIEnv* env,jobject){std::lock_guard<std::mutex> lk(g_mutex);return env->NewStringUTF(g1.p8.c_str());}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetEmailUs(JNIEnv* env,jobject){std::lock_guard<std::mutex> lk(g_mutex);return env->NewStringUTF(g1.p9.c_str());}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetWebUrl(JNIEnv* env,jobject){std::lock_guard<std::mutex> lk(g_mutex);return env->NewStringUTF(g1.p10.c_str());}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetMessage(JNIEnv* env,jobject){std::lock_guard<std::mutex> lk(g_mutex);return env->NewStringUTF(g1.p11.c_str());}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetMessageUrl(JNIEnv* env,jobject){std::lock_guard<std::mutex> lk(g_mutex);return env->NewStringUTF(g1.p12.c_str());}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetAppVersion(JNIEnv* env,jobject){std::lock_guard<std::mutex> lk(g_mutex);return env->NewStringUTF(g1.p13.c_str());}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetDownloadUrl(JNIEnv* env,jobject){std::lock_guard<std::mutex> lk(g_mutex);return env->NewStringUTF(g1.p14.c_str());}
