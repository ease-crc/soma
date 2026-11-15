#!/usr/bin/env python3
"""Run local OOPS. Exit 0: complete, 2: technical failure; findings remain warnings."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

from oops_common import COMMIT, DEFAULT_INSTALL, DEPENDENCY_OVERRIDES, RULES, SOURCE_PATCHES


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def installation_classpath(installation):
    marker = json.loads((installation / "installation.json").read_text(encoding="utf-8"))
    if (marker["commit"] != COMMIT or marker.get("dependency_overrides") != DEPENDENCY_OVERRIDES
            or marker.get("source_patches") != SOURCE_PATCHES
            or not marker["files"]):
        raise ValueError("Unrecognized OOPS installation")
    actual = {p.relative_to(installation).as_posix() for directory in
              (installation / "adapter", installation / "source/target/classes", installation / "source/target/lib")
              for p in directory.rglob("*") if p.is_file()}
    if actual != set(marker["files"]):
        raise ValueError("OOPS runtime inventory changed; reinstall")
    for name, expected in marker["files"].items():
        path = (installation / name).resolve()
        if not path.is_relative_to(installation.resolve()) or digest(path) != expected:
            raise ValueError(f"OOPS installation changed: {name}")
    return os.pathsep.join(map(str, [installation / "adapter", installation / "source/target/classes",
                                    installation / "source/target/lib/*"]))


def run_check(input_file, output, installation=DEFAULT_INSTALL, java="java", catalog=None,
              timeout=600, memory=2048, rules=RULES):
    output.mkdir(parents=True, exist_ok=True)
    report = {"schema_version": 1, "engine": "OOPS", "commit": COMMIT,
              "selected_rules": list(rules), "status": "tool_error", "complete": False,
              "findings": [], "warnings": [], "timeout_seconds": timeout, "memory_mb": memory}
    report["source_patches"] = SOURCE_PATCHES
    report["dependency_overrides"] = DEPENDENCY_OVERRIDES
    try:
        if timeout <= 0 or memory <= 0 or not rules or len(set(rules)) != len(rules) or not set(rules).issubset(RULES):
            raise ValueError("Positive limits and a nonempty unique subset of supported rules are required")
        input_file = input_file.resolve(strict=True)
        if catalog is None and (input_file.parent / "catalog-v001.xml").exists():
            catalog = input_file.parent / "catalog-v001.xml"
        inputs = {input_file}
        if catalog is not None:
            catalog = catalog.resolve(strict=True)
            inputs.add(catalog)
            inputs.update(catalog.parent.rglob("*.owl"))
            content = catalog.read_bytes()
            if b"<!DOCTYPE" in content.upper():
                raise ValueError("Catalog DTDs are not supported")
            for entry in ET.fromstring(content).iter("{urn:oasis:names:tc:entity:xmlns:xml:catalog}uri"):
                target = (catalog.parent / entry.attrib["uri"]).resolve(strict=True)
                if not target.is_relative_to(catalog.parent):
                    raise ValueError("Catalog target outside local directory")
                inputs.add(target)
        before = {str(p): digest(p) for p in sorted(inputs)}
        report["inputs_sha256"] = before
        classpath = installation_classpath(installation.resolve())
        with tempfile.TemporaryDirectory(prefix="oops-", dir=output) as work:
            result_file = Path(work).resolve() / "result.json"
            command = [str(java), f"-Xmx{memory}m", "-cp", classpath, "OopsWorker",
                       str(input_file), str(catalog) if catalog else "", str(result_file),
                       ",".join(map(str, rules))]
            with (output / "stdout.log").open("w", encoding="utf-8") as stdout, \
                 (output / "stderr.log").open("w", encoding="utf-8") as stderr:
                process = subprocess.run(command, stdout=stdout, stderr=stderr, timeout=timeout, check=False)
            report["returncode"] = process.returncode
            if process.returncode:
                raise RuntimeError(f"OOPS worker exited {process.returncode}; see stderr.log")
            result = json.loads(result_file.read_text(encoding="utf-8"))
            if result["completed_rules"] != sorted(rules) or not isinstance(result["findings"], list):
                raise ValueError("Incomplete or invalid OOPS worker report")
            if not isinstance(result["warnings"], list) or not all(isinstance(w, str) for w in result["warnings"]):
                raise ValueError("Invalid OOPS warnings")
            for finding in result["findings"]:
                if (not isinstance(finding, dict) or
                    not all(isinstance(finding.get(key), str) and finding[key]
                            for key in ("code", "name", "description", "importance")) or
                    not isinstance(finding.get("resources"), list) or
                    not all(isinstance(r, str) for r in finding["resources"])):
                    raise ValueError("Invalid OOPS finding")
            if before != {str(p): digest(p) for p in sorted(inputs)}:
                raise ValueError("Ontology inputs changed during OOPS execution")
            report.update(result)
            if result["warnings"]:
                raise RuntimeError("OOPS reported unmet rule preconditions; see warnings")
            report.update(status="findings" if result["findings"] else "clean", complete=True)
    except (OSError, ValueError, KeyError, TypeError, RuntimeError, ET.ParseError, subprocess.SubprocessError) as error:
        report["error"] = str(error)
    (output / "oops.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 0 if report["complete"] else 2


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--installation", type=Path, default=DEFAULT_INSTALL)
    parser.add_argument("--java", default="java")
    parser.add_argument("--catalog", type=Path)
    parser.add_argument("--timeout-seconds", type=float, default=600)
    parser.add_argument("--memory-mb", type=int, default=2048)
    parser.add_argument("--rules", default=",".join(map(str, RULES)))
    options = parser.parse_args()
    sys.exit(run_check(options.input, options.output, options.installation, options.java, options.catalog,
                       options.timeout_seconds, options.memory_mb, tuple(map(int, options.rules.split(",")))))
