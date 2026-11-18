"""Replace pinned trace fingerprints after an intentional ecology rule change.

Usage: python3 -I scripts/migrate-fingerprints.py <check.log> <EcologyRules.VERSION>

Reads the "expected X but was Y" lines that ./check.sh printed for the
V27 PERF step 1-3 and D2 F05 trace fingerprints, swaps X for Y in the two
test files, and stamps the rule version into their comments. Run it only in
the same commit that bumps EcologyRules.VERSION (plan v3.0 section 2.5).
"""
import re
import sys

log, version = sys.argv[1], sys.argv[2]
text = open(log).read()
pairs = re.findall(
    r'^FAIL (?:V27 PERF fixed simulation fingerprint step \d+|'
    r'D2 F05 keeps frozen \d+ step-\d+ fingerprint) - expected ([0-9a-f]{64}) but was ([0-9a-f]{64})',
    text, re.M)
for path in ('src/test/java/savanna/V27PerfTests.java',
             'src/test/java/savanna/A1aR2rD2Tests.java'):
    source = open(path).read()
    replaced = 0
    for old, new in pairs:
        if old in source:
            source = source.replace(old, new)
            replaced += 1
    source = re.sub(r'(Intentional trace migration: )[^\n]*', r'\g<1>' + version, source)
    open(path, 'w').write(source)
    print(path, 'replaced', replaced)
print('pairs', len(pairs))
