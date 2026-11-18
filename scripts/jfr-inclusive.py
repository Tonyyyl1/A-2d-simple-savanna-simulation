#!/usr/bin/env python3
"""Inclusive sample share of method-name patterns in a JFR recording.
   usage: <jfr> print --events jdk.ExecutionSample --stack-depth 64 run.jfr > samples.txt
          python3 -I jfr-inclusive.py samples.txt PATTERN [PATTERN ...]
   Each pattern is a plain substring of "package.Class.method". The last line
   is the union: samples whose stack contains ANY pattern (no double counting)."""
import re, sys
path, pats = sys.argv[1], sys.argv[2:]
samples, cur = [], None
with open(path, errors='replace') as f:
    for line in f:
        if line.startswith('jdk.ExecutionSample'):
            cur = []; samples.append(cur)
        elif cur is not None:
            m = re.match(r'([\w.$<>]+)\(.*\)\s+line:', line.strip())
            if m: cur.append(m.group(1))
n = len(samples)
print('samples', n)
for p in pats:
    inc = sum(1 for s in samples if any(p in fr for fr in s))
    print(f'{p:55s} {inc:6d} {100.0*inc/n:6.2f}%')
uni = sum(1 for s in samples if any(p in fr for fr in s for p in pats))
print(f'{"UNION":55s} {uni:6d} {100.0*uni/n:6.2f}%   overhead vs. without = {100.0*uni/(n-uni):.2f}%')
