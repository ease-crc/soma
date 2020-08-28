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
          <Pitfalls></Pitfalls>
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
	


