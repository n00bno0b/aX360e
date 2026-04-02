// SPDX-License-Identifier: WTFPL

#include "emulator.h"
#include <android/log.h>
#include <atomic>
#include <fstream>
#include <jni.h>
#include <thread>

#define LOG_TAG "Emulator_Config"
#define LOGE(...) {      \
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,"%s : %d",__FILE__,__LINE__);\
	__android_log_print(ANDROID_LOG_ERROR, LOG_TAG,__VA_ARGS__);\
}

#define CFG_TYPE_TOML 1
#define CFG_TYPE_YAML 0

#if CFG_TYPE_YAML
#include "yaml-cpp/yaml.h"

static_assert(sizeof(YAML::Node*)==8,"");

static YAML::Node* j_open_config_str(JNIEnv* env,jobject self,jstring jconfig_str){
    jboolean is_copy=false;
    const char* config_str=env->GetStringUTFChars(jconfig_str,&is_copy);
    std::istringstream config_stream(config_str);
    YAML::Node* config_node = new YAML::Node(YAML::Load(config_stream));
    env->ReleaseStringUTFChars(jconfig_str,config_str);
    return config_node;
}

static jstring j_close_config_str(JNIEnv* env,jobject self,YAML::Node* config_node){
    YAML::Emitter out;
    out << *config_node;
    jboolean is_copy=false;

    jstring result=env->NewStringUTF(out.c_str());
    delete config_node;
    return result;
}

static YAML::Node* j_open_config_file(JNIEnv* env,jobject self,jstring config_path){
    jboolean is_copy=false;
    const char* path=env->GetStringUTFChars(config_path,&is_copy);

    YAML::Node* config_node = new YAML::Node(YAML::LoadFile(path));
    env->ReleaseStringUTFChars(config_path,path);
    return config_node;
}

static jstring j_load_config_entry(JNIEnv* env,jobject self,YAML::Node* config_node,jstring tag){
    jboolean is_copy=false;
    const char* tag_cstr=env->GetStringUTFChars(tag,&is_copy);
    std::string  tag_str(tag_cstr);
    env->ReleaseStringUTFChars(tag,tag_cstr);
    size_t pos=tag_str.find('|');
    std::string  parent=tag_str.substr(0,pos);
    std::string  child=tag_str.substr(pos+1);

    switch(std::count(child.begin(),child.end(),'|')){
        case 0: {
            YAML::Node node=(*config_node)[parent][child];
            if(node.IsDefined())
                return env->NewStringUTF(node.as<std::string>().c_str());
        }
            break;
        case 1:{
            pos=child.find('|');
            std::string child2=child.substr(pos+1);
            child=child.substr(0,pos);

            YAML::Node node=(*config_node)[parent][child][child2];
            if(node.IsDefined())
                return env->NewStringUTF(node.as<std::string>().c_str());
        }
            break;

        case 2:{

            pos=child.find('|');
            std::string child2=child.substr(pos+1);
            child=child.substr(0,pos);
            pos=child2.find('|');
            std::string child3=child2.substr(pos+1);
            child2=child2.substr(0,pos);

            YAML::Node node=(*config_node)[parent][child][child2][child3];
            if(node.IsDefined())
                return env->NewStringUTF(node.as<std::string>().c_str());
        }
            break;

        default:
            LOGE("load_config_entry fail %s,level too deep",tag_str.c_str());
            break;
    }
    return NULL;
}


static jobjectArray j_load_config_entry_ty_arr(JNIEnv* env,jobject self,YAML::Node* config_node,jstring tag){
    jboolean is_copy=false;
    const char* tag_cstr=env->GetStringUTFChars(tag,&is_copy);
    std::string  tag_str(tag_cstr);
    env->ReleaseStringUTFChars(tag,tag_cstr);
    size_t pos=tag_str.find('|');
    std::string  parent=tag_str.substr(0,pos);
    std::string  child=tag_str.substr(pos+1);

    switch(std::count(child.begin(),child.end(),'|')){
        case 0: {
            YAML::Node node=(*config_node)[parent][child];
            if(node.IsDefined()) {
                std::vector<std::string> v=node.as<std::vector<std::string>>();
                jobjectArray arr=env->NewObjectArray(v.size(),env->FindClass("java/lang/String"),NULL);
                for(size_t i=0;i<v.size();i++){
                    env->SetObjectArrayElement(arr,i,env->NewStringUTF(v[i].c_str()));
                }
                return arr;
            }
        }

            break;

        default:
            LOGE("load_config_entry fail %s,level too deep",tag_str.c_str());
            break;
    }
    return NULL;
}

static void j_save_config_entry(JNIEnv* env,jobject self,YAML::Node* config_node,jstring tag,jstring val){

    jboolean is_copy=false;
    const char* tag_cstr=env->GetStringUTFChars(tag,&is_copy);
    std::string  tag_str(tag_cstr);
    env->ReleaseStringUTFChars(tag,tag_cstr);
    size_t pos=tag_str.find('|');
    std::string  parent=tag_str.substr(0,pos);
    std::string  child=tag_str.substr(pos+1);
    const char* val_cstr=env->GetStringUTFChars(val,&is_copy);

    switch(std::count(child.begin(),child.end(),'|')){
        case 0: {
            (*config_node)[parent][child] = std::string(val_cstr);
        }
            break;
        case 1:{
            pos=child.find('|');
            std::string child2=child.substr(pos+1);
            child=child.substr(0,pos);
            (*config_node)[parent][child][child2]=std::string(val_cstr);
        }
            break;

        case 2:{

            pos=child.find('|');
            std::string child2=child.substr(pos+1);
            child=child.substr(0,pos);
            pos=child2.find('|');
            std::string child3=child2.substr(pos+1);
            child2=child2.substr(0,pos);
            (*config_node)[parent][child][child2][child3]=std::string(val_cstr);
        }
            break;

        default:
            LOGE("save_config_entry fail %s,level too deep",tag_str.c_str());
            break;
    }

    env->ReleaseStringUTFChars(val,val_cstr);
}


static void j_save_config_entry_ty_arr(JNIEnv* env,jobject self,YAML::Node* config_node,jstring tag,jobjectArray val) {

    jboolean is_copy = false;
    const char *tag_cstr = env->GetStringUTFChars(tag, &is_copy);
    std::string tag_str(tag_cstr);
    env->ReleaseStringUTFChars(tag, tag_cstr);
    size_t pos = tag_str.find('|');
    std::string parent = tag_str.substr(0, pos);
    std::string child = tag_str.substr(pos + 1);

    switch (std::count(child.begin(), child.end(), '|')) {
        case 0: {
            std::vector<std::string> v;
            for (int i = 0; i < env->GetArrayLength(val); i++) {
                jstring jstr = (jstring) env->GetObjectArrayElement(val, i);
                const char *str = env->GetStringUTFChars(jstr, &is_copy);
                v.push_back(std::string(str));
                env->ReleaseStringUTFChars(jstr, str);
            }
            (*config_node)[parent][child] = v;
        }
            break;


        default:
        LOGE("save_config_entry fail %s,level too deep",tag_str.c_str());
            break;
    }
}
static void j_close_config_file(JNIEnv* env,jobject self,YAML::Node* config_node,jstring config_path){
    YAML::Emitter out;
    out << *config_node;
    jboolean is_copy=false;
    const char* path=env->GetStringUTFChars(config_path,&is_copy);
    std::ofstream fout(path);
    fout << out.c_str();
    fout.close();
    env->ReleaseStringUTFChars(config_path,path);
    delete config_node;
}
#elif CFG_TYPE_TOML
#include "cpptoml/include/cpptoml.h"
static std::shared_ptr<cpptoml::table>* j_open_config_str(JNIEnv* env,jobject self,jstring jconfig_str) {
    jboolean is_copy=false;
    const char* config_str=env->GetStringUTFChars(jconfig_str,&is_copy);
    if (!config_str) {
        LOGE("open_config_str: GetStringUTFChars returned null");
        return nullptr;
    }
    try {
        std::istringstream config_stream(config_str);
        cpptoml::parser p{config_stream};
        auto toml=p.parse();
        LOGE("open_config_str %d",toml.use_count());
        env->ReleaseStringUTFChars(jconfig_str,config_str);
        std::unique_ptr<std::shared_ptr<cpptoml::table>> toml_p=std::make_unique<std::shared_ptr<cpptoml::table>>(toml);
        return toml_p.release();
    } catch (const std::exception& e) {
        LOGE("open_config_str failed: %s", e.what());
        env->ReleaseStringUTFChars(jconfig_str,config_str);
        return nullptr;
    }
}
static jstring j_close_config_str(JNIEnv* env,jobject self,std::shared_ptr<cpptoml::table>* config_table){
    std::unique_ptr<std::shared_ptr<cpptoml::table>> toml_p;
    toml_p.reset(config_table);
    std::ostringstream out_strm;
    out_strm << *toml_p;
    std::string out=out_strm.str();
    jboolean is_copy=false;
    jstring result=env->NewStringUTF(out.c_str());
    return result;
}
static std::shared_ptr<cpptoml::table>* j_open_config_file(JNIEnv* env,jobject self,jstring config_path){
    jboolean is_copy=false;
    const char* path=env->GetStringUTFChars(config_path,&is_copy);
    if (!path) {
        LOGE("open_config_file: GetStringUTFChars returned null");
        return nullptr;
    }
    try {
        std::shared_ptr<cpptoml::table> toml=cpptoml::parse_file(path);
        env->ReleaseStringUTFChars(config_path,path);
        std::unique_ptr<std::shared_ptr<cpptoml::table>> toml_p=std::make_unique<std::shared_ptr<cpptoml::table>>(toml);
        return toml_p.release();
    } catch (const std::exception& e) {
        LOGE("open_config_file failed: %s", e.what());
        env->ReleaseStringUTFChars(config_path,path);
        return nullptr;
    }
}
static jstring j_load_config_entry(JNIEnv* env,jobject self,std::shared_ptr<cpptoml::table>* config_table,jstring tag){
    jboolean is_copy=false;
    const char* tag_cstr=env->GetStringUTFChars(tag,&is_copy);
    if (!tag_cstr) return NULL;
    std::string  tag_str(tag_cstr);
    env->ReleaseStringUTFChars(tag,tag_cstr);

    size_t pos=tag_str.find('|');
    if (pos == std::string::npos) {
        LOGE("load_config_entry: invalid tag format (no '|'): %s", tag_str.c_str());
        return NULL;
    }
    std::string  table_name=tag_str.substr(0,pos);
    std::string  key_name=tag_str.substr(pos+1);

    std::shared_ptr<cpptoml::table> table=(*config_table)->get_table(table_name);
    if(!table){
        return NULL;
    }
    if(const auto val=table->get_as<bool>(key_name);val){
        std::string val_str=*val?"true":"false";
        LOGE("load_config_entry Z %s %s",tag_str.c_str(),val_str.c_str());
        jstring result=env->NewStringUTF(val_str.c_str());
        return result;
    }
    else if(const auto val=table->get_as<int>(key_name);val){
        std::string val_str=std::to_string(*val);
        LOGE("load_config_entry I %s %s",tag_str.c_str(),val_str.c_str());
        jstring result=env->NewStringUTF(val_str.c_str());
        return result;
    }
    else if(const auto val=table->get_as<double>(key_name);val){
        std::string val_str=std::to_string(*val);
        LOGE("load_config_entry F %s %s",tag_str.c_str(),val_str.c_str());
        jstring result=env->NewStringUTF(val_str.c_str());
        return result;
    }
    else if(const auto val=table->get_as<std::string>(key_name);val){
        LOGE("load_config_entry S %s %s",tag_str.c_str(),val->c_str());
        jstring result=env->NewStringUTF(val->c_str());
        return result;
    }

    return NULL;
}
static jobjectArray j_load_config_entry_ty_arr(JNIEnv* env,jobject self,std::shared_ptr<cpptoml::table>* config_table,jstring tag){
    return NULL;
}
static void j_save_config_entry(JNIEnv* env,jobject self,std::shared_ptr<cpptoml::table>* config_table,jstring tag,jstring val){
    jboolean is_copy=false;
    const char* tag_cstr=env->GetStringUTFChars(tag,&is_copy);
    const char* val_cstr=env->GetStringUTFChars(val,&is_copy);
    if (!tag_cstr || !val_cstr) {
        if (tag_cstr) env->ReleaseStringUTFChars(tag, tag_cstr);
        if (val_cstr) env->ReleaseStringUTFChars(val, val_cstr);
        LOGE("save_config_entry: GetStringUTFChars returned null");
        return;
    }
    std::string  tag_str(tag_cstr);
    std::string  val_str(val_cstr);
    env->ReleaseStringUTFChars(tag,tag_cstr);
    env->ReleaseStringUTFChars(val,val_cstr);

    size_t pos=tag_str.find('|');
    if (pos == std::string::npos) {
        LOGE("save_config_entry: invalid tag format (no '|'): %s", tag_str.c_str());
        return;
    }
    std::string  table_name=tag_str.substr(0,pos);
    std::string  key_name=tag_str.substr(pos+1);
    std::shared_ptr<cpptoml::table> table=(*config_table)->get_table(table_name);
    if (!table) {
        LOGE("save_config_entry: table '%s' not found, creating it", table_name.c_str());
        table = cpptoml::make_table();
        (*config_table)->insert(table_name, table);
    }
    if(val_str=="true"||val_str=="false"){
        LOGE("save_config_entry Z %s %s",tag_str.c_str(),val_str.c_str());
        table->insert(key_name,val_str=="true");
    }
    else if(val_str.find('.')!=std::string::npos){
        LOGE("save_config_entry F %s %s",tag_str.c_str(),val_str.c_str());
        try {
            table->insert(key_name,std::stod(val_str));
        } catch (const std::exception& e) {
            LOGE("save_config_entry: stod failed for '%s': %s", val_str.c_str(), e.what());
            table->insert(key_name,val_str);
        }
    }
    else if(val_str.size()>0){
        const bool has_sign = val_str[0]=='-';
        const auto begin = has_sign ? val_str.begin()+1 : val_str.begin();
        if((!has_sign || val_str.size()>1) && begin!=val_str.end() && std::all_of(begin,val_str.end(),::isdigit)){
            LOGE("save_config_entry I %s %s",tag_str.c_str(),val_str.c_str());
            try {
                table->insert(key_name,std::stoi(val_str));
            } catch (const std::exception& e) {
                LOGE("save_config_entry: stoi failed for '%s': %s", val_str.c_str(), e.what());
                table->insert(key_name,val_str);
            }
        }
        else{
            LOGE("save_config_entry S %s %s",tag_str.c_str(),val_str.c_str());
            table->insert(key_name,val_str);
        }
    }
    else{
        LOGE("save_config_entry S %s %s",tag_str.c_str(),val_str.c_str());
        table->insert(key_name,val_str);
    }
}
static void j_save_config_entry_ty_arr(JNIEnv* env,jobject self,std::shared_ptr<cpptoml::table>* config_table,jstring tag,jobjectArray val) {
}
static void j_close_config_file(JNIEnv* env,jobject self,std::shared_ptr<cpptoml::table>* config_table,jstring config_path){
    jboolean is_copy=false;
    const char* path=env->GetStringUTFChars(config_path,&is_copy);
    std::ofstream fout(path);
    fout << **config_table;
    fout.close();
    env->ReleaseStringUTFChars(config_path,path);
    std::unique_ptr<std::shared_ptr<cpptoml::table>> toml_p;
    toml_p.reset(config_table);
}
#else
#error "CFG_TYPE_XXX not defined"
#endif

int register_Emulator$Config(JNIEnv* env){

    static const JNINativeMethod methods[] = {

            { "native_open_config", "(Ljava/lang/String;)J", (void *) j_open_config_str },
            { "native_close_config", "(J)Ljava/lang/String;", (void *) j_close_config_str },
            { "native_open_config_file", "(Ljava/lang/String;)J", (void *) j_open_config_file },
            { "native_load_config_entry", "(JLjava/lang/String;)Ljava/lang/String;", (void *) j_load_config_entry },
            { "native_load_config_entry_ty_arr", "(JLjava/lang/String;)[Ljava/lang/String;", (void *) j_load_config_entry_ty_arr },
            { "native_save_config_entry", "(JLjava/lang/String;Ljava/lang/String;)V", (void *) j_save_config_entry },
            { "native_save_config_entry_ty_arr", "(JLjava/lang/String;[Ljava/lang/String;)V", (void *) j_save_config_entry_ty_arr },
            { "native_close_config_file", "(JLjava/lang/String;)V", (void *) j_close_config_file },
    };
    jclass clazz = env->FindClass("aenu/emulator/Emulator$Config");
    return env->RegisterNatives(clazz,methods, sizeof(methods)/sizeof(methods[0]));
}

//public native void setup_game_path(String path);
//public native void setup_game_path(Emulator.Path path);
static void j_setup_game_path(JNIEnv* env,jobject self,jobject path ){

    jclass path_cls;
    if(env->IsInstanceOf(path,path_cls=env->FindClass("aenu/emulator/Emulator$Path"))){
        jfieldID f_fd  = env->GetFieldID(path_cls, "fd", "I");
        ae::boot_game_fd=env->GetIntField(path, f_fd);
        if(ae::boot_game_fd!=-1)
            ae::boot_type=ae::BOOT_TYPE_WITH_FD;
        else{
            jfieldID f_uri  = env->GetFieldID(path_cls, "uri", "Ljava/lang/String;");
            jstring j_uri = reinterpret_cast<jstring>(env->GetObjectField(path, f_uri));
            if (!j_uri) {
                LOGE("setup_game_path: uri field is null");
                return;
            }
            const char* uri_chars = env->GetStringUTFChars(j_uri, nullptr);
            if (!uri_chars) {
                LOGE("setup_game_path: GetStringUTFChars returned null");
                return;
            }
            ae::boot_game_uri=std::string(uri_chars);
            env->ReleaseStringUTFChars(j_uri, uri_chars);
            ae::boot_type=ae::BOOT_TYPE_WITH_URI;
        }
    }
    else if(env->IsInstanceOf(path,path_cls=env->FindClass("java/lang/String"))){
        const char*  _path = env->GetStringUTFChars(reinterpret_cast<jstring>(path), nullptr);
        ae::boot_type=ae::BOOT_TYPE_WITH_PATH;
        ae::boot_game_path=std::string(_path);
        env->ReleaseStringUTFChars(reinterpret_cast<jstring>(path), _path);
    }
}

static std::atomic<bool> g_booted{false};

static void j_boot(JNIEnv* env,jobject self){
    bool expected = false;
    if (!g_booted.compare_exchange_strong(expected, true)) {
        __android_log_print(ANDROID_LOG_WARN, "ax360e_native",
                            "j_boot: already booted, ignoring duplicate call");
        return;
    }
    std::thread(ae::main_thr).detach();
}

static void j_notify_surface_changed(JNIEnv* env,jobject self){
    ae::notify_surface_changed();
}

static void j_change_surface(JNIEnv* env,jobject self,jint w,jint h){
    ae::window_width=w;
    ae::window_height=h;
}

static void j_setup_surface(JNIEnv* env,jobject self,jobject surface){

    if(ae::window){
        ANativeWindow_release(ae::window);
        ae::window=nullptr;}
    if(surface) {
        ae::window=ANativeWindow_fromSurface(env,surface);
        ae::window_width=ANativeWindow_getWidth(ae::window);
        ae::window_height=ANativeWindow_getHeight(ae::window);
    }
}

static void j_key_event(JNIEnv* env,jobject self,jint key_code,jboolean pressed,jint value){
    ae::key_event(key_code,pressed,value);
}

static void j_pause(JNIEnv* env,jobject self){
    ae::pause();
}

static void j_resume(JNIEnv* env,jobject self){
    ae::resume();
}

static jboolean j_is_running(JNIEnv* env,jobject self){
    return ae::is_running();
}

static jboolean j_is_paused(JNIEnv* env,jobject self){
    return ae::is_paused();
}

static void j_quit(JNIEnv* env,jobject self){
    ae::quit();
}

int register_Emulator(JNIEnv* env){
    static const JNINativeMethod methods[] = {
            { "setup_game_path", "(Ljava/lang/String;)V", (void *) j_setup_game_path },
            { "setup_game_path", "(Laenu/emulator/Emulator$Path;)V", (void *) j_setup_game_path },
            { "setup_surface", "(Landroid/view/Surface;)V", (void *) j_setup_surface },
            { "boot", "()V", (void *) j_boot },
            { "key_event", "(IZI)V", (void *) j_key_event },
            { "quit", "()V", (void *) j_quit },
            { "is_running", "()Z", (void *) j_is_running },
            { "is_paused", "()Z", (void *) j_is_paused },
            { "pause", "()V", (void *) j_pause },
            { "resume", "()V", (void *) j_resume },
            { "change_surface", "(II)V", (void *) j_change_surface },
            { "notify_surface_changed", "()V", (void *) j_notify_surface_changed },
    };
    return env->RegisterNatives(env->FindClass("aenu/emulator/Emulator"),methods, sizeof(methods)/sizeof(methods[0]));
}