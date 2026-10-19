#!/usr/bin/env python3
"""Independent native-vs-Kotlin offline transport lockdown differential test.

Tests text policy only; not an Android compile, guest boot, or live link.
"""
from __future__ import annotations
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
NATIVE_SRC = ROOT / 'host-tests/a4/boot_policy_cli.cpp'
KOTLIN_SRC = ROOT / 'overlay/kotlin/OfflineBootPolicy.kt'
KOTLIN_TEST = ROOT / 'host-tests/a9/OfflinePolicyBatch.kt'
DISK = '/data/user/0/org.winkit.android.a2/files/machines/YARDOMOB.raw'

BASE = ('[dosbox]\nmemsize=16\n[ne2000]\nne2000=false\n[ipx]\nipx=false\n'
        '[autoexec]\n@echo off\nIMGMOUNT 2 "' + DISK + '" -t hdd -fs none -size 512,63,16,8\nBOOT C:\n')


def cases():
    out = {'base offline profile': (BASE, True)}
    additions = {
        'sblaster': '[sblaster]\nsbtype=sb16\n',
        'midi': '[midi]\nmpu401=intelligent\n',
        'serial disabled': '[serial]\nserial1=disabled\n',
        'serial dummy': '[serial]\nserial1=dummy\n',
        'parallel none': '[parallel]\nparallel1=none\n',
    }
    for name, addition in additions.items():
        out[name] = (BASE.replace('[autoexec]', addition+'[autoexec]'), True)
    prohibited = {
        'ethnet section': '[ethnet]\nserver=192.0.2.1\n',
        'ethernet section': '[ethernet]\nmode=udp\n',
        'network section': '[network]\nmode=bridge\n',
        'ipxnet section': '[ipxnet]\nconnect=192.0.2.1\n',
        'modem section': '[modem]\nport=23\n',
        'slirp section': '[slirp]\nenabled=true\n',
        'serial modem': '[serial]\nserial1=modem\n',
        'serial directserial': '[serial]\nserial1=directserial realport:ttyUSB0\n',
        'serial nullmodem': '[serial]\nserial2=nullmodem\n',
        'parallel host': '[parallel]\nparallel1=printer\n',
        'parallel bad syntax': '[parallel]\nparallel1\n',
        'foreign conf file': '[dosbox_extra]\nconfigfile=/tmp/external.conf\n',
        'autoload override': '[dosbox_extra]\nautoexec=override.conf\n',
        'direct tunnel property': '[dosbox_extra]\ntunnel=192.0.2.1\n',
        'secondary ethernet': '[dosbox_extra]\nethernet=enable\n',
        'host selector': '[dosbox_extra]\nhostip=192.0.2.1\n',
        'sdl net': '[dosbox_extra]\nsdl_net=enabled\n',
    }
    for name, addition in prohibited.items():
        out[name] = (BASE.replace('[autoexec]', addition+'[autoexec]'), False)
    mutations = {
        'switch interface': BASE.replace('ne2000=false', 'ne2000=true'),
        'switch ipx': BASE.replace('ipx=false', 'ipx=true'),
        'inject ethnet command': BASE.replace('BOOT C:', 'ETHNET CONNECT 192.0.2.1\nBOOT C:'),
        'extra mount': BASE.replace('BOOT C:', 'MOUNT D /tmp\nBOOT C:'),
        'second boot': BASE + 'BOOT C:\n',
        'fake path': BASE.replace('YARDOMOB.raw', 'YARDO.raw'),
        'chaining': BASE.replace('BOOT C:', 'BOOT C: & ECHO BYPASS'),
        'different disk option': BASE.replace('-t hdd', '-t floppy'),
        'removed ipx line': BASE.replace('ipx=false\n', ''),
        'duplicate section': BASE.replace('[autoexec]', '[ne2000]\nne2000=false\n[autoexec]'),
        'nul byte': BASE + '\0',
    }
    for name, contents in mutations.items():
        out[name] = (contents, False)
    # Stable generated mutations of benign settings, all must remain offline.
    for n in range(40):
        value = 8 + n
        out[f'benign mixer #{n}'] = (BASE.replace('[autoexec]', f'[mixer]\nrate={value*1000}\n[autoexec]'), True)
    return out


class ParityTests(unittest.TestCase):
    def test_native_and_kotlin_74_case_matrix(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            native = root / 'native'
            kotlin = root / 'policy.jar'
            subprocess.run(['c++','-std=c++17','-O2','-Wall','-Wextra','-Werror',
                            '-I',str(ROOT/'overlay/native'),str(NATIVE_SRC),'-o',str(native)],check=True)
            subprocess.run(['kotlinc',str(KOTLIN_SRC),str(KOTLIN_TEST),'-include-runtime','-d',str(kotlin)],
                           check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
            fixtures = cases()
            files = []
            for index, (_, (source, _)) in enumerate(fixtures.items()):
                path = root / f'{index:03}.conf'
                path.write_bytes(source.encode('utf-8'))
                files.append(path)
            result = subprocess.run(['java','-jar',str(kotlin),DISK,*map(str,files)],
                                    check=True,capture_output=True,text=True)
            kresults = {k:int(v) for k,v in (line.split('\t') for line in result.stdout.splitlines())}
            self.assertEqual(len(kresults),len(fixtures))
            for (name, (_, expected)), path in zip(fixtures.items(), files):
                native_pass = subprocess.run([str(native),str(path),DISK],
                                             stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode == 0
                kotlin_pass = bool(kresults[path.name])
                self.assertEqual(native_pass, kotlin_pass, f'native/Kotlin mismatch in {name}')
                self.assertEqual(expected, native_pass, f'wrong offline acceptance: {name}')
            print(f'A9 independent native/Kotlin parity: {len(fixtures)} cases passed')

if __name__ == '__main__':
    unittest.main(verbosity=2)
