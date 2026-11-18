#!/usr/bin/env python3
"""Same-condition paired measurement next to a three-JVM scan (plan author's temporary criterion,
relayed 2026-11-15; only valid while the user's permission for one -Xmx1g verification JVM next to a
3-JVM sweep stands).

usage: measure-contended.py <outdir> <rounds> <config,config,...> name=classpath[@flag,flag] ...
Rules (plan author):
 * a slot (config, round, variant) counts only if exactly 3 other EcologyMatrixRunner JVMs run both
   before the JVM starts and after it exits (sampled every 5 s in between and recorded, not used);
 * variants alternate round by round in the given order; one JVM of ours at a time,
   -Xmx1g -XX:+UseParallelGC -XX:ParallelGCThreads=1;
 * up to 2 re-runs per slot when the scan count is not 3; if no scan JVM shows up for 90 s the scan has
   ended: the script stops and does NOT continue in "no scan" conditions;
 * results in <outdir>/results.jsonl, judged by measure-contended-summary.py."""
import json, os, re, subprocess, sys, time
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
CANDA = ("capacity.preyOverload=10,grass.capacityScale=0.4,grazing.fullIntakeFill=0.5,"
         "grazing.refugeFill=0.15,init.lion=0,init.cheetah=0")
ALL = {"default": ["1111", "1000", "off"], "candA": ["1111", "2000", "off", "--params=" + CANDA]}
out = os.path.join(ROOT, sys.argv[1]); rounds = int(sys.argv[2])
configs = [(c, ALL[c]) for c in sys.argv[3].split(",")]
variants = []
for spec in sys.argv[4:]:
    vname, rest = spec.split("=", 1)
    cp, _, flagtext = rest.partition("@")
    variants.append((vname, cp, [f for f in flagtext.split(",") if f]))
mycps = {v[1] for v in variants}
os.makedirs(out, exist_ok=True)
os.chdir(ROOT)
resfile = os.path.join(out, "results.jsonl")

def ps_lines():
    r = subprocess.run(["ps", "-axo", "pid=,command="], capture_output=True, text=True)
    return [l.strip() for l in r.stdout.splitlines() if l.strip()]

def scan_count():
    """number of other EcologyMatrixRunner JVMs (real java processes, not ours, not the time wrapper)"""
    n = 0
    for l in ps_lines():
        pid, _, cmd = l.partition(" ")
        cmd = cmd.strip()
        if "savanna.EcologyMatrixRunner" not in cmd or not cmd.split(" ")[0].endswith("/java"):
            continue
        m = re.search(r"-cp (\S+)", cmd)
        if m and m.group(1) in mycps:
            continue
        n += 1
    return n

def other_jvms():
    n = 0
    for l in ps_lines():
        cmd = l.partition(" ")[2].strip()
        if cmd.split(" ")[0].endswith("java") and "savanna." in cmd and "EcologyMatrixRunner" not in cmd:
            m = re.search(r"-cp (\S+)", cmd)
            if not (m and m.group(1) in mycps):
                n += 1
    return n

def wait_three():
    """wait until exactly 3 scan JVMs run; give up (return None) when none shows up for 90 s"""
    zero_since = None; t0 = time.time()
    while True:
        c = scan_count()
        if c == 3:
            time.sleep(2)
            if scan_count() == 3:
                return 3
            continue
        if c == 0:
            zero_since = zero_since or time.time()
            if time.time() - zero_since >= 90:
                return None
        else:
            zero_since = None
        if time.time() - t0 > 900:
            return -1
        time.sleep(5)

def num(pattern, text):
    m = re.search(pattern, text, re.M)
    return float(m.group(1)) if m else None

def run_once(cfg, args, vname, cp, flags, rnd, attempt):
    tag = f"{cfg}-{vname}-r{rnd}-a{attempt}"
    pre = wait_three()
    if pre is None:
        print("SCAN_ENDED: no scan JVM for 90 s, stopping (no 'no scan' substitutes)", flush=True)
        sys.exit(3)
    if pre == -1:
        print("SCAN_UNSTABLE: scan count did not settle at 3 for 15 min, stopping", flush=True)
        sys.exit(4)
    gc = os.path.join(out, tag + ".gc")
    cmd = ["/usr/bin/time", "-l", "java", "-Djava.awt.headless=true", "-Xmx1g", "-XX:+UseParallelGC",
           "-XX:ParallelGCThreads=1", f"-Xlog:gc:file={gc}"] + flags + ["-cp", cp,
           "savanna.EcologyMatrixRunner"] + args
    samples = [pre]
    with open(os.path.join(out, tag + ".out"), "w") as so, open(os.path.join(out, tag + ".time"), "w") as se:
        p = subprocess.Popen(cmd, stdout=so, stderr=se)
        t0 = time.time()
        while p.poll() is None:
            time.sleep(5)
            if time.time() - t0 > 2400:
                p.kill()
            if int(time.time() - t0) % 15 < 5:
                samples.append(scan_count())
        rc = p.returncode
    post = scan_count(); oth = other_jvms()
    t = open(os.path.join(out, tag + ".time")).read()
    o = open(os.path.join(out, tag + ".out")).read()
    m = re.search(r"^\s*([0-9.]+) real\s+([0-9.]+) user\s+([0-9.]+) sys", t, re.M)
    real, user, sysv = (float(m.group(i)) for i in (1, 2, 3)) if m else (0, 0, 0)
    instr = num(r"^\s*(\d+)\s+instructions retired", t); cyc = num(r"^\s*(\d+)\s+cycles elapsed", t)
    peak = num(r"^\s*(\d+)\s+peak memory footprint", t)
    cpu = user + sysv
    ghz = cyc / 1e9 / cpu if cyc and cpu else 0.0
    ratio = real / cpu if cpu else 9.9
    gcms = 0.0
    try:
        for l in open(gc):
            g = re.search(r"Pause .*? ([0-9.]+)ms", l)
            if g: gcms += float(g.group(1))
    except OSError:
        pass
    eco = re.search(r"^ECO_RESULT .*", o, re.M)
    fin = re.search(r" final=(\S+)", eco.group(0)).group(1) if eco else ""
    cond_ok = (pre == 3 and post == 3)
    rec = dict(config=cfg, variant=vname, round=rnd, attempt=attempt, rc=rc, real=real, user=user, sys=sysv,
               cpu=cpu, instr=instr, cycles=cyc, ghz=round(ghz, 3), ratio=round(ratio, 3), peak=peak,
               gc_ms=round(gcms, 1), scan_before=pre, scan_after=post, scan_min=min(samples + [post]),
               scan_max=max(samples + [post]), other_jvms=oth, final=fin, cond_ok=cond_ok,
               t_end=time.strftime("%H:%M:%S"))
    with open(resfile, "a") as f:
        f.write(json.dumps(rec) + "\n")
    print(f"{tag} rc={rc} real={real} cpu={cpu:.2f} ghz={ghz:.3f} ratio={ratio:.3f} scan={pre}/{post} "
          f"min={rec['scan_min']} max={rec['scan_max']} other={oth} cond_ok={cond_ok} @{rec['t_end']}", flush=True)
    return rec

for cfg, args in configs:
    for rnd in range(1, rounds + 1):
        for vname, cp, flags in variants:
            attempt = 1
            while True:
                rec = run_once(cfg, args, vname, cp, flags, rnd, attempt)
                if (rec["cond_ok"] and rec["rc"] == 0) or attempt >= 3:
                    break
                attempt += 1
print("CONTENDED_DONE", flush=True)
