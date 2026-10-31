"""Two ELF64 files are the same program: every byte equal outside .note.gnu.build-id.

Usage: same_elf_but_build_id.py <committed.so> <rebuilt.so>

The build id is a hash the linker takes before `llvm-strip`, over symbol
tables and debug info that name the build directory. A rebuild of the same
source with the same toolchain on another machine therefore differs in exactly
those 20 bytes and nowhere else (engine-repro.yml, first run 41160528754:
libengine.so, 20 bytes, all in the note). Exit 0 when that is all that
differs, 1 otherwise, with where the differences are.
"""
import struct
import sys


def sections(data):
    shoff, = struct.unpack_from('<Q', data, 0x28)
    shentsize, shnum, shstrndx = struct.unpack_from('<HHH', data, 0x3a)
    raw = [struct.unpack_from('<IIQQQQ', data, shoff + i * shentsize) for i in range(shnum)]
    strtab = raw[shstrndx][4]
    out = []
    for name, typ, _flags, _addr, off, size in raw:
        label = data[strtab + name:data.index(b'\0', strtab + name)].decode()
        if typ != 8:  # SHT_NOBITS takes no room in the file
            out.append((label, off, size))
    return out


a, b = (open(p, 'rb').read() for p in sys.argv[1:3])
if len(a) != len(b):
    print(f'sizes differ: {len(a)} / {len(b)}')
    sys.exit(1)
secs = sections(a)
note = next(((off, off + size) for label, off, size in secs if label == '.note.gnu.build-id'), (0, 0))
diff = [i for i in range(len(a)) if a[i] != b[i] and not note[0] <= i < note[1]]
if not diff:
    print('same program (build id aside)')
    sys.exit(0)
where = {}
for i in diff:
    label = next((s for s, off, size in secs if off <= i < off + size), '(between sections)')
    where[label] = where.get(label, 0) + 1
print(f'{len(diff)} bytes differ outside the build id:')
for label, n in sorted(where.items(), key=lambda kv: -kv[1]):
    print(f'  {label}: {n}')
sys.exit(1)
