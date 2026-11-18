#!/usr/bin/env python3
"""usage: gc-summary.py <t1 dir>   (gc.log from -Xlog:gc, time.txt from /usr/bin/time -l, out.log)
Reports OOM, real, total GC pause and share, GC share over the last quarter of the run, Full GC count,
maximum live size after a Full GC, maximum RSS and the final populations."""
import re, sys
d = sys.argv[1]
t = open(f"{d}/time.txt").read()
m = re.search(r"^\s*([0-9.]+) real\s+([0-9.]+) user\s+([0-9.]+) sys", t, re.M)
real, user, sysv = (float(m.group(i)) for i in (1, 2, 3)) if m else (0.0, 0.0, 0.0)
rss = re.search(r"^\s*(\d+)\s+maximum resident set size", t, re.M)
peak = re.search(r"^\s*(\d+)\s+peak memory footprint", t, re.M)
out = open(f"{d}/out.log").read()
oom = "OutOfMemoryError" in t or "OutOfMemoryError" in out
pauses = []          # (uptime seconds, pause seconds)
fulls = []           # (uptime, after MB, pause s)
last = 0.0
for l in open(f"{d}/gc.log"):
    g = re.match(r"\[([0-9.]+)s\]\[info\]\[gc\] GC\(\d+\) (Pause \S+(?: \([^)]*\))?(?: \([^)]*\))?) (\d+)M->(\d+)M\(\d+M\) ([0-9.]+)ms", l)
    if not g:
        continue
    up, kind, before, after, ms = float(g.group(1)), g.group(2), int(g.group(3)), int(g.group(4)), float(g.group(5))
    pauses.append((up, ms / 1000)); last = max(last, up)
    if kind.startswith("Pause Full"):
        fulls.append((up, after, ms / 1000))
total = sum(p for _, p in pauses)
q = last * 0.75
lq = sum(p for u, p in pauses if u >= q)
lq_share = lq / (last - q) if last > q else 0.0
eco = re.search(r"^ECO_RESULT .*", out, re.M)
fin = re.search(r" final=(\S+)", eco.group(0)).group(1) if eco else "-"
sm = re.search(r"stepMedianMs=([0-9.]+)", eco.group(0)).group(1) if eco else "-"
print(f"T1_SUMMARY dir={d} oom={oom} real={real:.2f} cpu={user+sysv:.2f} pauses={len(pauses)} gc_total_s={total:.2f} "
      f"gc_share={100*total/real if real else 0:.2f}% last_quarter_share={100*lq_share:.2f}% full_gc={len(fulls)} "
      f"max_live_after_full_MB={max((a for _, a, _ in fulls), default=0)} full_gc_pause_s={sum(p for *_, p in fulls):.2f} "
      f"rss_GB={int(rss.group(1))/1e9 if rss else 0:.3f} peak_footprint_GB={int(peak.group(1))/1e9 if peak else 0:.3f} "
      f"stepMedianMs={sm} final={fin}")
if fulls:
    print("FULL_GCS " + " ".join(f"@{u:.0f}s:{a}M" for u, a, _ in fulls[:30]))
