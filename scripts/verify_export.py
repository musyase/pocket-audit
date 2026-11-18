"""Check the internal consistency of a Pocket Audit ZIP export.

Checksums do not authenticate the person or device that created the archive.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import PurePosixPath
from zipfile import BadZipFile, ZipFile


def verify(path: str) -> int:
    try:
        with ZipFile(path) as archive:
            names = archive.namelist()
            if len(names) != len(set(names)) or "manifest.json" not in names:
                raise ValueError("Archive has duplicate names or no manifest.json")
            manifest = json.loads(archive.read("manifest.json"))
            if manifest.get("schema_version") != 1 or not isinstance(manifest.get("files"), list):
                raise ValueError("Unsupported manifest format")
            expected = {"manifest.json"}
            for entry in manifest["files"]:
                name = entry["name"]
                parsed = PurePosixPath(name)
                if not name or parsed.is_absolute() or ".." in parsed.parts or "\\" in name:
                    raise ValueError(f"Unsafe archive path: {name}")
                if name in expected:
                    raise ValueError(f"Repeated manifest entry: {name}")
                expected.add(name)
                content = archive.read(name)
                actual = hashlib.sha256(content).hexdigest()
                if actual != entry["sha256"] or len(content) != entry["bytes"]:
                    raise ValueError(f"Checksum or size mismatch: {name}")
            if set(names) != expected or "report.csv" not in expected:
                raise ValueError("Archive entries do not match the manifest")
            print(f"Verified {len(expected) - 1} files. This checks integrity, not authorship.")
            return 0
    except (OSError, BadZipFile, KeyError, TypeError, json.JSONDecodeError, ValueError) as error:
        print(f"Verification failed: {error}")
        return 1


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify checksums in a Pocket Audit ZIP export")
    parser.add_argument("archive")
    return verify(parser.parse_args().archive)


if __name__ == "__main__":
    raise SystemExit(main())
