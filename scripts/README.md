This folder includes several utility scripts for working with the EASE ontologies.

**Controlled vocabulary generation**

Run ecv.sh to get an ELAN controlled vocabulary, constructed from concepts in the ontology. The script already configures which ontologies, and which parts of the taxonomy, are relevant for the generation. The output file will be called EASE.ecv, and will appear in the same folder as the script.

Dependencies:

* bash
* python (2.7 or higher)
* (python package) untangle
* (python package) uuid
* (python package) itertools
* (python package) hashlib

**Merging of ontology files**

Run uglify.sh to merge all EASE owl files into a single one. The generated file is called EASE-UGLY.owl, and will appear in the owl subfolder of this ROS package.

Dependencies:

* bash
* ROS
* rosprolog

