import copy
from contextlib import redirect_stdout
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import quality_gate as gate


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value), encoding="utf-8")


class QualityGate(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.hermit = self.root / "HermiT Results"
        self.oops = self.root / "OOPS Results"
        self.output = self.root / "output"
        self.result = {"schemaVersion": 1, "complete": True, "consistent": True, "toolErrors": [],
                       **{key: [] for key in gate.ENTITY_CHECKS}}
        self.make_reasoner(self.hermit, "HermiT")
        self.oops_report = {"schema_version": 1, "engine": "OOPS", "commit": gate.COMMIT,
                            "complete": True, "status": "clean", "selected_rules": list(gate.RULES),
                            "completed_rules": list(gate.RULES), "warnings": [], "findings": []}
        write(self.oops / "oops.json", self.oops_report)

    def make_reasoner(self, directory, engine, result=None, status="PASS"):
        result = copy.deepcopy(result or self.result)
        coverage, entries = {}, []
        for group, root in (("sources", "SOMA-ALL.owl"), ("exports", "SOMA.owl")):
            roots = [root, "SOMA-HOME.owl"]
            coverage[group] = {"roots": roots, "coveredBy": {r: r for r in roots}}
            for name in roots:
                path = directory / group / name / "report.json"
                write(path, {"metadata": {"reasoner": engine}, "status": status, "result": result})
                entries.append({"input": name, "report": str(path), "status": status})
        write(directory / "coverage.json", coverage)
        write(directory / "summary.json", {"schemaVersion": 1, "complete": True,
              "exitCode": {"PASS": 0, "FINDINGS": 1, "INCONSISTENT": 1, "TOOL_ERROR": 2}[status],
              "entries": entries})

    def run_gate(self, jobs=None):
        with redirect_stdout(io.StringIO()):
            code = gate.run(self.hermit, self.oops, self.output, jobs)
        report = gate.read_json(self.output / "quality-gate.json")
        self.assertEqual(code, report["exit_code"])
        self.assertTrue((self.output / "summary.md").is_file())
        return code, report

    def test_pass_and_ci_summary(self):
        from unittest.mock import patch
        summary = self.root / "ci-summary.md"
        with patch.dict(os.environ, GITHUB_STEP_SUMMARY=str(summary)):
            self.assertEqual(0, self.run_gate()[0])
        self.assertIn("**pass**", summary.read_text())

    def test_each_semantic_category_blocks_without_waivers(self):
        for category in gate.ENTITY_CHECKS:
            with self.subTest(category=category):
                result = copy.deepcopy(self.result)
                result[category] = ["urn:known-broken-entity"]
                self.make_reasoner(self.hermit, "HermiT", result, "FINDINGS")
                code, report = self.run_gate({"consistency": "failure"})
                self.assertEqual(1, code)
                self.assertIn("urn:known-broken-entity", report["checks"][0]["findings"][category])
        result = copy.deepcopy(self.result)
        result["consistent"] = False
        self.make_reasoner(self.hermit, "HermiT", result, "INCONSISTENT")
        self.assertEqual(1, self.run_gate()[0])

    def test_cli_passes_with_only_hermit_and_oops_artifacts(self):
        result = subprocess.run([sys.executable, gate.__file__, "--artifacts", str(self.root),
                                 "--output", str(self.output)], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        report = gate.read_json(self.output / "quality-gate.json")
        self.assertEqual({"HermiT", "OOPS"}, {c["engine"] for c in report["checks"]})
        (self.oops / "oops.json").unlink()
        self.assertEqual(2, self.run_gate()[0])

    def test_technical_errors_are_distinct_and_take_precedence(self):
        result = copy.deepcopy(self.result)
        result.update(complete=False, consistent=None, toolErrors=["Worker timed out"])
        self.make_reasoner(self.hermit, "HermiT", result, "TOOL_ERROR")
        self.assertEqual(2, self.run_gate()[0])
        # Keep one failed root while another produces semantic findings.
        failed_path = self.hermit / "sources/SOMA-ALL.owl/report.json"
        failed_report = gate.read_json(failed_path)
        result = copy.deepcopy(self.result)
        result["unsatisfiableClasses"] = ["urn:broken"]
        self.make_reasoner(self.hermit, "HermiT", result, "FINDINGS")
        write(failed_path, failed_report)
        summary_path = self.hermit / "summary.json"
        summary = gate.read_json(summary_path)
        summary["entries"][0]["status"] = "TOOL_ERROR"
        summary["exitCode"] = 2
        write(summary_path, summary)
        code, report = self.run_gate()
        self.assertEqual(2, code)
        self.assertEqual({"findings", "tool_error", "pass"}, {c["status"] for c in report["checks"]})

    def test_failed_tests_or_upload_remain_technical_despite_semantic_findings(self):
        result = copy.deepcopy(self.result)
        result["unsatisfiableClasses"] = ["urn:broken"]
        self.make_reasoner(self.hermit, "HermiT", result, "FINDINGS")
        code, report = self.run_gate({"consistency": "failure", "HermiT infrastructure": "failure"})
        self.assertEqual(2, code)
        self.assertTrue(any(c["engine"] == "HermiT infrastructure" and c["status"] == "tool_error"
                            for c in report["checks"]))

    def test_missing_invalid_incomplete_and_contradictory_reports_fail_closed(self):
        path = self.hermit / "summary.json"
        original = gate.read_json(path)
        for bad in (None, "", {**original, "entries": []}, {**original, "complete": False},
                    {**original, "exitCode": 1}):
            if bad is None:
                path.unlink()
            elif bad == "":
                path.write_text("")
            else:
                write(path, bad)
            self.assertEqual(2, self.run_gate()[0])
        write(path, original)
        report_path = self.hermit / "sources/SOMA-ALL.owl/report.json"
        report = gate.read_json(report_path)
        del report["result"]["emptyDataProperties"]
        write(report_path, report)
        self.assertEqual(2, self.run_gate()[0])

    def test_coverage_and_duplicate_entries_are_required(self):
        path = self.hermit / "summary.json"
        report = gate.read_json(path)
        report["entries"][1] = report["entries"][0]
        write(path, report)
        self.assertEqual(2, self.run_gate()[0])
        self.make_reasoner(self.hermit, "HermiT")
        write(self.hermit / "coverage.json", {})
        self.assertEqual(2, self.run_gate()[0])

    def test_invalid_result_types_and_contradictory_status_fail_closed(self):
        path = self.hermit / "sources/SOMA-ALL.owl/report.json"
        original = gate.read_json(path)
        for changes in ({"complete": 1}, {"consistent": 1}, {"consistent": None},
                        {"emptyDataProperties": "urn:broken"}, {"toolErrors": [None]},
                        {"consistent": False, "unsatisfiableClasses": ["urn:broken"]},
                        {"unsatisfiableClasses": ["urn:broken"]}):
            with self.subTest(changes=changes):
                document = copy.deepcopy(original)
                document["result"].update(changes)
                write(path, document)
                self.assertEqual(2, self.run_gate()[0])

    def test_skipped_failed_and_cancelled_jobs_cannot_pass(self):
        for jobs in ({"compile": "failure"}, {"consistency": "failure"}, {"pitfalls": "skipped"},
                     {"consistency": "cancelled"}, {"artifact-download": "failure"}):
            self.assertEqual(2, self.run_gate(jobs)[0])

    def finding(self, code):
        return {"code": code, "name": "Example", "resources": ["urn:test"], "importance": "MINOR"}

    def test_oops_policy_and_known_warnings(self):
        for rule in gate.RULES:
            self.oops_report.update(status="findings", findings=[self.finding(f"P{rule:02}")])
            write(self.oops / "oops.json", self.oops_report)
            self.assertEqual(1 if rule in gate.OOPS_ERRORS else 0, self.run_gate()[0])
        baseline_warning = gate.read_json(gate.BASELINE)["warnings"][0]
        self.oops_report["findings"] = [{**self.finding(baseline_warning["code"]), **baseline_warning}]
        write(self.oops / "oops.json", self.oops_report)
        code, report = self.run_gate()
        self.assertEqual(0, code)
        self.assertTrue(report["checks"][-1]["findings"][0]["known_warning"])

    def test_oops_unknown_missing_rules_and_tool_failure(self):
        original = copy.deepcopy(self.oops_report)
        for changes in ({"complete": False, "error": "OOPS timed out"}, {"completed_rules": []},
                        {"warnings": ["Rule not evaluated"]},
                        {"status": "findings", "findings": [self.finding("P99")]},
                        {"status": "findings", "findings": [None]}):
            write(self.oops / "oops.json", {**original, **changes})
            self.assertEqual(2, self.run_gate()[0])

    def test_oops_annotations_apply_the_same_policy_and_escape_messages(self):
        script = Path(gate.__file__).parent / "oops-github.py"
        for rule, expected, annotation in (("P08-A", 0, "warning"), ("P05", 0, "error"),
                                           ("P99", 2, "error")):
            finding = self.finding(rule)
            finding["resources"] = ["urn:test\n::notice::injection%"]
            write(self.oops / "oops.json", {**self.oops_report, "findings": [finding]})
            process = subprocess.run([sys.executable, str(script), str(self.oops / "oops.json")],
                                     capture_output=True, text=True)
            self.assertEqual(expected, process.returncode, process.stderr)
            self.assertTrue(process.stdout.startswith(f"::{annotation}::"))
            self.assertNotIn("\n::notice::", process.stdout)

    def test_cli_failure_writes_artifacts_and_exits_nonzero(self):
        # Executable boundary used by CI, including an entirely failed build.
        script = Path(gate.__file__)
        result = subprocess.run([sys.executable, str(script), "--artifacts", str(self.root / "missing"),
                                 "--output", str(self.output)], capture_output=True, text=True)
        self.assertEqual(2, result.returncode, result.stderr)
        self.assertEqual("tool_error", gate.read_json(self.output / "quality-gate.json")["status"])


    @unittest.skipUnless(os.environ.get("QUALITY_GATE_JAR"), "Set QUALITY_GATE_JAR after Maven package")
    def test_real_ontology_defects_block(self):
        fixtures = Path(gate.__file__).parent / "java/src/test/resources/reasoning"
        for name, status, code in (("consistent", "PASS", 0), ("inconsistent", "INCONSISTENT", 1),
                                   ("unsatisfiable-class", "FINDINGS", 1),
                                   ("empty-object-property", "FINDINGS", 1),
                                   ("empty-data-property", "FINDINGS", 1),
                                   ("missing-import", "TOOL_ERROR", 2)):
            with self.subTest(ontology=name):
                output = self.root / name
                process = subprocess.run(["java", "-cp", os.environ["QUALITY_GATE_JAR"],
                    "main.reasoning.HermitCli", f"--input={fixtures / (name + '.ofn')}",
                    f"--output={output}", "--timeout-seconds=60", "--memory-mb=512"],
                    capture_output=True, text=True, timeout=90)
                self.assertEqual(code, process.returncode, process.stdout + process.stderr)
                document = gate.read_json(output / "input/report.json")
                self.make_reasoner(self.hermit, "HermiT", document["result"], status)
                self.assertEqual(code, self.run_gate()[0])


if __name__ == "__main__":
    unittest.main()
