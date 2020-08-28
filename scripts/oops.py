#!/usr/bin/env python

import os
import sys
import requests

OOPS_URL="http://oops.linkeddata.es/rest"

def oops(owl_file):
	#
	f = open(owl_file, 'r')
	content = f.read()
	f.close()
	#
	xml_content = """
    <?xml version="1.0" encoding="UTF-8"?>
    <OOPSRequest>
          <OntologyUrl>http://www.ease-crc.org/ont/SOMA.owl</OntologyUrl>
          <OntologyContent>%s</OntologyContent>
          <Pitfalls>2 3 4 5 6 7 8 10 11 12 13 19 20 21 22 25 25 26 27 28 29</Pitfalls>
          <OutputFormat>XML</OutputFormat>
    </OOPSRequest>
	""" % (content)
	headers = {'Content-Type': 'application/xml',
	           'Connection': 'Keep-Alive',
	           'Content-Length': str(len(xml_content)),
	           'Accept-Charset': 'utf-8'
	}
	return requests.post(OOPS_URL, data=xml_content, headers=headers).text

if __name__ == "__main__":
	# read arguments
	owl_file = sys.argv[1]
	out_dir = sys.argv[2]
	# get XML data
	xml_data = oops(owl_file)
	# write file
	out_file = os.path.join(out_dir,'oops.xml')
	f = open(out_file, "w")
	f.write(xml_data)
	f.close()
	


