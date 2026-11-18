"""Write a confirmation grid with the top N candidates of a sweep.

Usage: python3 -I scripts/sweeps/pick-top.py <results.csv> <N> <grid-out> [comment]
Order: fewer extinct runs, more fully passing runs, smaller worst balance
among the surviving runs, smaller worst hotspot span. The current defaults
are always confirmed too (EcologySweep adds them as the base candidate).
"""
import collections
import csv
import sys

rows = list(csv.DictReader(open(sys.argv[1])))
top_n = int(sys.argv[2])
groups = collections.defaultdict(list)
for row in rows:
    groups[row['overrides']].append(row)

def score(item):
    runs = item[1]
    extinct = sum(int(r['extinctionStep']) >= 0 for r in runs)
    passed = sum(r['gate'] == 'PASS' for r in runs)
    alive = [float(r.get('balanceMean') or r['balance']) for r in runs if int(r['extinctionStep']) < 0]
    worst = max(alive) if alive else float('inf')
    span = max(int(r['maxOverloadSpan']) for r in runs)
    return (extinct, -passed, worst, span)

ranked = sorted(groups.items(), key=score)
with open(sys.argv[3], 'w') as out:
    out.write('# ' + (sys.argv[4] if len(sys.argv) > 4 else 'confirmation grid') + '\n')
    for overrides, runs in ranked[:top_n]:
        if overrides != 'default':
            out.write('candidate ' + overrides + '\n')
        print(score((overrides, runs)), overrides)
