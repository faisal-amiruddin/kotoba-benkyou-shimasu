#include <jni.h>
#include <voicevox_core.h>
#include <algorithm>
#include <array>
#include <cstring>
#include <list>
#include <mutex>
#include <stdexcept>
#include <string>

namespace {
std::mutex lock;
VoicevoxSynthesizer* synth = nullptr;
struct Loaded { std::string path; std::array<uint8_t,16> id; };
std::list<Loaded> loaded;
std::string bytes(JNIEnv* env, jbyteArray input) {
    std::string value(env->GetArrayLength(input), '\0');
    env->GetByteArrayRegion(input,0,value.size(),reinterpret_cast<jbyte*>(value.data()));
    if(value.find('\0')!=std::string::npos) throw std::runtime_error("NUL is not allowed in text or paths");
    return value;
}
jbyteArray array(JNIEnv* env,const uint8_t* value,size_t size) {
    if(size>256*1024*1024) throw std::runtime_error("Audio too large; shorten the sentence");
    auto result=env->NewByteArray(static_cast<jsize>(size));
    if(result) env->SetByteArrayRegion(result,0,size,reinterpret_cast<const jbyte*>(value));
    return result;
}
void check(VoicevoxResultCode result) {
    if(result!=VOICEVOX_RESULT_OK) throw std::runtime_error(voicevox_error_result_to_message(result));
}
void fail(JNIEnv* env,const std::exception& error) { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),error.what()); }
void ensureModel(const std::string& path) {
    auto existing=std::find_if(loaded.begin(),loaded.end(),[&](const Loaded& item){return item.path==path;});
    if(existing!=loaded.end()) { loaded.splice(loaded.end(),loaded,existing); return; }
    if(loaded.size()>=3) {
        check(voicevox_synthesizer_unload_voice_model(synth,reinterpret_cast<const uint8_t(*)[16]>(loaded.front().id.data())));
        loaded.pop_front();
    }
    VoicevoxVoiceModelFile* model=nullptr;
    check(voicevox_voice_model_file_open(path.c_str(),&model));
    Loaded item{path,{}};
    voicevox_voice_model_file_id(model,reinterpret_cast<uint8_t(*)[16]>(item.id.data()));
    auto result=voicevox_synthesizer_load_voice_model(synth,model,voicevox_make_default_load_voice_model_options());
    voicevox_voice_model_file_delete(model);
    check(result);
    loaded.push_back(item);
}
}

extern "C" JNIEXPORT void JNICALL Java_id_kotoba_studio_NativeSpeech_initialize(JNIEnv* env,jclass,jbyteArray dictionary,jbyteArray runtime,jint threads) {
    std::lock_guard<std::mutex> guard(lock);
    try {
        if(synth) return;
        const auto dict=bytes(env,dictionary),ortFile=bytes(env,runtime);
        const VoicevoxOnnxruntime* ort=nullptr;
        auto ortOptions=voicevox_make_default_load_onnxruntime_options();
        ortOptions.filename=ortFile.c_str();
        check(voicevox_onnxruntime_load_once(ortOptions,&ort));
        OpenJtalkRc* jtalk=nullptr;
        check(voicevox_open_jtalk_rc_new(dict.c_str(),&jtalk));
        auto options=voicevox_make_default_initialize_options();
        options.acceleration_mode=VOICEVOX_ACCELERATION_MODE_CPU;
        options.cpu_num_threads=std::clamp<int>(threads,1,4);
        auto result=voicevox_synthesizer_new(ort,jtalk,options,&synth);
        voicevox_open_jtalk_rc_delete(jtalk);
        check(result);
    } catch(const std::exception& error) { fail(env,error); }
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_id_kotoba_studio_NativeSpeech_query(JNIEnv* env,jclass,jbyteArray modelPath,jbyteArray text,jint style) {
    std::lock_guard<std::mutex> guard(lock);
    try {
        if(!synth) throw std::runtime_error("VOICEVOX has not been initialized");
        ensureModel(bytes(env,modelPath));
        auto value=bytes(env,text);
        char* query=nullptr;
        check(voicevox_synthesizer_create_audio_query(synth,value.c_str(),style,&query));
        auto result=array(env,reinterpret_cast<uint8_t*>(query),std::strlen(query));
        voicevox_json_free(query);
        return result;
    } catch(const std::exception& error) { fail(env,error); return nullptr; }
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_id_kotoba_studio_NativeSpeech_synthesize(JNIEnv* env,jclass,jbyteArray query,jint style,jboolean upspeak) {
    std::lock_guard<std::mutex> guard(lock);
    try {
        if(!synth) throw std::runtime_error("VOICEVOX has not been initialized");
        auto value=bytes(env,query);
        auto options=voicevox_make_default_synthesis_options();
        options.enable_interrogative_upspeak=upspeak;
        uintptr_t size=0; uint8_t* wav=nullptr;
        check(voicevox_synthesizer_synthesis(synth,value.c_str(),style,options,&size,&wav));
        auto result=array(env,wav,size);
        voicevox_wav_free(wav);
        return result;
    } catch(const std::exception& error) { fail(env,error); return nullptr; }
}
