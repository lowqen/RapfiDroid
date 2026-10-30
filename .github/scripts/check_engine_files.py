"""The engine inside an APK is the engine in the repo, byte for byte.

Usage: check_engine_files.py <app.apk>

Every file under app/src/main/assets/engine and app/src/main/jniLibs must sit
in the APK (as assets/engine/... and lib/<abi>/...) with the same SHA-256.
Release shrinking (R8, Phase 7-1) works on code and resources only; this is
the check that it left the engine's binaries and weights alone. It also
prints what the APK is made of, so a run shows the size it ships at.
"""
import collections
import hashlib
import os
import sys
import zipfile

apk = zipfile.ZipFile(sys.argv[1])
names = set(apk.namelist())


def sha(data):
    return hashlib.sha256(data).hexdigest()


expected = {}
for root, prefix in (('app/src/main/assets/engine', 'assets/engine'), ('app/src/main/jniLibs', 'lib')):
    for dirpath, _, files in os.walk(root):
        for f in files:
            path = os.path.join(dirpath, f)
            rel = os.path.relpath(path, root).replace(os.sep, '/')
            expected[f'{prefix}/{rel}'] = sha(open(path, 'rb').read())

bad = []
for entry, digest in sorted(expected.items()):
    if entry not in names:
        bad.append(f'missing  {entry}')
    elif sha(apk.read(entry)) != digest:
        bad.append(f'CHANGED  {entry}')
    else:
        print(f'same     {entry}')

sizes, stored = collections.Counter(), collections.Counter()
for i in apk.infolist():
    n = i.filename
    key = 'dex' if n.endswith('.dex') else '/'.join(n.split('/')[:2]) if n.startswith(('assets/', 'lib/')) else n.split('/')[0]
    sizes[key] += i.file_size
    stored[key] += i.compress_size
print(f'\nAPK {os.path.getsize(sys.argv[1]) / 1e6:.2f} MB')
for k, v in stored.most_common(8):
    print(f'  {v / 1e6:7.2f} MB stored  {sizes[k] / 1e6:7.2f} MB raw  {k}')

if bad:
    print('\n'.join(bad))
    sys.exit(f'{len(bad)} engine file(s) differ from the repo')
print(f'\nengine files: {len(expected)} of {len(expected)} identical to the repo')
