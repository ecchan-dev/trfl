#!/usr/bin/env python3
"""Worker-invoked, explicit phone install. Never starts an emulator."""
import argparse
import hashlib
import os
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
parser.add_argument('--expected-device', required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
env = os.environ.copy()
env.setdefault('JAVA_HOME', str(Path.home() / '.jdks/jbr-21.0.11'))
env.setdefault('ANDROID_HOME', str(Path.home() / 'Android/Sdk'))
adb = str(Path(env['ANDROID_HOME']) / 'platform-tools/adb')

def phone(*cmd):
    return subprocess.check_output([adb, '-s', args.serial, *cmd], text=True).strip()

assert phone('shell', 'getprop', 'ro.serialno') == args.expected_device, 'Wrong target phone'
commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
subprocess.run(['./gradlew', ':app:assembleRelease', ':app:lintRelease', ':app:testReleaseUnitTest', f'-PtrflCommit={commit}'], cwd=root, env=env, check=True)
# Compose's first-use rendering is substantially slower in debuggable builds.
# Release uses the same local signing certificate, allowing an in-place update.
apk = root / 'app/build/outputs/apk/release/app-release.apk'
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
print(phone('install', '-r', str(apk)), flush=True)
path = phone('shell', 'pm', 'path', 'org.trfl.app').splitlines()[0].removeprefix('package:')
installed = phone('shell', 'sha256sum', path).split()[0]
assert installed == digest, 'Installed APK does not match built APK'
print(phone('shell', 'am', 'start', '-n', 'org.trfl.app/.MainActivity'), flush=True)
print(f'VERIFIED phone APK sha256={digest} source_commit={commit}', flush=True)
