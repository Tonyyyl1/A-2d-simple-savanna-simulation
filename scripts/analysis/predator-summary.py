# Per-candidate summary of a P-2 sweep directory: predator min/mean/femMin/troughs/lnSdRun, balanceNorm, and herbivore minima, per seed.
# usage: python3 -I scripts/analysis/predator-summary.py <sweep-dir> <steps> <comma-separated override keys to show>
import csv,glob,re,sys
d=sys.argv[1]; steps=sys.argv[2]; keys=sys.argv[3].split(',')
def kv(s,sep=','):
    out={}
    for x in s.split(sep):
        if ':' in x:
            k,v=x.split(':',1); out[k]=v
    return out
rows=list(csv.DictReader(open(f'{d}/results.csv')))
by={}
for r in rows:
    f=glob.glob(f"{d}/runs/{r['hash']}-{r['seed']}-{steps}-off.log")
    if not f: continue
    line=[l for l in open(f[0]) if l.startswith('ECO_RESULT')][-1]
    g=lambda k:(re.search(r' '+k+r'=(\S+)',line) or [None,'-'])[1]
    by.setdefault(r['hash'],[]).append((r['seed'],r['overrides'],{k:g(k) for k in ['min','means','femMin','troughs','lnSdRun','balanceNorm','extinctSpecies','final']}))
print('cand | seed | Lion min/mean/femMin/troughs/lnSd | Cheetah min/mean/femMin/lnSd | herb min Z/B/G | balanceNorm | extinct')
for h,runs in by.items():
    ov=dict(x.split('=') for x in runs[0][1].split(',') if '=' in x)
    lab=' '.join(f"{k.split('.')[-1]}={ov[k]}" for k in keys if k in ov) or 'default'
    print('##',lab)
    for seed,_,m in sorted(runs):
        mn=kv(m['min']); me=kv(m['means']); fm=kv(m['femMin']); tr=kv(m['troughs']); sd=kv(m['lnSdRun'])
        print(f"  {seed:>6} | L {mn.get('Lion')}/{me.get('Lion')}/{fm.get('Lion')}/{tr.get('Lion')}/{sd.get('Lion')} | C {mn.get('Cheetah')}/{me.get('Cheetah')}/{fm.get('Cheetah')}/{sd.get('Cheetah')} | H {mn.get('Zebra')}/{mn.get('Buffalo')}/{mn.get('Gazelle')} | {m['balanceNorm']} | {m['extinctSpecies']}")
