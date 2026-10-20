from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
for src in ('overlay/kotlin/GuestStore.kt','overlay/kotlin/OfflineSmokeFixture.kt','overlay/kotlin/WinKitSetupActivity.kt','android/app/src/main/java/org/winkit/android/GuestStore.kt'):
    s=(ROOT/src).read_text()
    assert '.renameTo(' not in s, f'unsafe rename publication: {src}'
    assert 'NoReplacePublisher.publish(' in s, f'missing exclusive publication: {src}'
    assert 'Files.createLink(' not in s, f'optional Android hard link still present: {src}'
    assert 'createNewFile()' in s, f'missing exclusive staging: {src}'
    print('PASS no-replace source:',src)
safe = (ROOT/'overlay/kotlin/GuestStore.kt').read_text()
for token in ('StandardOpenOption.CREATE_NEW', 'LinkOption.NOFOLLOW_LINKS', 'output.force(true)', 'FileChannel.open('):
    assert token in safe, f'missing safe publication gate: {token}'
assert (ROOT/'scripts/BUILD_A9_ANDROID.sh').exists()
print('PASS A10 source publication contracts')
