"""Where a Flight Recorder recording allocated: `jfr print --json` in, top lists out.

Usage: alloc_top.py <events.json>

Each allocation event (sample, or TLAB refill) is charged, by its weight, to the
first frame of the app's own code on its stack (test and fake classes
skipped), and separately to the class of the object allocated.
"""
import collections
import json
import sys

APP = 'dev.gomoku.zenyth.'
SKIP = ('Test', 'Fake', 'BoardRig', 'PerfBaseline', 'EngineStreams')

events = json.load(open(sys.argv[1], encoding='utf-8'))['recording']['events']
by_frame, by_class, by_stack = collections.Counter(), collections.Counter(), collections.Counter()
by_top = collections.Counter()
total = 0
for e in events:
    v = e['values']
    # Sample events carry a weight; TLAB events the TLAB (or the object) size.
    w = int(v.get('weight') or v.get('tlabSize') or v.get('allocationSize') or 0)
    total += w
    by_class[(v.get('objectClass') or {}).get('name', '?')] += w
    frames = ((v.get('stackTrace') or {}).get('frames')) or []
    own = []
    for f in frames:
        m = f.get('method') or {}
        cls = (m.get('type') or {}).get('name', '').replace('/', '.')
        if cls.startswith(APP) and not any(s in cls for s in SKIP):
            own.append(f"{cls[len(APP):]}.{m.get('name')}:{f.get('lineNumber')}")
    by_frame[own[0] if own else '(no app frame)'] += w
    # Whatever the code: the frames nearest the allocation, for what the app
    # frames cannot reach (a stack deeper than the recorded 64 frames).
    top = []
    for f in frames[:8]:
        m = f.get('method') or {}
        cls = (m.get('type') or {}).get('name', '').replace('/', '.')
        top.append(f"{cls.rsplit('.', 1)[-1]}.{m.get('name')}")
    by_top[' < '.join(top)] += w
    by_stack[' < '.join(own[:3]) if own else '(no app frame)'] += w

def show(title, counter, n):
    print(f'== {title}')
    for k, w in counter.most_common(n):
        print(f'  {w / max(total, 1) * 100:5.1f} %  {w / 1e6:9.1f} MB  {k}')

print(f'sampled allocation: {total / 1e6:.1f} MB in {len(events)} samples')
show('first app frame', by_frame, 30)
show('first three app frames', by_stack, 25)
show('object class', by_class, 20)
show('nearest eight frames, any code', by_top, 12)
