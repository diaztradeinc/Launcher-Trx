"""Fail delivery on Google SDK authorization errors; never print API keys."""
import pathlib
import re
import sys

log = pathlib.Path(sys.argv[1]).read_text(errors="replace")
failed = re.search(r"Google (?:Android Maps|Navigation) SDK[^\n]*Authorization failure", log, re.I)
if failed:
    print("BLOCKED: Google SDK authorization failed for the Nav Lab. Check the existing key's Android app restrictions, enabled SDKs and billing. No verified APK will be published.")
    sys.exit(1)
print("No Google authorization failure found in this log. This does not prove live routing or GPS.")
