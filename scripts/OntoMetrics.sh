#!/bin/bash
# The script calls a webservice that computes ontology metrics.
# It stores the result in JSON format, but also in JS such that
# it can be imported in HTML code.

BASEDIR=$(dirname "$0")
# The local OWL file
OWL_FILE="$1"
# The server URL
HOST="https://ontometrics.informatik.uni-rostock.de/ontologymetrics/ServletController"

echo "${BASEDIR}"

# Prints urldata to stdin
function get_data {
	echo -n "text="
	php -r "echo rawurlencode(file_get_contents('${1}'));"
	echo "&path=&base=on"
}

# the web-service return HTML with a link to XML data
echo "fetching result from https://ontometrics.informatik.uni-rostock.de"
XML_URL=`get_data ${OWL_FILE} | curl ${HOST} \
	-H 'User-Agent: Mozilla/5.0 (X11; Linux x86_64; rv:78.0) Gecko/20100101 Firefox/78.0' \
	-H 'Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8' \
	-H 'Accept-Language: en-US,en;q=0.5' \
	--compressed \
	-H 'Content-Type: application/x-www-form-urlencoded' \
	-H 'Origin: https://ontometrics.informatik.uni-rostock.de' \
	-H 'Connection: keep-alive' \
	-H 'Referer: https://ontometrics.informatik.uni-rostock.de/ontologymetrics/' \
	-H 'Upgrade-Insecure-Requests: 1' \
	--data "@-" | egrep -o 'https?://[^ ]+\.xml'`

# download XML data
echo "writing metrics.xml"
curl ${XML_URL} > "$2/metrics.xml"
# convert to JSON
echo "writing metrics.json"
${BASEDIR}/xml2json.py -t xml2json -o "$2/metrics.json" "$2/metrics.xml"
# make it accessible as JS script
echo "writing metrics.js"
echo -n "metric_data='" > "$2/metrics.js"
cat "$2/metrics.json" >> "$2/metrics.js"
echo "';" >> "$2/metrics.js"

