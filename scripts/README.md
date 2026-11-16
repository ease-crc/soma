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

Check an ontology with the HermiT reasoner. Imports are resolved through the `catalog-v001.xml` next to the file. The check reports OWL 2 DL profile violations, inconsistency, unsatisfiable classes and unsatisfiable object properties, and exits with 1 if it finds any:

```sh
./mvnw package -f scripts/java/pom.xml
java -cp scripts/java/target/IsDefinedInAdder-1.0-SNAPSHOT.jar main.HermitCheck owl/SOMA-HOME.owl
```
