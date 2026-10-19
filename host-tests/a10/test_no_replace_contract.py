from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
for src in ('overlay/kotlin/GuestStore.kt','overlay/kotlin/OfflineSmokeFixture.kt','overlay/kotlin/WinKitSetupActivity.kt','android/app/src/main/java/org/winkit/android/GuestStore.kt'):
    s=(ROOT/src).read_text()
    assert '.renameTo(' not in s, f'unsafe rename publication: {src}'
    assert 'Files.createLink(' in s, f'missing no-replace publication: {src}'
    assert 'createNewFile()' in s, f'missing exclusive staging: {src}'
    print('PASS no-replace source:',src)
assert (ROOT/'scripts/BUILD_A9_ANDROID.sh').exists()
print('PASS A10 source publication contracts')
