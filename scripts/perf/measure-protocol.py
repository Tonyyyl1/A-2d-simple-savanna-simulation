#!/usr/bin/env python3
"""Measurement protocol runner (plan author's 2026-11-15 protocol).
usage: proto.py <outdir> <rounds> <variant:cp[:flag,flag]> ...   configs: default 1000 steps, candA 2000 steps
Runs, for each config, `rounds` rounds of every variant in order, one JVM at a time."""
import fcntl, json, os, re, statistics, subprocess, sys, time
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
RESUME = "--resume" in sys.argv
sys.argv = [a for a in sys.argv if a != "--resume"]
CANDA = ("capacity.preyOverload=10,grass.capacityScale=0.4,grazing.fullIntakeFill=0.5,"
         "grazing.refugeFill=0.15,init.lion=0,init.cheetah=0")
CONFIGS = [("default", ["1111", "1000", "off"]),
           ("candA", ["1111", "2000", "off", "--params=" + CANDA])]
if os.environ.get("PROTO_CONFIGS"):
    CONFIGS = [c for c in CONFIGS if c[0] in os.environ["PROTO_CONFIGS"].split(",")]
out = os.path.join(ROOT, sys.argv[1]); rounds = int(sys.argv[2])
variants = []
for spec in sys.argv[3:]:
    # name=classpath[@flag,flag]  (the classpath may itself contain ':')
    vname, rest = spec.split("=", 1)
    cp, _, flagtext = rest.partition("@")
    variants.append((vname, cp, [f for f in flagtext.split(",") if f]))
os.makedirs(out, exist_ok=True)
os.chdir(ROOT)
resfile = os.path.join(out, "results.jsonl")
# one instance per output directory (the 2026-11-15 double launch interleaved two instances)
_lock = open(os.path.join(out, "protocol.lock"), "a+")
try:
    fcntl.flock(_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
except OSError:
    _lock.seek(0)
    print(f"measure-protocol: {out} is already in use by pid {_lock.read().strip() or '?'}", file=sys.stderr)
    sys.exit(2)
_lock.seek(0); _lock.truncate(); _lock.write(str(os.getpid())); _lock.flush()
if os.path.exists(resfile) and not RESUME:
    print(f"measure-protocol: {resfile} already exists; pass --resume to append or use a new directory",
          file=sys.stderr)
    sys.exit(2)

def jvms():
    r = subprocess.run(["pgrep", "-fl", r"(^|/)java .*(savanna\.[A-Z]|EcologySweep)"],
                       capture_output=True, text=True)
    return [l for l in r.stdout.splitlines() if l.strip()]

def load1():
    m = re.search(r"load averages?: ([0-9.]+)", subprocess.run(["uptime"], capture_output=True, text=True).stdout)
    return float(m.group(1)) if m else -1.0

IGNORE_BUSY = os.environ.get("PROTO_IGNORE_BUSY") == "1"

def wait_ready():
    if IGNORE_BUSY:
        return load1()
    deadline = time.time() + 180
    while True:
        busy = jvms()
        if busy:
            time.sleep(10)
            continue  # hard rule: never run next to another simulation JVM
        ld = load1()
        if ld <= 5.0 or time.time() >= deadline:
            return ld
        time.sleep(10)

def num(pattern, text):
    m = re.search(pattern, text, re.M)
    return float(m.group(1)) if m else None

def run_once(cfg, args, vname, cp, flags, rnd, attempt):
    tag = f"{cfg}-{vname}-r{rnd}-a{attempt}"
    ld = wait_ready()
    gc = os.path.join(out, tag + ".gc")
    cmd = ["/usr/bin/time", "-l", "java", "-Djava.awt.headless=true", "-Xmx1g", "-XX:+UseParallelGC",
           "-XX:ParallelGCThreads=1", f"-Xlog:gc:file={gc}"] + flags + ["-cp", cp,
           "savanna.EcologyMatrixRunner"] + args
    with open(os.path.join(out, tag + ".out"), "w") as so, open(os.path.join(out, tag + ".time"), "w") as se:
        t_start = time.strftime('%H:%M:%S')
        rc = subprocess.call(cmd, stdout=so, stderr=se)
        t_end = time.strftime('%H:%M:%S')
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
    sm = re.search(r"stepMedianMs=([0-9.]+)", eco.group(0)) if eco else None
    valid = rc == 0 and ghz >= 3.97 and ratio <= 1.02
    rec = dict(config=cfg, variant=vname, round=rnd, attempt=attempt, rc=rc, real=real, user=user, sys=sysv,
               cpu=cpu, instr=instr, cycles=cyc, ghz=round(ghz, 3), ratio=round(ratio, 3), peak=peak,
               gc_ms=round(gcms, 1), start_load=ld, busy=len(jvms()), stepMedianMs=float(sm.group(1)) if sm else None,
               final=fin, valid=valid, start=t_start, end=t_end, pid=os.getpid())
    with open(resfile, "a") as f:
        f.write(json.dumps(rec) + "\n")
    print(f"{tag} rc={rc} real={real} cpu={cpu:.2f} ghz={ghz:.3f} ratio={ratio:.3f} load={ld} valid={valid}", flush=True)
    return rec

for cfg, args in CONFIGS:
    extra = {v[0]: (0 if IGNORE_BUSY or os.environ.get("PROTO_NO_RETRY") == "1" else 3) for v in variants}
    for rnd in range(1, rounds + 1):
        for vname, cp, flags in variants:
            attempt = 1
            while True:
                rec = run_once(cfg, args, vname, cp, flags, rnd, attempt)
                if rec["valid"] or extra[vname] <= 0:
                    break
                extra[vname] -= 1; attempt += 1
print("PROTO_DONE", flush=True)
