#!/usr/bin/env python3
"""Checks ontologies for pitfalls with the OOPS! web service and stores one XML report per file."""

import re
import sys
from pathlib import Path

import requests

OOPS_URL = "https://oops.linkeddata.es/rest"
PITFALLS = "2,3,4,5,6,7,8,10,11,12,13,19,20,21,22,25,26,27,28,29"


def oops(owl_file):
	content = Path(owl_file).read_text(encoding="utf-8")
	# OOPS! fails when it tries to download imports, so only the axioms of the file itself are checked.
	content = re.sub(r"\s*<owl:imports rdf:resource=\"[^\"]*\"/>", "", content)
	request = f"""<?xml version="1.0" encoding="UTF-8"?>
<OOPSRequest>
  <OntologyUrl></OntologyUrl>
  <OntologyContent><![CDATA[{content}]]></OntologyContent>
  <Pitfalls>{PITFALLS}</Pitfalls>
  <OutputFormat>XML</OutputFormat>
</OOPSRequest>"""
	response = requests.post(OOPS_URL, data=request.encode("utf-8"),
	                         headers={"Content-Type": "application/xml"}, timeout=300)
	response.raise_for_status()
	if "OOPSResponse" not in response.text:
		sys.exit(f"OOPS! failed for {owl_file}:\n{response.text}")
	return response.text


if __name__ == "__main__":
	out_dir = Path(sys.argv[1])
	for owl_file in sys.argv[2:]:
		report = out_dir / f"oops-{Path(owl_file).stem}.xml"
		report.write_text(oops(owl_file), encoding="utf-8")
		print(f"Wrote {report}")
