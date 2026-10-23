/* Isolated native SDL2 video/color probe: not DOSBox-X and no guest media. */
#define SDL_MAIN_HANDLED
#include <SDL.h>
#include <jni.h>
#include <android/log.h>
#include <stdatomic.h>

static atomic_int g_stage = ATOMIC_VAR_INIT(0);
static atomic_int g_frames = ATOMIC_VAR_INIT(0);

JNIEXPORT jint JNICALL
Java_org_winkit_android_a31smoke_A31RenderProbeActivity_probeStage(JNIEnv *e, jclass cls) {
    (void)e; (void)cls;
    return atomic_load(&g_stage);
}
JNIEXPORT jint JNICALL
Java_org_winkit_android_a31smoke_A31RenderProbeActivity_probeFrames(JNIEnv *e, jclass cls) {
    (void)e; (void)cls;
    return atomic_load(&g_frames);
}

int SDL_main(int argc, char **argv) {
    (void)argc; (void)argv;
    atomic_store(&g_stage, 1); /* Native entry called */
    __android_log_print(ANDROID_LOG_INFO, "WinKitA31Probe", "Native SDL_main entered");
    if (SDL_Init(SDL_INIT_VIDEO | SDL_INIT_EVENTS) < 0) {
        atomic_store(&g_stage, -1);
        __android_log_print(ANDROID_LOG_ERROR, "WinKitA31Probe", "SDL_Init: %s", SDL_GetError());
        return 1;
    }
    atomic_store(&g_stage, 2);
    SDL_Window *window = SDL_CreateWindow("A31 SDL2 color renderer",
        SDL_WINDOWPOS_UNDEFINED, SDL_WINDOWPOS_UNDEFINED, 640, 400, SDL_WINDOW_SHOWN);
    if (!window) {
        atomic_store(&g_stage, -2);
        __android_log_print(ANDROID_LOG_ERROR,"WinKitA31Probe","Window: %s",SDL_GetError());
        SDL_Quit();
        return 2;
    }
    atomic_store(&g_stage, 3);
    SDL_Renderer *renderer = SDL_CreateRenderer(window, -1, SDL_RENDERER_ACCELERATED);
    if (!renderer) renderer = SDL_CreateRenderer(window, -1, SDL_RENDERER_SOFTWARE);
    if (!renderer) {
        atomic_store(&g_stage, -3);
        __android_log_print(ANDROID_LOG_ERROR,"WinKitA31Probe","Renderer: %s",SDL_GetError());
        SDL_DestroyWindow(window);
        SDL_Quit();
        return 3;
    }
    atomic_store(&g_stage, 4);
    Uint32 started = SDL_GetTicks();
    int running = 1;
    while (running && SDL_GetTicks() - started < 120000) {
        SDL_Event e;
        while (SDL_PollEvent(&e)) if (e.type == SDL_QUIT) running = 0;
        SDL_SetRenderDrawColor(renderer, 67, 25, 160, 255); /* purple backdrop */
        SDL_RenderClear(renderer);
        int w=0,h=0;
        SDL_GetRendererOutputSize(renderer,&w,&h);
        SDL_Rect first = {w/10,h/4,w*8/10,h/6};
        SDL_Rect second = {w/10,h/2,w*8/10,h/6};
        SDL_SetRenderDrawColor(renderer, 12, 235, 225, 255); /* cyan */
        SDL_RenderFillRect(renderer,&first);
        SDL_SetRenderDrawColor(renderer, 255, 222, 32, 255); /* yellow */
        SDL_RenderFillRect(renderer,&second);
        SDL_RenderPresent(renderer);
        atomic_fetch_add(&g_frames,1);
        atomic_store(&g_stage,5);
        SDL_Delay(33);
    }
    atomic_store(&g_stage,6);
    SDL_DestroyRenderer(renderer);
    SDL_DestroyWindow(window);
    SDL_Quit();
    return 0;
}
