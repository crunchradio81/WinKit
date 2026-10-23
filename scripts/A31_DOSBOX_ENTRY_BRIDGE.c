/* A31 isolated Android native-entry tracer.
 * SDLActivity enters this library's SDL_main; it forwards to the ORIGINAL
 * unmodified libmain.so from the verified native build.
 * Only checkpoints. This does not patch DOSBox-X or mount guest media. */
#include <jni.h>
#include <SDL.h>
#include <android/log.h>
#include <dlfcn.h>
#include <stdatomic.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdio.h>
#include <string.h>

static atomic_int g_stage = ATOMIC_VAR_INIT(0);
static atomic_int g_result = ATOMIC_VAR_INIT(0);
static char g_error[384];

JNIEXPORT jint JNICALL
Java_org_winkit_android_a31smoke_A31SDLActivity_entryStage(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    return atomic_load(&g_stage);
}
JNIEXPORT jint JNICALL
Java_org_winkit_android_a31smoke_A31SDLActivity_entryResult(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    return atomic_load(&g_result);
}
JNIEXPORT jstring JNICALL
Java_org_winkit_android_a31smoke_A31SDLActivity_entryError(JNIEnv *env, jclass clazz) {
    (void)clazz;
    return (*env)->NewStringUTF(env, g_error);
}

static void capture_stdio(void) {
    /* SDL_AndroidGetInternalStoragePath is backed by the app's own files dir. */
    const char *root = SDL_AndroidGetInternalStoragePath();
    if (!root || !*root) return;
    char path[1024];
    int n = snprintf(path, sizeof(path), "%s/a31-dosbox-stdout.txt", root);
    if (n <= 0 || n >= (int)sizeof(path)) return;
    int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (fd >= 0) {
        fflush(stderr);
        fflush(stdout);
        dup2(fd, STDERR_FILENO);
        dup2(fd, STDOUT_FILENO);
        if (fd > STDERR_FILENO) close(fd);
        setvbuf(stderr, NULL, _IONBF, 0);
        setvbuf(stdout, NULL, _IONBF, 0);
    }
}

int SDL_main(int argc, char **argv) {
    atomic_store(&g_stage, 1);  /* SDL2 called the bridge */
    __android_log_print(ANDROID_LOG_INFO,"WinKitA31Entry","Bridge SDL_main entered");
    capture_stdio();
    fprintf(stderr, "[A31 ENTRY] native SDL_main bridge entered\n");
    void *handle = dlopen("libmain.so", RTLD_NOW | RTLD_LOCAL);
    if (!handle) {
        const char *error = dlerror();
        snprintf(g_error,sizeof(g_error),"dlopen libmain.so: %s",error?error:"unknown error");
        atomic_store(&g_stage, -1);
        fprintf(stderr,"[A31 ENTRY] %s\n",g_error);
        __android_log_print(ANDROID_LOG_ERROR,"WinKitA31Entry","%s",g_error);
        return 101;
    }
    atomic_store(&g_stage, 2);  /* DOSBox-X library opened */
    dlerror();
    int (*dosbox_main)(int,char**) = (int (*)(int,char**))dlsym(handle, "SDL_main");
    const char *error = dlerror();
    if (error || !dosbox_main) {
        snprintf(g_error,sizeof(g_error),"dlsym DOSBox SDL_main: %s",error?error:"not exported");
        atomic_store(&g_stage, -2);
        fprintf(stderr,"[A31 ENTRY] %s\n",g_error);
        return 102;
    }
    fprintf(stderr,"[A31 ENTRY] calling real DOSBox-X SDL_main\n");
    __android_log_print(ANDROID_LOG_INFO,"WinKitA31Entry","Calling real DOSBox-X SDL_main");
    atomic_store(&g_stage, 3); /* handed execution into DOSBox-X */
    int code = dosbox_main(argc, argv);
    atomic_store(&g_result, code);
    atomic_store(&g_stage, 4); /* DOSBox-X returned */
    fprintf(stderr,"[A31 ENTRY] DOSBox-X SDL_main returned code=%d\n",code);
    __android_log_print(ANDROID_LOG_INFO,"WinKitA31Entry","DOSBox SDL_main returned %d",code);
    return code;
}
