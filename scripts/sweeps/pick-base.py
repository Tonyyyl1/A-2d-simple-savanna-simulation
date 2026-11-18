"""Pick the hydration candidate robust over thirst-on seeds 1111-4111.

Usage: python3 -I scripts/sweeps/pick-base.py <dev results.csv> <confirm results.csv>
Prints the overrides of the candidate with the fewest thirst-on extinctions
over both files, then the smallest worst balance, then the smallest span.
"""
import collections
import csv
import sys

runs = collections.defaultdict(list)
confirmed = set()
for index, path in enumerate(sys.argv[1:3]):
    for row in csv.DictReader(open(path)):
        if row['thirst'] != 'on':
            continue
        runs[row['overrides']].append(row)
        if index == 1:
            confirmed.add(row['overrides'])

def score(overrides):
    rows = runs[overrides]
    extinct = sum(int(r['extinctionStep']) >= 0 for r in rows)
    alive = [float(r['balance']) for r in rows if int(r['extinctionStep']) < 0]
    return (extinct, max(alive) if alive else float('inf'),
            max(int(r['maxOverloadSpan']) for r in rows))

best = min((o for o in confirmed if o != 'default'), key=score)
sys.stderr.write('%s %s\n' % (score(best), best))
print(best)
