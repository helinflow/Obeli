#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <algorithm>
#include <thread>
#include "llama.h"

static llama_model * g_model=nullptr;
static llama_context * g_ctx=nullptr;
static std::mutex g_mutex;

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_helinflow_obeli_MainActivity_nativeLoadModel(JNIEnv *env,jclass,jstring jpath) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const char *path=env->GetStringUTFChars(jpath,nullptr);
    if(g_ctx){llama_free(g_ctx);g_ctx=nullptr;}
    if(g_model){llama_model_free(g_model);g_model=nullptr;}
    llama_backend_init();
    llama_model_params mp=llama_model_default_params();
    mp.n_gpu_layers=0;
    g_model=llama_model_load_from_file(path,mp);
    env->ReleaseStringUTFChars(jpath,path);
    if(!g_model) return env->NewStringUTF("Não foi possível abrir o GGUF. Verifique o arquivo e a memória disponível.");
    llama_context_params cp=llama_context_default_params();
    cp.n_ctx=2048; cp.n_batch=512; cp.n_threads=std::max(2u,std::thread::hardware_concurrency());
    g_ctx=llama_init_from_model(g_model,cp);
    if(!g_ctx){llama_model_free(g_model);g_model=nullptr;return env->NewStringUTF("Memória insuficiente para criar o contexto do modelo.");}
    return env->NewStringUTF("OK");
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_helinflow_obeli_MainActivity_nativeGenerate(JNIEnv *env,jclass,jstring jprompt) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if(!g_model||!g_ctx) return env->NewStringUTF("Nenhum modelo carregado.");
    const char *raw=env->GetStringUTFChars(jprompt,nullptr);
    std::string prompt="User: "+std::string(raw)+"\nAssistant:";
    env->ReleaseStringUTFChars(jprompt,raw);
    const llama_vocab *vocab=llama_model_get_vocab(g_model);
    std::vector<llama_token> tokens(prompt.size()+32);
    int n=llama_tokenize(vocab,prompt.c_str(),(int)prompt.size(),tokens.data(),(int)tokens.size(),true,false);
    if(n<0) return env->NewStringUTF("Não foi possível tokenizar a mensagem.");
    tokens.resize(n);
    if(tokens.empty()) return env->NewStringUTF("Mensagem vazia.");
    if(llama_decode(g_ctx,llama_batch_get_one(tokens.data(),(int)tokens.size()))!=0)
        return env->NewStringUTF("Falha ao processar o contexto. Tente uma mensagem menor.");
    llama_sampler_chain_params sp=llama_sampler_chain_default_params();
    llama_sampler *sampler=llama_sampler_chain_init(sp);
    llama_sampler_chain_add(sampler,llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler,llama_sampler_init_top_p(0.9f,1));
    llama_sampler_chain_add(sampler,llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler,llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    std::string output; char piece[512];
    for(int i=0;i<256;i++){
        llama_token id=llama_sampler_sample(sampler,g_ctx,-1);
        llama_sampler_accept(sampler,id);
        if(llama_vocab_is_eog(vocab,id)) break;
        int len=llama_token_to_piece(vocab,id,piece,sizeof(piece),0,false);
        if(len>0) output.append(piece,len);
        if(llama_decode(g_ctx,llama_batch_get_one(&id,1))!=0) break;
    }
    llama_sampler_free(sampler);
    return env->NewStringUTF(output.empty()?"O modelo não gerou texto.":output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_helinflow_obeli_MainActivity_nativeUnloadModel(JNIEnv *,jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if(g_ctx){llama_free(g_ctx);g_ctx=nullptr;}
    if(g_model){llama_model_free(g_model);g_model=nullptr;}
}