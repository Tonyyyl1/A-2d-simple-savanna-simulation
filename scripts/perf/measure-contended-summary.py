#!/usr/bin/env python3
"""Judge a measure-contended.py run (plan author's temporary same-condition paired criterion).
usage: measure-contended-summary.py <results.jsonl> [baseVariant=base] [calibVariant=s1off] [targetVariant=s2off]
Per config: valid slot = rc 0, scan count 3 before and after, effective clock within +-3% of the config
median over all rc-0 cond_ok runs; per variant needs 5 valid runs and cycles range <= 6%.
Primary ratio = base cycles median / variant cycles median; CPU-time ratio reported next to it (a gap
above 2% has to be explained). Calibration: base / s1off against the clean values (default 1.355,
candidate A 1.418), reported only."""
import json, statistics, sys
rows = [json.loads(l) for l in open(sys.argv[1]) if l.strip()]
base = sys.argv[2] if len(sys.argv) > 2 else "base"
calib = sys.argv[3] if len(sys.argv) > 3 else "s1off"
target = sys.argv[4] if len(sys.argv) > 4 else "s2off"
CLEAN = {"default": 1.355, "candA": 1.418}
med = statistics.median
for cfg in dict.fromkeys(r["config"] for r in rows):
    rs = [r for r in rows if r["config"] == cfg and r["rc"] == 0 and r["cond_ok"] and r["cycles"]]
    if not rs:
        print(f"== {cfg}: no usable runs"); continue
    mghz = med(r["ghz"] for r in rs)
    for r in rs:
        r["valid"] = abs(r["ghz"] / mghz - 1) <= 0.03
    print(f"== {cfg}: usable {len(rs)} runs, median effective clock {mghz:.3f} GHz, "
          f"{sum(not r['valid'] for r in rs)} outside +-3%")
    stats = {}
    for v in dict.fromkeys(r["variant"] for r in rows if r["config"] == cfg):
        allv = [r for r in rows if r["config"] == cfg and r["variant"] == v]
        good = [r for r in rs if r["variant"] == v and r["valid"]]
        # keep the last valid attempt of each round
        byround = {}
        for r in good:
            byround[r["round"]] = r
        good = [byround[k] for k in sorted(byround)]
        if not good:
            print(f"  {v:6s} no valid runs of {len(allv)} started"); continue
        cyc = [r["cycles"] for r in good]; cpu = [r["cpu"] for r in good]
        rng = (max(cyc) - min(cyc)) / med(cyc) * 100
        stats[v] = dict(cyc=med(cyc), cpu=med(cpu), n=len(good), rng=rng)
        flag = "" if len(good) >= 5 and rng <= 6 else "  <-- NOT VALID (needs 5 runs, range <= 6%)"
        print(f"  {v:6s} n={len(good)}/5  cycles med {med(cyc)/1e9:8.2f}G range {rng:4.1f}%  "
              f"cpu med {med(cpu):7.2f}s  ghz {med(r['ghz'] for r in good):.3f}  "
              f"gc {med(r['gc_ms'] for r in good)/1000:.1f}s  rounds {[r['round'] for r in good]}{flag}")
    if base in stats:
        for v, s in stats.items():
            if v == base: continue
            rc_ = stats[base]["cyc"] / s["cyc"]; rt = stats[base]["cpu"] / s["cpu"]
            gap = abs(rc_ / rt - 1) * 100
            note = "  (cycles vs cpu differ by >2%: explain)" if gap > 2 else ""
            print(f"  ratio {base}/{v}: cycles {rc_:.3f}  cpu {rt:.3f}{note}")
        if calib in stats:
            c = stats[base]["cyc"] / stats[calib]["cyc"]
            print(f"  calibration {base}/{calib}: contended cycles {c:.3f} vs clean {CLEAN.get(cfg, float('nan'))} "
                  f"(diff {c - CLEAN.get(cfg, float('nan')):+.3f}, report only)")
