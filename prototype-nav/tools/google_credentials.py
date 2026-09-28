"""Credential preflight and QA redaction. Never print a Google API key."""
import hashlib
import os
import pathlib
import re
import sys


def fingerprint(key):
    return hashlib.sha256(key.encode()).hexdigest()[:16]


def redact(text):
    text = re.sub(r"AIza[\w-]+", "[REDACTED_GOOGLE_KEY]", text)
    key = os.environ.get("MAPS_API_KEY", "").strip()
    return text.replace(key, "[REDACTED_GOOGLE_KEY]") if key else text


def preflight():
    key = os.environ.get("MAPS_API_KEY", "").strip()
    if not re.fullmatch(r"AIza[\w-]{35}", key):
        print("BLOCKED: MAPS_API_KEY is absent or malformed. Update the repository secret with the Android API key; no key was printed.")
        return 1
    print("Credential fingerprint (SHA-256 prefix): " + fingerprint(key))
    print("Package: com.diaztradeinc.trxnavprototype")
    print("Signing SHA-1: 03:04:AF:48:B6:70:72:BE:31:3F:17:C2:D8:F7:17:6D:6C:78:D3:BC")
    print("Format check only; Google must still authorize this credential and package.")
    return 0


if __name__ == "__main__":
    if len(sys.argv) == 2 and sys.argv[1] == "preflight":
        sys.exit(preflight())
    if len(sys.argv) >= 3 and sys.argv[1] == "redact":
        for root in sys.argv[2:]:
            for path in pathlib.Path(root).rglob("*"):
                if path.is_file() and path.suffix in (".txt", ".xml", ".html", ".log", ".json"):
                    original = path.read_text(errors="replace")
                    cleaned = redact(original)
                    if cleaned != original:
                        path.write_text(cleaned)
    else:
        sys.exit("Usage: google_credentials.py preflight | redact DIRECTORY ...")
