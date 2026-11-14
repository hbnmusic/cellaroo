"""Prints totals and failure messages from Gradle's JUnit XML reports."""
import glob
import xml.etree.ElementTree as ET

total = failed = skipped = 0
failures = []
for path in glob.glob("**/build/test-results/**/*.xml", recursive=True):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    for case in root.iter("testcase"):
        total += 1
        if case.find("skipped") is not None:
            skipped += 1
        for tag in ("failure", "error"):
            node = case.find(tag)
            if node is not None:
                failed += 1
                text = (node.get("message") or "") + "\n" + (node.text or "")
                raw = [l for l in text.strip().splitlines() if l.strip()]
                # The message, then only the stack frames from this project.
                lines = raw[:2] + [l for l in raw[2:] if "app.cellaroo" in l][:6]
                failures.append(f"- {case.get('classname')}.{case.get('name')}\n    " + "\n    ".join(l[:300] for l in lines))
print(f"tests={total} failed={failed} skipped={skipped}")
for f in failures[:25]:
    print(f)
