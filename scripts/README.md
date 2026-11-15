This folder includes several utility scripts for working with the SOMA ontology.

**Local ontology pitfalls**

OOPS now builds from a fixed upstream source commit and runs locally without the
public service. See [installation, rule selection, dependency review and tests](oops/README.md).
Build with `python scripts/install-oops.py` (`JAVA_HOME` required), then run
`python scripts/oops.py build/ci/dist/owl/SOMA-HOME.owl build/ci/reports`.
The normalized report is `oops.json`; technical failures exit 2. The aggregate
[quality gate](QUALITY-GATE.md) classifies OOPS findings as errors or warnings,
records existing warnings, and combines them with mandatory reasoner checks.

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

Build with JDK 25 and the pinned Maven Wrapper, then use the OWLAPI adapter:

```sh
bash ./mvnw -B -ntp verify -f scripts/java/pom.xml
java -cp scripts/java/target/IsDefinedInAdder-1.0-SNAPSHOT.jar main.reasoning.HermitCli --suite=. --output=build/ci/reports/hermit --timeout-seconds=600 --memory-mb=2048
# Single document (catalog defaults to sibling catalog-v001.xml if present):
java -cp scripts/java/target/IsDefinedInAdder-1.0-SNAPSHOT.jar main.reasoning.HermitCli --input=owl/SOMA-HOME.owl --catalog=owl/catalog-v001.xml --output=build/hermit-home
```

`--suite` loads and validates all `.owl` files and local catalogs in `owl/` and
`build/ci/dist/owl/`. Expensive reasoning runs cover the public roots: source
SOMA-ALL/HOME and exported SOMA/HOME. Further ontologies are selected automatically
when their logical axioms and named class/ObjectProperty/DataProperty signatures
are not contained in any single selected root's import closure. Axiom annotations
do not affect this logical coverage. Independent root theories are never unioned
to claim coverage of another input.

Currently this selects five roots per set: the two main roots plus ELAN, Allen
and USD. `coverage.json` records the assignment of every loaded file to a root.
`input-snapshot.json` binds planning and results to the same file contents and
inventory; changes during the suite make it incomplete and fail the gate.
Each selected root gets a fresh manager/reasoner in a separate Java process.
Use `--scope=modules` for optional isolated diagnostics of every module, or
`--input=FILE` for one document. Isolated checks help localize findings; they are
not needed in the normal aggregate quality check.

Build exports first with `verify`. The suite continues after reasoner findings
and tool failures and writes `summary.json`, `coverage.json`, per-root
`report.json` and unique worker stdout/stderr logs. Invalid files/catalogs during
planning fail with an incomplete summary. Imports resolve strictly through local
catalogs with no network fallback. Standalone import-free files need no catalog.

HermiT 1.4.5.519 is a pinned application dependency, packaged in the Java CLI.
Consistency is checked before unsatisfiable classes and empty ObjectProperties
and DataProperties over the entire import closure. Results come from OWLAPI,
including datatype-induced emptiness. See [the reasoning contract](java/REASONING-CONTRACT.txt).
The former HermiT CLI text parser `hermit_test.py` has been removed.

Konclude is also available as a native, checksum-pinned backend, with direct
ObjectProperty and DataProperty queries over the same importclosure:

```sh
# Ubuntu 24.04 runtime dependency:
sudo apt-get update && sudo apt-get install --yes libpcre3
export KONCLUDE_BINARY="$(python scripts/install-konclude.py)"
bash ./mvnw -B -ntp -f scripts/java/pom.xml verify
java -cp scripts/java/target/IsDefinedInAdder-1.0-SNAPSHOT.jar main.reasoning.HermitCli --reasoner=konclude --binary="$KONCLUDE_BINARY" --suite=. --output=build/ci/reports/konclude
```

Windows: use `mvnw.cmd`, set `KONCLUDE_BINARY` to the installer's printed `.exe`
path, and quote it in `--binary`. Maven runs the native tests when that variable
is present. Konclude is local-only; CI excludes its tests and uses HermiT as
the sole reasoner. See
[Konclude installation and supported-input limits](java/KONCLUDE.md).
HermiT remains the default backend.

Exit codes: 0 = complete and no findings, 1 = inconsistency or semantic findings,
2 = tool failure/incomplete check. Reports record the dependency version,
Java/OWLAPI versions, wall time, timeout, worker heap limit, root path and SHA-256
hashes of the root, local catalog and all allowed import targets (a conservative
superset of the closure). Inputs are hashed before and after execution; a change
is a tool error. Defaults are 600 seconds per input and 2048 MiB Java heap per
worker; `--timeout-seconds` and `--memory-mb` override them. The heap cap is `-Xmx`,
not a cap on total process RSS. Empty, invalid or missing worker output cannot
pass. Maven tests use synthetic fixtures; only running the suite checks SOMA.


**Java ontology CLI**

Install JDK 25 and set `JAVA_HOME`. From the repository root:

```sh
bash ./mvnw -B -ntp verify -f scripts/java/pom.xml -Dsoma.version=current
# Windows: use .\mvnw.cmd for Maven.
```

`verify` runs the tests, builds the CLI, processes the ontologies and verifies
the serialized exports with an independent manager and generated catalog.
It defaults to version `current`; override it with `-Dsoma.version=VERSION`.
The CLI can also run directly from the repository root:

```sh
java -jar scripts/java/target/IsDefinedInAdder-1.0-SNAPSHOT.jar --versionInfo=current
```

It accepts `--config=PATH` (default `scripts/java/application.yaml`),
`--commit=SHA` and the JVM properties `soma.version` and `soma.commit`.
The manifest records HEAD automatically when Git is available; for source
archives supply `--commit=SHA`, otherwise the revision is recorded as `unknown`.
Paths inside YAML are resolved relative to
that file. `ontology.output-directory` is required and must be separate from the
input directory. It defaults to `build/ci`. Collapse `out-path` values are
filenames inside its `dist/owl` subdirectory. The default exports all 16 source
modules to `build/ci/dist/owl`, replacing
SOMA.owl and SOMA-HOME.owl with the configured aggregations, plus a complete
local XML import catalog. Inputs under `owl/` are read only.

`build/ci/work` contains import-free Functional Syntax documents for external
reasoners; these include the entire public export closure, including DUL.
`build/ci/reports/processing.json` records success/module details or a processing
failure. `build/ci/manifest.json` records commit, version, Java/Maven/OWLAPI,
enabled transformations and SHA-256 hashes of the public OWL files and catalog.
`build/ci/dist/files` is reserved for documentation. A failed processing run
removes any previous success manifest; partially written or previous exports
must not be published without a successful build. Invalid configuration errors
appear on stderr before output begins. Unexpected stale OWL files in the export
directory cause an error rather than being silently reused. Output paths must
not link to source files, including hard links. Build cleanup must preserve
`build/local-plans`; only `scripts/java/target` and `build/ci` are generated here.

Processing order is explicit in `CIRunner`: load sources, copy into an independent
manager, optionally repair entity namespaces, check namespaces, optionally rewrite
GCIs, add isDefinedBy and version annotations, aggregate, then export. Only the
exporter writes files. The four transformations are individually configurable in
YAML. Version is required only when version annotations are enabled. Defaults
preserve the previous enabled steps; IRI repair stays off.

Catalog imports must resolve to existing local files; missing or duplicate
mappings fail even when OWLAPI has already cached the imported ontology. Explicit
catalog aliases remain supported: Upstream HOME imports SOMA-ALL.owl while the
actual ontology IRI is SOMA-All.owl. Working imports are canonicalized through
the catalog, without changing source files. A separate generated catalog ensures
HOME loads the exported SOMA aggregation rather than the small source SOMA module.
No network fallback, catalog delegation or rewrite directives are supported.

Intentional changes: aggregation uses the entire transitive import closure and
excludes the entire closure of retained imports. Root ontology annotations are
preserved. GCI rewrites retain annotations and avoid singleton intersections.
Manchester export is rejected because it can lose GCIs; use functional, rdf_xml
or owl_xml. Configuration mistakes and ambiguous ontology references fail early.

Tests cover invalid configuration, missing/ambiguous imports, unchanged source
bytes on success and failure, exact synthetic axiom differences, complete SOMA
aggregation and reload of all exports using only their local catalog. HermiT
contract and subprocess tests run during Maven testing; the full semantic suite
is a separate command and CI step.
Dependency versions and overrides are defined in [pom.xml](java/pom.xml);
the Maven distribution is pinned in [the wrapper configuration](../.mvn/wrapper/maven-wrapper.properties).

Use Maven with JDK 25 for verification. An IDE configured with JDK 21 cannot
compile this project until its project/module SDK is updated to JDK 25.
