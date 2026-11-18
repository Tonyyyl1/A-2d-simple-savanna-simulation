#!/usr/bin/env python3
import json, statistics, sys
rows = [json.loads(l) for l in open(sys.argv[1])]
base = sys.argv[2] if len(sys.argv) > 2 else "base"
def med(x): return statistics.median(x) if x else float("nan")
res = {}
for cfg in dict.fromkeys(r["config"] for r in rows):
    for v in dict.fromkeys(r["variant"] for r in rows):
        rs = [r for r in rows if r["config"] == cfg and r["variant"] == v]
        va = [r for r in rs if r["valid"]]
        if not rs: continue
        cpu = [r["cpu"] for r in va]
        spread = (max(cpu) - min(cpu)) / min(cpu) * 100 if cpu else float("nan")
        res[(cfg, v)] = dict(n=len(va), invalid=len(rs) - len(va), cpu=med(cpu), cpu_min=min(cpu) if cpu else 0,
            cpu_max=max(cpu) if cpu else 0, spread=spread, real=med([r["real"] for r in va]),
            instr=med([r["instr"] for r in va]) / 1e9, cyc=med([r["cycles"] for r in va]) / 1e9,
            gc=med([r["gc_ms"] for r in va]) / 1000, step=med([r["stepMedianMs"] for r in va if r["stepMedianMs"]]),
            peak=med([r["peak"] for r in va]) / 1e6, ghz=med([r["ghz"] for r in va]),
            load=med([r["start_load"] for r in va]), finals=sorted(set(r["final"] for r in va)))
for (cfg, v), s in res.items():
    flag = " NOT-CLEAN" if s["n"] < 5 or s["spread"] > 6 else ""
    print(f"{cfg:8s} {v:8s} n={s['n']} inv={s['invalid']} cpu med={s['cpu']:.2f} [{s['cpu_min']:.2f}..{s['cpu_max']:.2f}] spread={s['spread']:.1f}%{flag} "
          f"real={s['real']:.2f} instr={s['instr']:.1f}G cyc={s['cyc']:.1f}G gc={s['gc']:.2f}s step={s['step']:.1f}ms peak={s['peak']:.0f}MB ghz={s['ghz']:.3f} load={s['load']:.2f}")
for cfg in dict.fromkeys(k[0] for k in res):
    b = res.get((cfg, base))
    for (c, v), s in res.items():
        if c != cfg or v == base or not b: continue
        print(f"{cfg:8s} {base}/{v}: cpu x{b['cpu']/s['cpu']:.3f}  instr x{b['instr']/s['instr']:.3f}  real x{b['real']/s['real']:.3f}  stepMedian x{b['step']/s['step']:.3f}  gc {b['gc']:.1f}s->{s['gc']:.1f}s")
    print(cfg, "final populations identical across variants:", len(set(f for (c, v), s in res.items() if c == cfg for f in s["finals"])) == 1)
