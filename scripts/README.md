This folder includes several utility scripts for working with the SOMA ontology.

**Controlled vocabulary generation**

Run ecv.sh to get an ELAN controlled vocabulary, constructed from concepts in the ontology. The script already configures which ontologies, and which parts of the taxonomy, are relevant for the generation. The output file will be called EASE.ecv, and will appear in the same folder as the script.

Dependencies:

* bash
* python (2.7 or higher)
* (python package) untangle
* (python package) uuid
* (python package) itertools
* (python package) hashlib

**Testing consistency**

Run the hermit.py script to verify an OWL file with the HermiT reasoner. Verification here means checking whether some concepts are subsumed by owl:Nothing, ie. they are unsatisfiable.

Syntax to run the script is:

./hermit_test.py [options] inputfile

where inputfile is a path to an OWL file. The following options are available:
* -r, --reasoner: path to HermiT.jar, the executable for the HermiT reasoner. If not provided, it is assumed the jar is in the same folder as where the script is called from
* -o, --output: path to a file where to store the results of the HermiT reasoner. If not provided, results are not stored

Classification and subsumption reasoning will be performed on the input owl file. In case of non-satisfiable concepts, messages will be produced on stdout (ie. the console) by the script. A successful run of the script, that is, if no errors happen and no non-satisfiable concepts are found, produces no console output.
