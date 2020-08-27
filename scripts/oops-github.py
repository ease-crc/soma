#!/usr/bin/env python

import sys
import xml.etree.ElementTree as ET

def report_important(name,descr,iris):
	# TODO: include file path and line number
  #print("::warning file=app.js,line=1,col=5::"+name)
  print("::warning::"+name)

def report_pitfall(name,descr,level,iris):
	if level=="Important":
		report_important(name,descr,iris)
	elif level=="Minor":
		pass

if __name__ == "__main__":
	# read arguments
	xml_file = sys.argv[1]
	root = ET.parse(xml_file).getroot()
	
	for pitfall_xml in root.findall('{http://www.oeg-upm.net/oops}Pitfall'):
		name  = pitfall_xml.findall('{http://www.oeg-upm.net/oops}Name')[0].text
		descr = pitfall_xml.findall('{http://www.oeg-upm.net/oops}Description')[0].text
		level = pitfall_xml.findall('{http://www.oeg-upm.net/oops}Importance')[0].text
		affects = pitfall_xml.findall('{http://www.oeg-upm.net/oops}Affects')[0]
		iris = list(map(lambda x: x.text, affects.findall('{http://www.oeg-upm.net/oops}AffectedElement')))
		report_pitfall(name,descr,level,iris)

