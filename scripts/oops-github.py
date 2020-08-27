#!/usr/bin/env python

import sys
import xml.etree.ElementTree as ET

def get_affected_soma_iris(xml_elem):
	affects = xml_elem.findall('{http://www.oeg-upm.net/oops}Affects')[0]
	iris = list(map(lambda x: x.text,
		affects.findall('{http://www.oeg-upm.net/oops}AffectedElement')))
	return list(filter(lambda x: "SOMA.owl" in x, iris))

def format_pitfall(name,descr,iris):
	return descr+" Affected resources: "+str(list(map(lambda x: x.split('#')[1],iris)))

def report_pitfall(name,descr,level,iris):
	msg = format_pitfall(name,descr,iris)
	if level=="Important":
		print("::warning ::["+level+"]"+msg)
	elif level=="Minor":
		print("::warning ::["+level+"]"+msg)

def report_suggestion(name,descr,iris):
	msg = format_pitfall(name,descr,iris)
	print("::info ::"+msg)

if __name__ == "__main__":
	# read arguments
	xml_file = sys.argv[1]
	root = ET.parse(xml_file).getroot()
	
	for pitfall_xml in root.findall('{http://www.oeg-upm.net/oops}Pitfall'):
		name  = pitfall_xml.findall('{http://www.oeg-upm.net/oops}Name')[0].text
		descr = pitfall_xml.findall('{http://www.oeg-upm.net/oops}Description')[0].text
		level = pitfall_xml.findall('{http://www.oeg-upm.net/oops}Importance')[0].text
		iris = get_affected_soma_iris(pitfall_xml)
		report_pitfall(name,descr,level,iris)
	
	for suggestion_xml in root.findall('{http://www.oeg-upm.net/oops}Suggestion'):
		name  = suggestion_xml.findall('{http://www.oeg-upm.net/oops}Name')[0].text
		descr = suggestion_xml.findall('{http://www.oeg-upm.net/oops}Description')[0].text
		iris = get_affected_soma_iris(suggestion_xml)
		report_suggestion(name,descr,iris)

