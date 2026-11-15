#!/usr/bin/env python3
"""Combine reasoner/OOPS artifacts. Exit 0: pass, 1: findings, 2: tool error."""
import argparse
from collections import Counter
import json
import os
from pathlib import Path
import re
import sys

from oops_common import COMMIT, RULES

# Explicit project policy, independent of upstream importance labels. Structural
# contradictions are errors; modeling/documentation heuristics need human review.
OOPS_ERRORS = {5, 6, 19, 25, 26, 27, 28, 29}
OOPS_WARNINGS = set(RULES) - OOPS_ERRORS
ENTITY_CHECKS = ("unsatisfiableClasses", "emptyObjectProperties", "emptyDataProperties", "qualityFindings")
BASELINE = Path(__file__).parent / "oops/warning-baseline.json"
EXIT_CODES = {"pass": 0, "findings": 1, "tool_error": 2}


def read_json(path):
    with Path(path).open(encoding="utf-8") as stream:
        return json.load(stream)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def strings(value):
    return isinstance(value, list) and all(isinstance(v, str) and v.strip() for v in value)


def unique_file(directory, name):
    paths = list(Path(directory).rglob(name))
    require(len(paths) == 1, f"Expected one {name} in {directory}, found {len(paths)}")
    return paths[0]


def reasoner_status(result):
    require(result["schemaVersion"] == 1 and type(result["complete"]) is bool,
            "Invalid reasoner protocol")
    require(result["consistent"] is None or type(result["consistent"]) is bool,
            "Invalid consistency result")
    require(all(strings(result[k]) for k in (*ENTITY_CHECKS, "toolErrors")), "Invalid finding lists")
    if not result["complete"] or result["consistent"] is None or result["toolErrors"]:
        return "TOOL_ERROR", "tool_error"
    if result["consistent"] is False:
        require(not any(result[k] for k in ENTITY_CHECKS), "Entity findings on inconsistent input")
        return "INCONSISTENT", "findings"
    if any(result[k] for k in ENTITY_CHECKS):
        return "FINDINGS", "findings"
    return "PASS", "pass"


def reasoner_results(directory, engine):
    summary_path = unique_file(directory, "summary.json")
    summary = read_json(summary_path)
    require(summary["schemaVersion"] == 1 and summary["complete"] is True,
            "Incomplete reasoner suite")
    coverage = read_json(summary_path.parent / "coverage.json")
    require(set(coverage) == {"sources", "exports"}, "Missing source/export coverage")
    expected = {}
    for group, plan in coverage.items():
        roots, assignments = plan["roots"], plan["coveredBy"]
        require(strings(roots) and roots and len(roots) == len(set(roots)), "Invalid roots")
        require(all(Path(r).name == r and "/" not in r and "\\" not in r and r.endswith(".owl")
                    for r in roots), "Unsafe root filename")
        require(isinstance(assignments, dict) and assignments
                and set(assignments.values()) == set(roots)
                and all(assignments.get(r) == r for r in roots), "Invalid module coverage")
        preferred = {"SOMA-ALL.owl" if group == "sources" else "SOMA.owl", "SOMA-HOME.owl"}
        require(preferred <= set(roots), "Missing public roots")
        for root in roots:
            expected[group, root] = summary_path.parent / group / root / "report.json"
    entries = summary["entries"]
    require(isinstance(entries, list) and len(entries) == len(expected), "Missing suite entries")
    results = []
    seen = set()
    for entry in entries:
        # Reports may have been produced on Windows or Linux and downloaded elsewhere.
        parts = entry["report"].replace("\\", "/").split("/")
        key = tuple(parts[-3:-1])
        require(key in expected and key not in seen, "Unexpected or duplicate suite entry")
        seen.add(key)
        document = read_json(expected[key])
        result = document["result"]
        require(document["metadata"]["reasoner"] == engine, "Wrong reasoner identity")
        native, status = reasoner_status(result)
        require(document["status"] == entry["status"] == native, "Contradictory reasoner status")
        results.append({"engine": engine, "input": "/".join(key), "status": status,
                        "consistent": result["consistent"], "errors": result["toolErrors"],
                        "findings": {k: result[k] for k in ENTITY_CHECKS}})
    exit_code = max(EXIT_CODES[r["status"]] for r in results)
    require(summary["exitCode"] == exit_code, "Contradictory suite exit code")
    return results


def warning_identity(finding):
    return finding["code"], tuple(sorted(finding["resources"]))


def oops_severity(code):
    match = re.fullmatch(r"P(\d{2})(?:-[A-Z])?", code)
    require(match is not None and int(match[1]) in RULES, "Unknown OOPS rule")
    return "error" if int(match[1]) in OOPS_ERRORS else "warning"


def oops_results(directory, baseline):
    report = read_json(unique_file(directory, "oops.json"))
    require(report["schema_version"] == 1 and report["engine"] == "OOPS" and report["commit"] == COMMIT,
            "Unexpected OOPS identity")
    require(report["complete"] is True and report["status"] in ("clean", "findings"),
            report.get("error", "Incomplete OOPS execution"))
    require(report["selected_rules"] == report["completed_rules"] == list(RULES)
            and report["warnings"] == [], "Incomplete OOPS rules/preconditions")
    require(isinstance(report["findings"], list), "Invalid OOPS findings")
    require((report["status"] == "findings") == bool(report["findings"]), "Contradictory OOPS status")
    known = read_json(baseline)
    require(known["schema_version"] == 1 and known["commit"] == COMMIT, "Invalid warning baseline")
    identities = {warning_identity(f) for f in known["warnings"]}
    findings = []
    for finding in report["findings"]:
        require(isinstance(finding, dict) and strings(finding.get("resources"))
                and isinstance(finding.get("name"), str), "Invalid OOPS finding")
        severity = oops_severity(finding["code"])
        findings.append({**finding, "severity": severity,
                         "known_warning": severity == "warning" and warning_identity(finding) in identities})
    return [{"engine": "OOPS", "input": "exports/SOMA-HOME.owl (import closure)",
             "status": "findings" if any(f["severity"] == "error" for f in findings) else "pass",
             "errors": [], "findings": findings}]


def markdown(report):
    lines = ["# Ontology quality gate", "", f"Result: **{report['status']}** (exit {report['exit_code']}).",
             "", "Consistency, named-class coherence, empty ObjectProperties and empty DataProperties are mandatory.",
             "No reasoner finding is waived. OOPS errors block; warnings remain visible.", "",
             "| Engine | Input | Result | Details |", "| --- | --- | --- | --- |"]
    for result in report["checks"]:
        if result["engine"] == "OOPS" and isinstance(result["findings"], list):
            counts = Counter(f["severity"] for f in result["findings"])
            known = sum(f["known_warning"] for f in result["findings"])
            detail = f"{counts['error']} errors; {counts['warning']} warnings ({known} known)"
        else:
            detail = "; ".join(f"{k}: {len(v)}" for k, v in result["findings"].items())
            if result.get("consistent") is False:
                detail = "Globally inconsistent; entity checks not applicable"
        cells = (result["engine"], result["input"], result["status"], detail)
        lines.append("| " + " | ".join(str(c).replace("|", "&#124;").replace("\n", " ") for c in cells) + " |")
    lines += ["", "## Technical failures", ""]
    errors = [f"{r['engine']} / {r['input']}: {e}" for r in report["checks"] for e in r["errors"]]
    # JSON in a code block keeps arbitrary diagnostic strings out of Markdown/HTML.
    lines += ["    " + json.dumps(error, ensure_ascii=True) for error in errors] or ["None."]
    lines += ["", "## OOPS rule findings", ""]
    for result in report["checks"]:
        if result["engine"] == "OOPS" and isinstance(result["findings"], list):
            counts = Counter((f["code"], f["severity"], f["known_warning"]) for f in result["findings"])
            lines += [f"- {code}: {count} {severity} findings ({'known' if known else 'new/unbaselined'})."
                      for (code, severity, known), count in sorted(counts.items())]
    lines += ["", "Full entity IRIs, OOPS resources and diagnostics are in quality-gate.json and raw artifacts.", ""]
    return "\n".join(lines)


def run(hermit, oops, output, jobs=None, baseline=BASELINE):
    checks = []
    for engine, loader in (("HermiT", lambda: reasoner_results(hermit, "HermiT")),
                           ("OOPS", lambda: oops_results(oops, baseline))):
        try:
            checks.extend(loader())
        except (OSError, ValueError, KeyError, TypeError, IndexError, AttributeError) as error:
            checks.append({"engine": engine, "input": "suite", "status": "tool_error",
                           "errors": [str(error)], "findings": {}})
    for name, status in (jobs or {}).items():
        engine = {"consistency": "HermiT", "pitfalls": "OOPS"}.get(name)
        # A semantic failure is an expected failed producer job. A failure with
        # clean reports still means installation/tests/annotations/upload failed.
        explained = engine and any(c["engine"] == engine and c["status"] != "pass" for c in checks)
        if status != "success" and not (status == "failure" and explained):
            checks.append({"engine": name, "input": "CI job", "status": "tool_error",
                           "errors": [f"Required job ended with {status}"], "findings": {}})
    code = max(EXIT_CODES[c["status"]] for c in checks)
    report = {"schema_version": 1, "status": ("pass", "findings", "tool_error")[code],
              "exit_code": code, "checks": checks}
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    (output / "quality-gate.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    summary = markdown(report)
    (output / "summary.md").write_text(summary, encoding="utf-8")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as stream:
            stream.write(summary)
    print(f"Quality gate: {report['status']} (exit {code}); reports: {output}")
    return code


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifacts", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--jobs", default="{}", help="JSON map of required CI job conclusions")
    options = parser.parse_args()
    sys.exit(run(options.artifacts / "HermiT Results", options.artifacts / "OOPS Results",
                 options.output, json.loads(options.jobs)))
