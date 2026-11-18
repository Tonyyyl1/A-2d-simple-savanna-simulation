#!/usr/bin/env python3
"""usage: jfr-top.py samples.txt [N]  -- top leaf frames, top inclusive savanna.* methods, top first-savanna-frame (self attribution)"""
import re, sys, collections
path=sys.argv[1]; N=int(sys.argv[2]) if len(sys.argv)>2 else 12
samples=[];cur=None
for line in open(path,errors='replace'):
    if line.startswith('jdk.ExecutionSample'): cur=[]; samples.append(cur)
    elif cur is not None:
        m=re.match(r'([\w.$<>]+)\(.*\)\s+line:',line.strip())
        if m: cur.append(m.group(1))
n=len(samples); print('samples',n)
leaf=collections.Counter(s[0] for s in samples if s)
incl=collections.Counter(); first=collections.Counter(); jdk=collections.Counter()
for s in samples:
    seen=set()
    for f in s:
        if f.startswith('savanna.') and f not in seen: seen.add(f)
    for f in seen: incl[f]+=1
    fs=next((f for f in s if f.startswith('savanna.')),None)
    if fs: first[fs]+=1
    for f in s:
        if f.startswith('java.util.HashMap.') or f.startswith('java.util.TreeMap.') or f.startswith('java.util.HashSet.') or f.startswith('java.util.LinkedHashMap.') or f.startswith('java.util.Objects.hash'):
            jdk['(any frame in HashMap/TreeMap/HashSet/LinkedHashMap/Objects.hash)']+=1; break
print('-- top leaf frames'); [print(f'{100*v/n:6.2f}% {k}') for k,v in leaf.most_common(N)]
print('-- top inclusive savanna.* methods'); [print(f'{100*v/n:6.2f}% {k}') for k,v in incl.most_common(N*2)]
print('-- top first-savanna-frame (self attribution)'); [print(f'{100*v/n:6.2f}% {k}') for k,v in first.most_common(N)]
print('-- hash-structure frames anywhere on the stack'); [print(f'{100*v/n:6.2f}% {k}') for k,v in jdk.most_common(3)]
print('-- leaf in HashMap/TreeMap family', f"{100*sum(v for k,v in leaf.items() if k.startswith(('java.util.HashMap','java.util.TreeMap','java.util.LinkedHashMap','java.util.HashSet','java.util.Objects.hash')))/n:.2f}%")
