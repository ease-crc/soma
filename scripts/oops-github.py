#!/usr/bin/env python3

import os
import re
import sys
import xml.etree.ElementTree as ET

# The checked ontologies overlap, so the same finding can be reported for several files.
reported = set()

def emit(annotation):
	# Escaping required by GitHub workflow commands.
	annotation = annotation.strip().replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
	if annotation not in reported:
		reported.add(annotation)
		print(annotation)

def find_entity(name):
	# Declarations in Functional Syntax (SOMA:name or <...#name>) or RDF/XML (rdf:about="...#name").
	declaration = re.compile(r'Declaration\(\w+\((\w*:|<[^>]*#)' + re.escape(name) + r'>?\)|#' + re.escape(name) + '">')
	for fname in sorted(os.listdir('owl')):
		fpath='owl/'+fname
		# Only SOMA entities are reported; DUL declares some of the same local names.
		if fname == 'DUL.owl' or not os.path.isfile(fpath):
			continue
		with open(fpath, encoding='utf-8') as f:
			count=0
			for line in f:
				count+=1
				if declaration.search(line):
					return (fpath,count)

def get_affected_soma_iris(xml_elem):
	affects = xml_elem.findall('{http://www.oeg-upm.net/oops}Affects')[0]
	iris = list(map(lambda x: x.text,
		affects.iter('{http://www.oeg-upm.net/oops}AffectedElement')))
	return list(filter(lambda x: "SOMA.owl" in x, iris))

def get_resource_names(iris):
	return list(map(lambda x: x.split('#')[-1],iris))

def report_pitfall(name,descr,level,iris):
	names = get_resource_names(iris)
	if level in ("Critical", "Important", "Minor"):
		msg_level="warning"
	else:
		msg_level="debug"
	for name in names:
		needle = find_entity(name)
		if needle!=None:
			(path,line) = needle
			emit("::"+msg_level+" file="+path+",line="+str(line)+"::["+level+"]("+name+") "+descr)
		else:
			emit("::"+msg_level+" file=SOMA.owl::["+level+"]("+name+") "+descr)

def report_suggestion(name,descr,iris):
	#names = get_resource_names(iris)
	#msg = format_pitfall(name,descr,names)
	emit("::info ::"+descr)

def run(xml_file):
	root = ET.parse(xml_file).getroot()
	
	pitfalls = {"Critical": [], "Important": [], "Minor": []}
	for pitfall_xml in root.findall('{http://www.oeg-upm.net/oops}Pitfall'):
		name  = pitfall_xml.findall('{http://www.oeg-upm.net/oops}Name')[0].text
		descr = pitfall_xml.findall('{http://www.oeg-upm.net/oops}Description')[0].text
		level = pitfall_xml.findall('{http://www.oeg-upm.net/oops}Importance')[0].text
		iris = get_affected_soma_iris(pitfall_xml)
		if len(iris)>0:
			pitfalls.setdefault(level, []).append((name,descr,iris))
	
	for level in pitfalls:
		for (name,descr,iris) in pitfalls[level]:
			report_pitfall(name,descr,level,iris)
	
	for suggestion_xml in root.findall('{http://www.oeg-upm.net/oops}Suggestion'):
		name  = suggestion_xml.findall('{http://www.oeg-upm.net/oops}Name')[0].text
		descr = suggestion_xml.findall('{http://www.oeg-upm.net/oops}Description')[0].text
		iris = get_affected_soma_iris(suggestion_xml)
		if len(iris)>0:
			report_suggestion(name,descr,iris)

if __name__ == "__main__":
	for xml_file in sys.argv[1:]:
		run(xml_file)

