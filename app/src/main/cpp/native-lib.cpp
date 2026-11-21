#include <jni.h>
#include <string>
#include <vector>
#include <set>
#include <map>
#include <mutex>
#include <cstring>

static std::mutex g_mutex;

struct ListenerConfig {
    bool directLinkEnabled = false;
    std::string directLinkUrl;
    std::set<std::string> allowedPages;
    bool loaded = false;

    std::string contactUrl;
    std::string cricLiveUrl;
    std::string footLiveUrl;
    std::string emailUs;
    std::string webUrl;
    std::string message;
    std::string messageUrl;
    std::string appVersion;
    std::string downloadUrl;
};
static ListenerConfig g_config;

static std::map<std::string, long> g_pageCounts;
static long g_totalCount = 0;

struct DataState {
    std::string json;
    bool ok = false;
};
static DataState g_data;
static std::string g_rawJson;

static std::string g_encryptionKey;
static bool g_hasEncryptionKey = false;
static bool g_dataLoaded = false;

static std::string g_configUrl;
static bool g_hasConfigUrl = false;

static std::string jsonGetString(const std::string& json, size_t from, const std::string& field) {
    size_t fp = json.find("\"" + field + "\"", from);
    if (fp == std::string::npos) return "";
    size_t cp = json.find(':', fp);
    if (cp == std::string::npos) return "";
    size_t qs = json.find('"', cp);
    if (qs == std::string::npos) return "";
    qs++;
    size_t qe = qs;
    while (qe < json.size()) {
        if (json[qe] == '\\') { qe += 2; continue; }
        if (json[qe] == '"') break;
        qe++;
    }
    if (qe >= json.size()) return "";
    return json.substr(qs, qe - qs);
}

static std::string jsonGetArrayRaw(const std::string& json, const std::string& key) {
    try {
        std::string sk = "\"" + key + "\"";
        size_t kp = json.find(sk);
        if (kp == std::string::npos) return "[]";
        size_t sp = json.find('[', kp);
        if (sp == std::string::npos) return "[]";
        int depth = 1; size_t ep = sp + 1; bool inStr = false;
        while (ep < json.length() && depth > 0) {
            char c = json[ep];
            if (inStr) {
                if (c == '\\') { ep += 2; continue; }
                if (c == '"') inStr = false;
            } else {
                if (c == '"') inStr = true;
                else if (c == '[') depth++;
                else if (c == ']') depth--;
            }
            ep++;
        }
        if (depth != 0) return "[]";
        return json.substr(sp, ep - sp);
    } catch (...) { return "[]"; }
}

static std::string jsonGetObjectRaw(const std::string& json, size_t keyPos) {
    size_t ob = json.find('{', keyPos);
    if (ob == std::string::npos) return "";
    size_t end = ob + 1; int depth = 1; bool inStr = false;
    while (end < json.size() && depth > 0) {
        char c = json[end];
        if (inStr) { if (c == '\\') { end += 2; continue; } if (c == '"') inStr = false; }
        else { if (c == '"') inStr = true; else if (c == '{') depth++; else if (c == '}') depth--; }
        end++;
    }
    if (depth != 0) return "";
    return json.substr(ob, end - ob);
}

static std::string extractEncryptedPayload(const std::string& json) {
    size_t dp = json.find("\"data\"");
    if (dp == std::string::npos) return "";
    size_t cp = json.find(':', dp);
    if (cp == std::string::npos) return "";
    size_t vs = cp + 1;
    while (vs < json.size() && (json[vs] == ' ' || json[vs] == '\t' || json[vs] == '\n')) vs++;
    if (vs >= json.size() || json[vs] != '"') return "";
    size_t es = vs + 1, ee = es;
    while (ee < json.size()) {
        if (json[ee] == '\\') { ee += 2; continue; }
        if (json[ee] == '"') break;
        ee++;
    }
    if (ee >= json.size()) return "";
    return json.substr(es, ee - es);
}

static void deriveKeyAndIv(const std::string& pw, uint8_t key[16], uint8_t iv[16]) {
    
    const uint8_t* d = (const uint8_t*)pw.c_str();
    size_t n = pw.size();
    if (n == 0) { memset(key, 0, 16); memset(iv, 0, 16); return; }
    for (int i = 0; i < 16; i++) key[i] = d[i % n] ^ (uint8_t)(i * 31 + 7);
    for (int i = 0; i < 16; i++) iv[i] = d[(i + 5) % n] ^ (uint8_t)(i * 17 + 3);
}

static std::vector<uint8_t> base64Decode(const std::string& in) {
    static const char* ch = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::vector<uint8_t> out; int val = 0, valb = -8;
    for (unsigned char c : in) {
        if (c == '=') break;
        const char* p = strchr(ch, (char)c);
        if (!p) return {};
        val = (val << 6) + (int)(p - ch); valb += 6;
        if (valb >= 0) { out.push_back((uint8_t)((val >> valb) & 0xFF)); valb -= 8; }
    }
    return out;
}

static const uint8_t kInvSBox[256] = {
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
static const uint8_t kFwdSBox[256] = {
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

static uint8_t gfMul(uint8_t a, uint8_t b) {
    uint8_t p = 0;
    for (int i = 0; i < 8; i++) {
        if (b & 1) p ^= a;
        bool hi = (a & 0x80) != 0;
        a <<= 1;
        if (hi) a ^= 0x1b;
        b >>= 1;
    }
    return p;
}

static void aesExpandKey(const uint8_t k[16], uint8_t rk[11][16]) {
    static const uint8_t rcon[10] = {0x01,0x02,0x04,0x08,0x10,0x20,0x40,0x80,0x1b,0x36};
    memcpy(rk[0], k, 16);
    for (int r = 1; r <= 10; r++) {
        uint8_t t[4];
        memcpy(t, rk[r - 1] + 12, 4);
        uint8_t t0 = t[0]; t[0] = t[1]; t[1] = t[2]; t[2] = t[3]; t[3] = t0;
        for (int i = 0; i < 4; i++) t[i] = kFwdSBox[t[i]];
        t[0] ^= rcon[r - 1];
        for (int i = 0; i < 16; i++) rk[r][i] = rk[r - 1][i] ^ (i < 4 ? t[i] : rk[r][i - 4]);
    }
}

static void aesDecryptBlock(const uint8_t in[16], uint8_t out[16], const uint8_t rk[11][16]) {
    uint8_t s[16];
    memcpy(s, in, 16);
    for (int i = 0; i < 16; i++) s[i] ^= rk[10][i];
    for (int r = 9; r >= 1; r--) {
        uint8_t t;
        t = s[13]; s[13] = s[9]; s[9] = s[5]; s[5] = s[1]; s[1] = t;
        t = s[10]; s[10] = s[2]; s[2] = t;
        t = s[14]; s[14] = s[6]; s[6] = t;
        t = s[3]; s[3] = s[7]; s[7] = s[11]; s[11] = s[15]; s[15] = t;
        for (int i = 0; i < 16; i++) s[i] = kInvSBox[s[i]];
        for (int i = 0; i < 16; i++) s[i] ^= rk[r][i];
        for (int c = 0; c < 4; c++) {
            uint8_t s0 = s[c*4], s1 = s[c*4+1], s2 = s[c*4+2], s3 = s[c*4+3];
            s[c*4+0] = gfMul(0x0e,s0) ^ gfMul(0x0b,s1) ^ gfMul(0x0d,s2) ^ gfMul(0x09,s3);
            s[c*4+1] = gfMul(0x09,s0) ^ gfMul(0x0e,s1) ^ gfMul(0x0b,s2) ^ gfMul(0x0d,s3);
            s[c*4+2] = gfMul(0x0d,s0) ^ gfMul(0x09,s1) ^ gfMul(0x0e,s2) ^ gfMul(0x0b,s3);
            s[c*4+3] = gfMul(0x0b,s0) ^ gfMul(0x0d,s1) ^ gfMul(0x09,s2) ^ gfMul(0x0e,s3);
        }
    }
    uint8_t t;
    t = s[13]; s[13] = s[9]; s[9] = s[5]; s[5] = s[1]; s[1] = t;
    t = s[10]; s[10] = s[2]; s[2] = t;
    t = s[14]; s[14] = s[6]; s[6] = t;
    t = s[3]; s[3] = s[7]; s[7] = s[11]; s[11] = s[15]; s[15] = t;
    for (int i = 0; i < 16; i++) s[i] = kInvSBox[s[i]];
    for (int i = 0; i < 16; i++) s[i] ^= rk[0][i];
    memcpy(out, s, 16);
}

static std::string aesCbcDecrypt(const std::string& base64Ciphertext, const std::string& password) {
    if (password.empty()) return "";
    try {
        std::vector<uint8_t> ct = base64Decode(base64Ciphertext);
        if (ct.empty() || ct.size() % 16 != 0) return "";
        uint8_t key[16], iv[16];
        deriveKeyAndIv(password, key, iv);
        uint8_t rk[11][16];
        aesExpandKey(key, rk);
        std::vector<uint8_t> pt(ct.size());
        uint8_t prev[16];
        memcpy(prev, iv, 16);
        for (size_t blk = 0; blk < ct.size() / 16; blk++) {
            uint8_t dec[16];
            aesDecryptBlock(ct.data() + blk * 16, dec, rk);
            for (int i = 0; i < 16; i++) pt[blk * 16 + i] = dec[i] ^ prev[i];
            memcpy(prev, ct.data() + blk * 16, 16);
        }
        if (pt.empty()) return "";
        uint8_t pad = pt.back();
        if (pad < 1 || pad > 16 || (size_t)pad > pt.size()) return "";
        for (size_t i = pt.size() - pad; i < pt.size(); i++) if (pt[i] != pad) return "";
        pt.resize(pt.size() - pad);
        return std::string(pt.begin(), pt.end());
    } catch (...) { return ""; }
}

static std::string decryptPayloadIfPresent(const std::string& base64Ciphertext) {
    if (!g_hasEncryptionKey) return "";
    return aesCbcDecrypt(base64Ciphertext, g_encryptionKey);
}

static void parseListenerConfig(const std::string& json) {
    try {
        size_t cp = json.find("\"listener_config\"");
        if (cp == std::string::npos) return;
        std::string obj = jsonGetObjectRaw(json, cp);
        if (obj.empty()) return;

        size_t ep = obj.find("\"enable_direct_link\"");
        if (ep != std::string::npos) {
            size_t col = obj.find(':', ep);
            if (col != std::string::npos) {
                size_t vs = col + 1;
                while (vs < obj.size() && (obj[vs] == ' ' || obj[vs] == '\t' || obj[vs] == '\n')) vs++;
                if (obj.compare(vs, 4, "true") == 0) g_config.directLinkEnabled = true;
                else if (obj.compare(vs, 5, "false") == 0) g_config.directLinkEnabled = false;
            }
        }

        g_config.directLinkUrl = jsonGetString(obj, 0, "direct_link_url");

        g_config.allowedPages.clear();
        size_t pp = obj.find("\"allowed_pages\"");
        if (pp != std::string::npos) {
            size_t as = obj.find('[', pp);
            if (as != std::string::npos) {
                size_t ae = as + 1; int depth = 1; bool inStr = false;
                while (ae < obj.size() && depth > 0) {
                    char ac = obj[ae];
                    if (inStr) { if (ac == '\\') { ae += 2; continue; } if (ac == '"') inStr = false; }
                    else { if (ac == '"') inStr = true; else if (ac == '[') depth++; else if (ac == ']') depth--; }
                    ae++;
                }
                if (depth == 0 && ae > as + 1) {
                    std::string arr = obj.substr(as + 1, ae - as - 2);
                    size_t pos = 0;
                    while (pos < arr.length()) {
                        size_t qs = arr.find('"', pos);
                        if (qs == std::string::npos) break;
                        size_t qe = arr.find('"', qs + 1);
                        if (qe == std::string::npos) break;
                        g_config.allowedPages.insert(arr.substr(qs + 1, qe - qs - 1));
                        pos = qe + 1;
                    }
                }
            }
        }

        g_config.loaded = true;

        auto readField = [&](const std::string& field) -> std::string {
            std::string v = jsonGetString(json, 0, field);
            if (v.empty() && !obj.empty()) v = jsonGetString(obj, 0, field);
            if (v.empty() && !g_data.json.empty()) v = jsonGetString(g_data.json, 0, field);
            return v;
        };
        g_config.contactUrl  = readField("contact_url");
        g_config.cricLiveUrl = readField("cric_live_url");
        g_config.footLiveUrl = readField("foot_live_url");
        g_config.emailUs     = readField("email_us");
        g_config.webUrl      = readField("web_url");
        g_config.message     = readField("message");
        g_config.messageUrl  = readField("message_url");
        g_config.appVersion  = readField("app_version");
        g_config.downloadUrl = readField("download_url");
    } catch (...) {}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeValidateIntegrity(JNIEnv*, jobject) {

    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeGetConfigKey(JNIEnv* env, jobject) {
    return env->NewStringUTF("data_object_url");
}

extern "C" JNIEXPORT void JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeUpdateConfig(JNIEnv* env, jobject obj, jstring key) {

    jclass cls = env->GetObjectClass(obj);
    jclass classClass = env->FindClass("java/lang/Class");
    if (!classClass) { env->ExceptionClear(); env->DeleteLocalRef(cls); return; }
    jmethodID getSimpleName = env->GetMethodID(classClass, "getSimpleName", "()Ljava/lang/String;");
    if (!getSimpleName) { env->ExceptionClear(); env->DeleteLocalRef(classClass); env->DeleteLocalRef(cls); return; }
    jstring jname = (jstring)env->CallObjectMethod(cls, getSimpleName);
    if (env->ExceptionCheck()) { env->ExceptionClear(); env->DeleteLocalRef(classClass); env->DeleteLocalRef(cls); return; }

    bool valid = false;
    if (jname) {
        const char* cname = env->GetStringUTFChars(jname, nullptr);
        valid = (cname && std::string(cname) == "NativeDataRepository");
        if (cname) env->ReleaseStringUTFChars(jname, cname);
        env->DeleteLocalRef(jname);
    }
    env->DeleteLocalRef(classClass);
    env->DeleteLocalRef(cls);

    if (!valid) {
        std::lock_guard<std::mutex> lk(g_mutex);
        g_hasEncryptionKey = false;
        g_dataLoaded = false;
        g_encryptionKey.clear();
        return;
    }
    if (!key) return;
    const char* ks = env->GetStringUTFChars(key, nullptr);
    if (ks) {
        std::string k(ks);
        env->ReleaseStringUTFChars(key, ks);
        std::lock_guard<std::mutex> lk(g_mutex);
        g_encryptionKey = k;
        g_hasEncryptionKey = true;
        g_dataLoaded = true;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeStoreData(JNIEnv* env, jobject, jstring jsonData) {
    if (!jsonData) return JNI_FALSE;
    std::lock_guard<std::mutex> lk(g_mutex);
    const char* js = env->GetStringUTFChars(jsonData, nullptr);
    if (!js) return JNI_FALSE;
    std::string raw(js);
    env->ReleaseStringUTFChars(jsonData, js);
    try {
        g_rawJson = raw;
        parseListenerConfig(raw);
        if (!g_hasEncryptionKey || !g_dataLoaded) {
            if (!g_config.directLinkEnabled) { g_data.json = raw; g_data.ok = true; return JNI_TRUE; }
            g_data.ok = false; g_data.json = ""; return JNI_FALSE;
        }
        std::string json = raw;
        std::string encrypted = extractEncryptedPayload(raw);
        if (!encrypted.empty()) {
            std::string decrypted = decryptPayloadIfPresent(encrypted);
            if (!decrypted.empty()) json = decrypted;
        }
        g_data.json = json;
        g_data.ok = true;
        parseListenerConfig(g_data.json);
        if (!g_config.loaded) parseListenerConfig(raw);
        return JNI_TRUE;
    } catch (...) { return JNI_FALSE; }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeGetCategories(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lk(g_mutex);
    if (!g_data.ok || !g_dataLoaded) return env->NewStringUTF("[]");
    std::string r = jsonGetArrayRaw(g_data.json, "categories");
    if (r == "[]") r = jsonGetArrayRaw(g_rawJson, "categories");
    return env->NewStringUTF(r.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeGetChannels(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lk(g_mutex);
    if (!g_data.ok || !g_dataLoaded) return env->NewStringUTF("[]");
    std::string r = jsonGetArrayRaw(g_data.json, "channels");
    if (r == "[]") r = jsonGetArrayRaw(g_rawJson, "channels");
    return env->NewStringUTF(r.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeGetLiveEvents(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lk(g_mutex);
    if (!g_data.ok || !g_dataLoaded) return env->NewStringUTF("[]");
    std::string r = jsonGetArrayRaw(g_data.json, "live_events");
    if (r == "[]") r = jsonGetArrayRaw(g_data.json, "liveEvents");
    if (r == "[]") r = jsonGetArrayRaw(g_rawJson, "live_events");
    if (r == "[]") r = jsonGetArrayRaw(g_rawJson, "liveEvents");
    return env->NewStringUTF(r.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeGetExternalLiveEvents(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lk(g_mutex);
    if (!g_data.ok) return env->NewStringUTF("[]");
    std::string r = jsonGetArrayRaw(g_data.json, "external_live_events");
    if (r == "[]") r = jsonGetArrayRaw(g_rawJson, "external_live_events");
    return env->NewStringUTF(r.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeGetEventCategories(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lk(g_mutex);
    if (!g_data.ok || !g_dataLoaded) return env->NewStringUTF("[]");
    std::string r = jsonGetArrayRaw(g_data.json, "event_categories");
    if (r == "[]") r = jsonGetArrayRaw(g_data.json, "eventCategories");
    if (r == "[]") r = jsonGetArrayRaw(g_rawJson, "event_categories");
    if (r == "[]") r = jsonGetArrayRaw(g_rawJson, "eventCategories");
    return env->NewStringUTF(r.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeGetSports(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lk(g_mutex);
    if (!g_data.ok || !g_dataLoaded) return env->NewStringUTF("[]");
    std::string r = jsonGetArrayRaw(g_data.json, "sports_slug");
    if (r == "[]") r = jsonGetArrayRaw(g_data.json, "sports");
    if (r == "[]") r = jsonGetArrayRaw(g_rawJson, "sports_slug");
    if (r == "[]") r = jsonGetArrayRaw(g_rawJson, "sports");
    return env->NewStringUTF(r.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeIsDataLoaded(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lk(g_mutex);
    if (!g_dataLoaded) return JNI_FALSE;
    return g_data.ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeStoreConfigUrl(JNIEnv* env, jobject, jstring url) {
    if (!url) return;
    const char* us = env->GetStringUTFChars(url, nullptr);
    if (us) {
        std::lock_guard<std::mutex> lk(g_mutex);
        g_configUrl = std::string(us);
        g_hasConfigUrl = true;
        env->ReleaseStringUTFChars(url, us);
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_data_repository_NativeDataRepository_nativeGetConfigUrl(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lk(g_mutex);
    return env->NewStringUTF(g_hasConfigUrl ? g_configUrl.c_str() : "");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeShouldShowLink(JNIEnv* env, jobject, jstring pageType, jstring, jlong maxPerPage, jlong maxTotal) {
    std::lock_guard<std::mutex> lk(g_mutex);
    if (!g_dataLoaded || !g_hasEncryptionKey) return JNI_FALSE;
    if (!g_config.loaded) return JNI_FALSE;
    if (!pageType) return JNI_FALSE;

    const char* pts = env->GetStringUTFChars(pageType, nullptr);
    if (!pts) return JNI_FALSE;
    std::string pt(pts);
    env->ReleaseStringUTFChars(pageType, pts);

    if (g_config.allowedPages.find(pt) == g_config.allowedPages.end()) return JNI_FALSE;
    if (!g_config.directLinkEnabled) return JNI_FALSE;

    if (maxTotal > 0 && (jlong)g_totalCount >= maxTotal) return JNI_FALSE;
    long pageCount = g_pageCounts.count(pt) ? g_pageCounts[pt] : 0;
    if (maxPerPage > 0 && (jlong)pageCount >= maxPerPage) return JNI_FALSE;

    g_pageCounts[pt] = pageCount + 1;
    g_totalCount++;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetDirectLinkUrl(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lk(g_mutex);
    if (!g_dataLoaded) return env->NewStringUTF("");
    return env->NewStringUTF(g_config.directLinkUrl.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeResetSessions(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lk(g_mutex);
    g_pageCounts.clear();
    g_totalCount = 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeIsConfigValid(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lk(g_mutex);
    if (!g_dataLoaded || !g_hasEncryptionKey) return JNI_FALSE;
    return g_config.loaded ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetContactUrl(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lk(g_mutex);
    return env->NewStringUTF(g_config.contactUrl.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetCricLiveUrl(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lk(g_mutex);
    return env->NewStringUTF(g_config.cricLiveUrl.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetFootLiveUrl(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lk(g_mutex);
    return env->NewStringUTF(g_config.footLiveUrl.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetEmailUs(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lk(g_mutex);
    return env->NewStringUTF(g_config.emailUs.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetWebUrl(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lk(g_mutex);
    return env->NewStringUTF(g_config.webUrl.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetMessage(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lk(g_mutex);
    return env->NewStringUTF(g_config.message.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetMessageUrl(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lk(g_mutex);
    return env->NewStringUTF(g_config.messageUrl.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetAppVersion(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lk(g_mutex);
    return env->NewStringUTF(g_config.appVersion.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetDownloadUrl(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lk(g_mutex);
    return env->NewStringUTF(g_config.downloadUrl.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetRcKeyAdRedirectInApp(JNIEnv* env, jobject) {
    return env->NewStringUTF("ad_redirect_in_app");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetRcKeyAdDurationSeconds(JNIEnv* env, jobject) {
    return env->NewStringUTF("ad_duration_seconds");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetRcKeyHideAdContent(JNIEnv* env, jobject) {
    return env->NewStringUTF("hide_ad_content");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetRcKeyRedirectModePhone(JNIEnv* env, jobject) {
    return env->NewStringUTF("redirect_mode_phone");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetRcKeyRedirectModeTablet(JNIEnv* env, jobject) {
    return env->NewStringUTF("redirect_mode_tablet");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetRcKeyRedirectModeTv(JNIEnv* env, jobject) {
    return env->NewStringUTF("redirect_mode_tv");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetRcKeyRedirectModeOther(JNIEnv* env, jobject) {
    return env->NewStringUTF("redirect_mode_other");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetvpro_app_utils_NativeListenerManager_nativeGetRcKeyExternalBrowser(JNIEnv* env, jobject) {
    return env->NewStringUTF("external_browser");
}
