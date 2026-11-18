"""Rank the runs of a long (single-run-per-candidate) sweep: survivors first
(smallest balance), then the latest extinction.

Usage: python3 -I scripts/sweeps/pick-long.py <results.csv> [N <grid-out> [comment]]
Without N, prints the overrides of the winner (empty for the defaults).
With N, writes a confirmation grid with the top N candidates.
"""
import csv
import sys

rows = list(csv.DictReader(open(sys.argv[1])))

def score(row):
    step = int(row['extinctionStep'])
    alive = step < 0
    balance = float(row.get('balanceMean') or row['balance']) if alive else float('inf')
    return (0 if alive else 1, balance, -step)

ranked = sorted(rows, key=score)
best = ranked[0]
sys.stderr.write('%s %s %s %s\n' % (best['overrides'], best['extinctionStep'],
                                    best['extinctSpecies'], best['balance']))
if len(sys.argv) > 3:
    with open(sys.argv[3], 'w') as out:
        out.write('# ' + (sys.argv[4] if len(sys.argv) > 4 else 'confirmation grid') + '\n')
        for row in ranked[:int(sys.argv[2])]:
            if row['overrides'] != 'default':
                out.write('candidate ' + row['overrides'] + '\n')
            print(score(row), row['overrides'])
else:
    print('' if best['overrides'] == 'default' else best['overrides'])
