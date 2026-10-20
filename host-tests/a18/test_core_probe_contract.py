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
engine='''#if defined(RETRODOS_HOST_PROVIDES_MAIN)
int dosbox_x_engine_main(int argc, char* argv[]) SDL_MAIN_NOEXCEPT {
    CommandLine com_line(argc,argv);
    Config myconf(&com_line);
    if (!DOSBOX_parse_argv()) return 1;
        /* -- SDL init */
        if (SDL_Init(SDL_INIT_AUDIO|SDL_INIT_VIDEO|SDL_INIT_TIMER|SDL_INIT_NOPARACHUTE) >= 0)
            sdl.inited = true;
        else
            E_Exit("Can't init SDL %s",SDL_GetError());
        /* Start up main machine */
        MAPPER_StartUp();
        DOSBOX_InitTickLoop();
        DOSBOX_RealInit();
            DOSBOX_RunMachine();
}'''
for fn, source, milestones in (
    (p.patch_core_host,host,(20,29)),
    (p.patch_core_sdlmain,engine,(21,22,23,24,25,26,27,28,30)),
):
    modified=fn(source)
    for n in milestones:
        assert f"WinKit A18 stage {n}:" in modified, n
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
assert 'const val UI_RELEASE = "A19"' in activity
assert 'WinKit-A19-startup-diagnostics.zip' in activity
assert 'WinKit A19 startup diagnostics (A18 core probes)' in activity
assert 'setOnApplyWindowInsetsListener' in activity
assert 'button.isAllCaps = false' in activity
assert 'button.setSingleLine(false)' in activity
assert 'isFillViewport = true' in activity
assert 'it.setTextColor(bodyColor)' in activity
assert 'addAction(export)' in activity
print("PASS A18 pinned core startup checkpoints and document-provider diagnostics")
