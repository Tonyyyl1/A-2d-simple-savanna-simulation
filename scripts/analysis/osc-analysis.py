#!/usr/bin/env python3
"""Lag and phase analysis of ThirstDiagnosticTool series (plan section 13, diagnosis D).

Usage: python3 -I scripts/analysis/osc-analysis.py <series.tsv> [more.tsv ...]
Reads the 10-step TSV the tool prints (one row per window and species, plus GRASS rows) and prints:
Gazelle peak intervals, the lag with the best correlation between Gazelle and grass, between
Lion/Cheetah and Gazelle, and the Gazelle per-capita birth/starvation/predation rates against grass.
Pure Python, no third-party packages. Read-only analysis of already produced files.
"""
import csv
import math
import sys


def load(path):
    series = {}
    grass = {}
    with open(path) as handle:
        for row in csv.reader(handle, delimiter='\t'):
            if not row or row[0] == 'step' or row[0].startswith('PROXIMITY'):
                continue
            if row[0] == 'GRASS':
                grass[int(row[1])] = float(row[2])
                continue
            step, species = int(row[0]), row[1]
            series.setdefault(species, {})[step] = {
                'pop': int(row[2]), 'births': int(row[4]), 'starve': int(row[5]),
                'pred': int(row[6]), 'food': float(row[8]), 'zero': float(row[9])}
    return series, grass


def corr(a, b):
    n = min(len(a), len(b))
    if n < 5:
        return float('nan')
    a, b = a[:n], b[:n]
    ma, mb = sum(a) / n, sum(b) / n
    da = [x - ma for x in a]
    db = [x - mb for x in b]
    sa = math.sqrt(sum(x * x for x in da))
    sb = math.sqrt(sum(x * x for x in db))
    if sa == 0 or sb == 0:
        return float('nan')
    return sum(x * y for x, y in zip(da, db)) / (sa * sb)


def lagged(x, y, lag):
    """corr(x(t), y(t+lag)) in samples; positive lag = y follows x."""
    if lag >= 0:
        return corr(x[:len(x) - lag], y[lag:])
    return corr(x[-lag:], y[:len(y) + lag])


def best_lag(x, y, max_lag):
    best = (float('nan'), 0)
    for lag in range(-max_lag, max_lag + 1):
        r = lagged(x, y, lag)
        if not math.isnan(r) and (math.isnan(best[0]) or abs(r) > abs(best[0])):
            best = (r, lag)
    return best


def peaks(values, steps, window=3):
    result = []
    for i in range(window, len(values) - window):
        local = values[i - window:i + window + 1]
        if values[i] == max(local) and values[i] > 1.5 * min(local):
            result.append(steps[i])
    return result


def report(path, window):
    series, grass = load(path)
    steps = sorted(set(series['Gazelle']) & set(grass))
    g = [series['Gazelle'][s]['pop'] for s in steps]
    a = [grass[s] for s in steps]
    print('##', path, 'samples', len(steps), 'last step', steps[-1])
    pk = peaks(g, steps)
    gaps = [b - a_ for a_, b in zip(pk, pk[1:])]
    print('  Gazelle peaks at', pk, 'intervals', gaps,
          'mean %.0f' % (sum(gaps) / len(gaps)) if gaps else '')
    print('  Gazelle min/max/mean pop %d / %d / %.0f; grass min/max %.3f / %.3f' %
          (min(g), max(g), sum(g) / len(g), min(a), max(a)))
    r, lag = best_lag(g, a, 20)
    print('  corr(Gazelle(t), grass(t+lag)) best r=%.2f at lag %+d steps (negative r = grass falls after Gazelle rise)' %
          (r, lag * window))
    r0 = lagged(g, a, 0)
    print('  corr(Gazelle, grass) same time r=%.2f' % r0)
    for predator in ('Lion', 'Cheetah'):
        p = [series[predator][s]['pop'] for s in steps]
        r, lag = best_lag(g, p, 30)
        print('  corr(Gazelle(t), %s(t+lag)) best r=%.2f at lag %+d steps (min %d, max %d)' %
              (predator, r, lag * window, min(p), max(p)))
    for species in ('Zebra', 'Buffalo'):
        z = [series[species][s]['pop'] for s in steps]
        r, lag = best_lag(g, z, 30)
        print('  corr(Gazelle(t), %s(t+lag)) best r=%.2f at lag %+d steps' % (species, r, lag * window))
    births = [series['Gazelle'][s]['births'] / max(1, series['Gazelle'][s]['pop']) / window for s in steps]
    starve = [series['Gazelle'][s]['starve'] / max(1, series['Gazelle'][s]['pop']) / window for s in steps]
    pred = [series['Gazelle'][s]['pred'] / max(1, series['Gazelle'][s]['pop']) / window for s in steps]
    food = [series['Gazelle'][s]['food'] for s in steps]
    n = len(steps)
    print('  Gazelle per-capita rates per step: births %.4f starve %.4f pred %.4f' %
          (sum(births) / n, sum(starve) / n, sum(pred) / n))
    for name, rates in (('births', births), ('starve', starve), ('pred', pred)):
        r, lag = best_lag(a, rates, 20)
        print('  corr(grass(t), Gazelle %s rate(t+lag)) best r=%.2f at lag %+d steps' % (name, r, lag * window))
    r, lag = best_lag(a, food, 20)
    print('  corr(grass(t), Gazelle mean food%%(t+lag)) best r=%.2f at lag %+d steps' % (r, lag * window))
    r, lag = best_lag(food, births, 20)
    print('  corr(Gazelle food%%(t), births rate(t+lag)) best r=%.2f at lag %+d steps' % (r, lag * window))
    for s in pk[:6]:
        i = steps.index(s)
        lo = max(0, i - 5)
        hi = min(n - 1, i + 5)
        print('  around peak %d: grass %.3f (t-50) -> %.3f (peak) -> %.3f (t+50); births/cap %.4f -> %.4f -> %.4f; starve/cap %.4f -> %.4f -> %.4f' %
              (s, a[lo], a[i], a[hi], births[lo], births[i], births[hi], starve[lo], starve[i], starve[hi]))


if __name__ == '__main__' and not (len(sys.argv) > 1 and sys.argv[1] == '--tables'):
    for path in sys.argv[1:]:
        report(path, 10)


def acf_period(values, max_lag):
    """First positive autocorrelation peak after the first zero crossing (samples), or None."""
    n = len(values)
    mean = sum(values) / n
    d = [v - mean for v in values]
    var = sum(x * x for x in d)
    if var == 0:
        return None
    ac = [sum(d[i] * d[i + k] for i in range(n - k)) / var for k in range(max_lag + 1)]
    crossed = False
    best = None
    for k in range(1, max_lag):
        if not crossed and ac[k] < 0:
            crossed = True
        if crossed and ac[k] > ac[k - 1] and ac[k] >= ac[k + 1] and ac[k] > 0.05:
            best = (k, ac[k])
            break
    return best


def binned(rows, key, edges, fields):
    """rows: list of dicts; groups by key into the half-open bins and averages fields."""
    out = []
    for lo, hi in zip(edges, edges[1:]):
        group = [r for r in rows if lo <= r[key] < hi]
        if len(group) < 3:
            out.append((lo, hi, len(group), None))
            continue
        out.append((lo, hi, len(group), [sum(r[f] for r in group) / len(group) for f in fields]))
    return out


def response_tables(paths, window=10):
    gz, cheetah, lion = [], [], []
    for path in paths:
        series, grass = load(path)
        steps = sorted(set(series['Gazelle']) & set(grass))
        for s in steps:
            if s <= 300:
                continue
            gaz = series['Gazelle'][s]
            gp = max(1, gaz['pop'])
            gz.append({'grass': grass[s], 'births': gaz['births'] / gp / window,
                       'starve': gaz['starve'] / gp / window, 'pred': gaz['pred'] / gp / window,
                       'food': gaz['food']})
            for name, bucket in (('Cheetah', cheetah), ('Lion', lion)):
                if s not in series[name]:
                    continue
                row = series[name][s]
                pop = max(1, row['pop'])
                bucket.append({'gazelle': gaz['pop'], 'births': row['births'] / pop / window,
                               'starve': row['starve'] / pop / window, 'food': row['food']})
    print('## Gazelle per-capita rates per step by global grass level (steps > 300, all files)')
    print('   grass bin        n   births  starve  pred    net     meanFood%')
    for lo, hi, n, vals in binned(gz, 'grass', [0, 0.05, 0.10, 0.15, 0.20, 0.30, 0.50],
                                  ['births', 'starve', 'pred', 'food']):
        if vals:
            print('   %.2f-%.2f  %5d   %.4f  %.4f  %.4f  %+.4f  %.1f' %
                  (lo, hi, n, vals[0], vals[1], vals[2], vals[0] - vals[1] - vals[2], vals[3]))
    for name, bucket in (('Cheetah', cheetah), ('Lion', lion)):
        print('## %s per-capita rates per step by Gazelle count (steps > 300)' % name)
        print('   Gazelle bin           n   births  starve  net     meanFood%')
        for lo, hi, n, vals in binned(bucket, 'gazelle', [0, 1500, 3000, 5000, 8000, 20000],
                                      ['births', 'starve', 'food']):
            if vals:
                print('   %5d-%-5d  %5d   %.4f  %.4f  %+.4f  %.1f' %
                      (lo, hi, n, vals[0], vals[1], vals[0] - vals[1], vals[2]))


def periods(paths, window=10):
    print('## autocorrelation period (first positive ACF peak after the first zero crossing; samples after step 300)')
    for path in paths:
        series, grass = load(path)
        steps = [s for s in sorted(series['Gazelle']) if s > 300]
        for name in ('Gazelle', 'Lion', 'Cheetah'):
            values = [series[name][s]['pop'] for s in steps]
            result = acf_period(values, min(80, len(values) // 2))
            logv = [math.log(max(1, v)) for v in values]
            sd = math.sqrt(sum((x - sum(logv) / len(logv)) ** 2 for x in logv) / len(logv))
            print('  %-28s %-8s period %s  sd(ln pop) %.2f (x%.1f)' %
                  (path.split('/')[-1], name,
                   ('%d steps (acf %.2f)' % (result[0] * window, result[1])) if result else 'none', sd,
                   math.exp(2 * sd)))


if __name__ == '__main__' and len(sys.argv) > 2 and sys.argv[1] == '--tables':
    response_tables(sys.argv[2:])
    periods(sys.argv[2:])
