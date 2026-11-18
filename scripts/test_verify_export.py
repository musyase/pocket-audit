"""Tests for the standalone report verifier."""

from __future__ import annotations

import contextlib
import hashlib
import io
import json
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

from verify_export import verify


class VerifyExportTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / "report.zip"

    def write(self, *, content: bytes = b"code,status\r\nA,checked\r\n", declared: bytes | None = None, extra: bool = False) -> None:
        original = content if declared is None else declared
        manifest = {
            "schema_version": 1,
            "files": [{
                "name": "report.csv",
                "sha256": hashlib.sha256(original).hexdigest(),
                "bytes": len(original),
            }],
        }
        with ZipFile(self.path, "w") as archive:
            archive.writestr("report.csv", content)
            archive.writestr("manifest.json", json.dumps(manifest))
            if extra:
                archive.writestr("unlisted.txt", "surprise")

    def test_valid_archive(self) -> None:
        self.write()
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(0, verify(str(self.path)))

    def test_changed_report_fails(self) -> None:
        self.write(content=b"changed", declared=b"original")
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(1, verify(str(self.path)))

    def test_unlisted_file_fails(self) -> None:
        self.write(extra=True)
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(1, verify(str(self.path)))


if __name__ == "__main__":
    unittest.main()
