# Per-candidate summary of a P-1 sweep directory (worst case over seeds): span, lnSdRun, whole-run minima, means, troughs.
# usage: python3 -I scripts/analysis/herbivore-summary.py <sweep-dir> <steps> <comma-separated override keys to show>
# e.g.   python3 -I scripts/analysis/herbivore-summary.py build/v33-p1-graze 10000 species.zebra.grazingRate,repro.grassGate
# Candidates are ordered by the worst-seed margin over the minima thresholds Zebra 800, Buffalo 480, Gazelle 1200 (0.40 x target).
import csv,glob,re,sys
d=sys.argv[1]; steps=sys.argv[2]; keys=sys.argv[3].split(',')
def kv(s,sep=';'): return dict(x.split(':') for x in s.split(sep) if ':' in x)
rows=list(csv.DictReader(open(f'{d}/results.csv')))
by={}
for r in rows:
    m=kv(r['means']); sd=kv(r['lnSdRun']); tr=kv(r['troughs'])
    line=[l for l in open(glob.glob(f"{d}/runs/{r['hash']}-{r['seed']}-{steps}-off.log")[0]) if l.startswith('ECO_RESULT')][-1]
    mn=kv(re.search(r' min=(\S+)',line).group(1),',')
    ov=dict(x.split('=') for x in r['overrides'].split(','))
    lab=' '.join(f"{k.split('.')[-1]}={ov[k]}" for k in keys if k in ov)
    g=lambda dd,s: float(dd.get(s,0))
    by.setdefault(r['hash'],{'o':lab,'v':[]})['v'].append(
      (int(r['maxOverloadSpan']),max(g(sd,s) for s in('Zebra','Buffalo','Gazelle')),int(mn['Zebra']),int(mn['Buffalo']),int(mn['Gazelle']),g(m,'Zebra'),g(m,'Buffalo'),g(m,'Gazelle'),int(r['completed']),sum(int(tr.get(s,0)) for s in ('Zebra','Buffalo','Gazelle'))))
for h,dd in sorted(by.items(),key=lambda kv:-min(x[2]/800+x[3]/480+x[4]/1200 for x in kv[1]['v'])):
    v=dd['v']
    print(f"{dd['o'][:70]:70s} done>={min(x[8] for x in v)} span<={max(x[0] for x in v):4d} sd<={max(x[1] for x in v):.2f} tr={sum(x[9] for x in v)} minZ>={min(x[2] for x in v)} B>={min(x[3] for x in v)} G>={min(x[4] for x in v)} | Z {min(x[5] for x in v):.0f}-{max(x[5] for x in v):.0f} B {min(x[6] for x in v):.0f}-{max(x[6] for x in v):.0f} G {min(x[7] for x in v):.0f}-{max(x[7] for x in v):.0f}")
