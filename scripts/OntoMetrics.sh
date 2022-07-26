#!/bin/bash
# The script calls a webservice that computes ontology metrics.
# It stores the result in JSON format, but also in JS such that
# it can be imported in HTML code.

BASEDIR=$(dirname "$0")
# The local OWL file
OWL_FILE="$1"
# The server URL
HOST="http://opi.informatik.uni-rostock.de/api?url"

echo "${BASEDIR}"

# the web-service return HTML with a link to XML data
echo "fetching result from ${HOST}=${OWL_FILE}"
curl -X ${HOST}=${OWL_FILE}  > $2/metrics.xml

# convert to JSON
echo "writing metrics.json"
${BASEDIR}/xml2json.py -t xml2json -o "$2/metrics.json" "$2/metrics.xml"
# make it accessible as JS script
echo "writing metrics.js"
echo -n "metric_data='" > "$2/metrics.js"
cat "$2/metrics.json" >> "$2/metrics.js"
echo "';" >> "$2/metrics.js"

