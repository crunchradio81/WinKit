#!/usr/bin/env python3
"""A18 offline regression: exact pinned core hooks and guarded diagnostics."""
from pathlib import Path
import importlib.util
root=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location("winkit_overlay",root/"scripts/apply_a2_overlay.py")
p=importlib.util.module_from_spec(spec); spec.loader.exec_module(p)
host='''extern "C" int dosbox_x_main(int argc, char *argv[]);
extern "C" int retrodos_host_run(int argc, char **argv) {
    g_embedded.store(true);
    g_running.store(true);
    const int rc = dosbox_x_main(argc, argv);
}'''
engine='''static void GUI_StartUp() {
    DOSBoxMenu::item *item;
    AddExitFunction(AddExitFunctionFuncPair(GUI_ShutDown));
    GUI_LoadFonts();
    GFX_SetIcon();
    SetPriority(sdl.priority.focus); //Assume focus on startup
    /* Get some Event handlers */
    const int display = section->Get_int("display");
    // output type selection
    SetWindowTransparency(section->Get_int("transparency"));
    UpdateWindowDimensions();
    ApplyPreventCap();
}
#if defined(RETRODOS_HOST_PROVIDES_MAIN)
int dosbox_x_engine_main(int argc, char* argv[]) SDL_MAIN_NOEXCEPT {
    CommandLine com_line(argc,argv);
    Config myconf(&com_line);
    if (!DOSBOX_parse_argv()) return 1;
        /* -- SDL init */
        if (SDL_Init(SDL_INIT_AUDIO|SDL_INIT_VIDEO|SDL_INIT_TIMER|SDL_INIT_NOPARACHUTE) >= 0)
            sdl.inited = true;
        else
            E_Exit("Can't init SDL %s",SDL_GetError());
        /* -- -- other steps to prepare SDL window/output */
        SDL_Prepare();
        /* -- -- Keyboard layout detection and setup */
        KeyboardLayoutDetect();
        SetMapperKeyboardLayout(host_keyboard_layout);
        LOG(LOG_MISC,LOG_DEBUG)("Initializing SDL joystick subsystem...");
        /* GUI init */
        GUI_StartUp();
        AllocCallback1();
        /* Start up main machine */
        MAPPER_StartUp();
        DOSBOX_InitTickLoop();
        DOSBOX_RealInit();
            DOSBOX_RunMachine();
}
extern "C" int dosbox_x_main(int argc, char *argv[]) {
#if defined(RETRODOS_HOST_PROVIDES_MAIN)
    return dosbox_x_engine_main(argc, argv);
#else
    return main(argc, argv);
#endif
}'''
for fn, source, milestones in (
    (p.patch_core_host,host,(20,29)),
    (p.patch_core_sdlmain,engine,(21,22,23,24,25,26,27,28,30)),
):
    modified=fn(source)
    for n in milestones:
        assert f"WinKit A18 stage {n}:" in modified, n
    if fn == p.patch_core_sdlmain:
        for stage in "ABCDEFGHI":
            assert f"WinKit A21 stage 26{stage}:" in modified, stage
        for stage in range(40, 52):
            assert f"WinKit A22 stage {stage}:" in modified, stage
        assert '#define WK_A22_GUI(s) winkit_a18_core_checkpoint(s)' in modified
        assert 'WinKit A20 stage 20A: DOSBox-X wrapper entered' in modified
        assert '#error "WinKit Android must compile the embedded DOSBox-X entry"' in modified
    try: fn(modified)
    except ValueError: pass
    else: raise AssertionError("double instrumentation accepted")
    broken = (source.replace("dosbox_x_main(argc, argv);","invalid();") if fn == p.patch_core_host
              else source.replace("DOSBOX_RunMachine();","invalid();"))
    try: fn(broken)
    except ValueError: pass
    else: raise AssertionError("unrecognized pinned-source change accepted")
p_text=(root/"scripts/apply_a2_overlay.py").read_text()
assert 'retrodos_host_set_window(borrowed)' in p_text
assert 'extern "C" void winkit_a18_core_checkpoint(const char *stage)' in p_text
assert "repo / 'core/src/gui/sdlmain.cpp': patch_core_sdlmain" in p_text
verifier=(root/"scripts/verify_a8_upstream.py").read_text()
assert "core_changed != set(CORE_PATCHES)" in verifier
assert "core file not EXACT A18 diagnostic overlay" in verifier
activity=(root/"overlay/kotlin/WinKitSetupActivity.kt").read_text()
assert 'Selected document provider returned no writable stream' in activity
assert 'Could not open save destination' not in activity
assert 'Diagnostic export failed (' in activity
assert 'const val UI_RELEASE = "A22"' in activity
assert 'WinKit-A22-startup-diagnostics.zip' in activity
assert 'WinKit A22 startup diagnostics (GUI_StartUp probes)' in activity
assert 'setOnApplyWindowInsetsListener' in activity
assert 'button.isAllCaps = false' in activity
assert 'button.setSingleLine(false)' in activity
assert 'isFillViewport = true' in activity
assert 'it.setTextColor(bodyColor)' in activity
assert 'addAction(export)' in activity
build_patch=p.patch_build('''TOOLCHAIN="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64"
-DSDL_SHARED=ON -DSDL_STATIC=OFF -DSDL_TEST_LIBRARY=OFF
    cmake --build "$OUT/sdl3-build" -j"$JOBS"
APP_LIBS="$HERE/app/libs"
CXXFLAGS="-fPIC -O2 -g0 -DANDROID"
''')
assert 'CXXFLAGS="-fPIC -O2 -g0 -DANDROID -DRETRODOS_HOST_PROVIDES_MAIN"' in build_patch
build_recipe=(root/"scripts/BUILD_A10_ANDROID.sh").read_text()
assert "WinKit A20 stage 20A: DOSBox-X wrapper entered" in build_recipe
assert "WinKit A18 stage 21: engine main entered" in build_recipe
print("PASS A22 GUI startup checkpoints, SDL preparation, Android entry flags and diagnostics")
