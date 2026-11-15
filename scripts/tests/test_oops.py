import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import oops


class ProcessContract(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.input = self.root / "test.owl"
        self.input.write_text("input", encoding="utf-8")

    def run_case(self, side_effect):
        with patch.object(oops, "installation_classpath", return_value="local"), \
             patch.object(oops.subprocess, "run", side_effect=side_effect):
            code = oops.run_check(self.input, self.root / "report", rules=(8,))
        return code, json.loads((self.root / "report/oops.json").read_text())

    def test_timeout_and_process_failure(self):
        for effect in (subprocess.TimeoutExpired("java", 1),
                       lambda *a, **kw: subprocess.CompletedProcess(a, 7)):
            code, report = self.run_case(effect)
            self.assertEqual(2, code)
            self.assertFalse(report["complete"])
            self.assertEqual("tool_error", report["status"])

    def test_missing_invalid_incomplete_report(self):
        for data in (None, "", "{}", '{"completed_rules":[],"findings":[]}',
                     '{"completed_rules":[8],"findings":[null],"warnings":[]}'):
            def worker(command, **kwargs):
                if data is not None:
                    Path(command[-2]).write_text(data)
                return subprocess.CompletedProcess(command, 0)
            self.assertEqual(2, self.run_case(worker)[0])

    def test_findings_and_precondition_warning(self):
        for warnings, expected in (([], 0), (["not evaluated"], 2)):
            def worker(command, **kwargs):
                Path(command[-2]).write_text(json.dumps({"completed_rules": [8],
                    "findings": [{"code": "P08", "name": "Missing annotations", "description": "Example",
                                  "importance": "MINOR", "resources": ["urn:test:Entity"]}], "warnings": warnings}))
                return subprocess.CompletedProcess(command, 0)
            code, report = self.run_case(worker)
            self.assertEqual(expected, code)
            self.assertEqual(bool(expected == 0), report["complete"])

    def test_input_change(self):
        def worker(command, **kwargs):
            self.input.write_text("changed")
            Path(command[-2]).write_text(json.dumps({"completed_rules": [8], "findings": [], "warnings": []}))
            return subprocess.CompletedProcess(command, 0)
        self.assertEqual(2, self.run_case(worker)[0])

    def test_installation_inventory(self):
        adapter = self.root / "adapter"
        adapter.mkdir()
        worker = adapter / "OopsWorker.class"
        worker.write_bytes(b"class")
        marker = {"commit": oops.COMMIT, "dependency_overrides": oops.DEPENDENCY_OVERRIDES,
                  "source_patches": oops.SOURCE_PATCHES,
                  "files": {"adapter/OopsWorker.class": oops.digest(worker)}}
        (self.root / "installation.json").write_text(json.dumps(marker))
        oops.installation_classpath(self.root)
        worker.write_bytes(b"modified")
        with self.assertRaises(ValueError):
            oops.installation_classpath(self.root)
        worker.write_bytes(b"class")
        (adapter / "Extra.class").write_bytes(b"extra")
        with self.assertRaises(ValueError):
            oops.installation_classpath(self.root)


@unittest.skipUnless(os.environ.get("OOPS_INTEGRATION") == "1", "Set OOPS_INTEGRATION=1 after installation")
class LocalIntegration(unittest.TestCase):
    def test_positive_negative_and_all_rules_offline(self):
        fixtures = Path(__file__).parent / "fixtures/oops"
        # Make both HTTP and HTTPS public services unreachable to the worker.
        proxies = "-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=9 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=9 -Dhttp.nonProxyHosts="
        with tempfile.TemporaryDirectory() as temp, patch.dict(os.environ, JAVA_TOOL_OPTIONS=proxies):
            root = Path(temp)
            for name, status in (("annotated.owl", "clean"), ("unannotated.owl", "findings")):
                self.assertEqual(0, oops.run_check(fixtures / name, root / name, rules=(8,)))
                report = json.loads((root / name / "oops.json").read_text())
                self.assertEqual(status, report["status"])
                self.assertEqual(0, oops.run_check(fixtures / name, root / (name + "-repeat"), rules=(8,)))
                repeat = json.loads((root / (name + "-repeat") / "oops.json").read_text())
                self.assertEqual(report, repeat)
            self.assertEqual(0, oops.run_check(fixtures / "annotated.owl", root / "all"))
            for name, expected in (("hierarchy.owl", "clean"), ("cycle.owl", "findings")):
                self.assertEqual(0, oops.run_check(fixtures / name, root / name, rules=(6,)))
                checked = json.loads((root / name / "oops.json").read_text())
                self.assertEqual(expected, checked["status"])
                if name == "cycle.owl":
                    self.assertEqual({"P06"}, {f["code"] for f in checked["findings"]})
            self.assertEqual(0, oops.run_check(fixtures / "closure/root.owl", root / "closure", rules=(8,)))
            closure = json.loads((root / "closure/oops.json").read_text())
            self.assertTrue(any("urn:test:ImportedClass" in f["resources"] for f in closure["findings"]))
            pair_reports = []
            for index in range(2):
                target = root / f"pairs-{index}"
                self.assertEqual(0, oops.run_check(fixtures / "property-pairs.owl", target, rules=(5, 12, 27)))
                pair_reports.append(json.loads((target / "oops.json").read_text()))
            self.assertEqual(pair_reports[0], pair_reports[1])
            self.assertEqual({"P05", "P12", "P27"}, {f["code"] for f in pair_reports[0]["findings"]})
            for finding in pair_reports[0]["findings"]:
                self.assertFalse(any("oops.linkeddata.es/data/" in r for r in finding["resources"]))
            for name in ("invalid.owl", "missing-import.owl"):
                self.assertEqual(2, oops.run_check(fixtures / name, root / name))
            self.assertEqual(2, oops.run_check(fixtures / "annotated.owl", root / "timeout", timeout=0.001))
            self.assertEqual(2, oops.run_check(fixtures / "annotated.owl", root / "process", java="missing-java-binary"))


if __name__ == "__main__":
    unittest.main()
