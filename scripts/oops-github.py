#!/usr/bin/env python3
"""Emit visible annotations from normalized local OOPS reports."""
import json
import sys

from quality_gate import oops_severity


def escape(text):
    return str(text).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def run(path):
    with open(path, encoding="utf-8") as stream:
        report = json.load(stream)
    if not report.get("complete"):
        print("::error::" + escape(report.get("error", "Incomplete OOPS execution")))
        return 2
    for finding in report["findings"]:
        severity = oops_severity(finding["code"])
        message = f'{finding["code"]} [{finding["importance"]}] {finding["name"]}: '
        message += ", ".join(finding["resources"]) or "ontology-wide finding"
        print(f"::{severity}::" + escape(message))
    # This step renders annotations; quality_gate.py enforces semantic failures.
    return 0


if __name__ == "__main__":
    try:
        sys.exit(run(sys.argv[1]))
    except (OSError, ValueError, KeyError, TypeError) as error:
        print("::error::" + escape(error))
        sys.exit(2)
