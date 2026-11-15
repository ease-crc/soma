# Local OOPS

OOPS is built from [oeg-upm/OOPS](https://github.com/oeg-upm/OOPS), the source
repository maintained by the Ontology Engineering Group at UPM. Its README and
POM identify it as the source of the public OOPS service. We pin
[`11572ea6b31cba29dd3f8a443e24311652f059de`](https://github.com/oeg-upm/OOPS/commit/11572ea6b31cba29dd3f8a443e24311652f059de)
(2025-07-01, `Improve showing compound results on the website`). Inspection on
2026-11-15 found this at master HEAD; the POM still says `0.3.0-SNAPSHOT`.
There is a CLI/API and a test suite, but no assumption of ongoing maintenance or
compatibility with the deployed website. This is upstream OOPS, not OOPS-Plus.

The Java sources and POM declare Apache-2.0. Upstream `LICENSES/` and `REUSE.toml`
also document CC0/BSD assets and reserved UPM logos. This integration uses Java
classes and classpath resources, and does not redistribute/deploy the webapp,
its logos or its WAR. Preserve upstream license notices if redistributing it.
WordNet is supplied by `extjwnl-data-wn31`; retain that artifact's notices too.

## Build and run

Install Python 3.12+, Git, and a JDK. The upstream target is Java 17, Jena 5.3.0
requires Java 17+, and SOMA uses JDK 25 in CI. From the repository root:

```sh
export JAVA_HOME=/path/to/jdk-25
export PATH="$JAVA_HOME/bin:$PATH"
python scripts/install-oops.py
OOPS_INTEGRATION=1 python -m unittest discover -s scripts/tests -v
python scripts/oops.py build/ci/dist/owl/SOMA-HOME.owl build/ci/reports
python scripts/oops-github.py build/ci/reports/oops.json
```

On Windows set `JAVA_HOME` and add its `bin` to `PATH` in PowerShell; use
`$env:OOPS_INTEGRATION='1'`. The installer also accepts `--java-home PATH` and
`--destination PATH`. The runner accepts `--installation PATH`, `--java PATH`,
`--catalog PATH`, `--timeout-seconds` (600 default), `--memory-mb` (2048 default),
and `--rules 8,12` for a nonempty subset of the supported rules.

Installation downloads the fixed source commit and Maven dependencies. It uses
SOMA's checksum-pinned Maven Wrapper, compiles upstream, copies runtime JARs,
and compiles the adapter, a versioned P06 correction, and the existing strict local ontology/catalog loader
against upstream's isolated OWLAPI 5.5.1 classpath. SOMA's application
JAR is not mixed into that classpath. No server, healthcheck, port, Docker image
or public OOPS endpoint is involved. At runtime no download is needed.

`installation.json` records repository, rule-source commit, dependency overrides, source patches
and SHA-256 of every runtime file. The runner checks both hashes and the exact
inventory; adding a JAR/class or modifying a resource invalidates installation.
The installer refuses edited source checkouts and clears only its validated
generated `source/target` and `adapter` directories before rebuilding.

`OopsWorker` calls the upstream `Checker.check` API directly in one bounded
child JVM. This avoids upstream CLI's all-rules selection and fixed output path,
and upstream Linter's incomplete RDF rendering of some pitfall variants. It
does not implement replacement rules. The parent passes arguments as a list,
captures stdout/stderr, checks exit status and structured output, and kills and
waits for the JVM on timeout through Python's `subprocess.run`. `-Xmx` limits heap,
not total RSS. A new temporary result path prevents stale output from passing.

The loader resolves imports only through a strict local catalog (defaults to
sibling `catalog-v001.xml`); unresolved imports fail. The complete import closure
is merged, including ontology annotations, serialized in memory as RDF/XML, and
passed to Jena with import fetching disabled. The JDK XML factory is explicitly
selected because upstream's old transitive Xerces provider cannot enforce the
catalog loader's `ACCESS_EXTERNAL_DTD` setting. Input/catalog/allowed target
hashes are checked before and after execution. No fallback to the public service
exists.

## Rules and reports

The previous list is retained, removing only the duplicate P25:

| Rules | Implementation/resources |
| --- | --- |
| P02, P03, P04, P05, P06, P08, P10, P11, P12, P13 | Upstream `checkers/Pnn.java`, local ontology graph |
| P07 | Upstream checker and `SynsetHelp`, local WordNet 3.1 JAR |
| P19, P20, P21, P22, P25, P26, P27, P28, P29 | Upstream `checkers/Pnn.java`, local ontology graph/tokenizer/stop words |

All 20 implementations are present and selected explicitly; missing/duplicate
registrations fail. We reviewed their calls and shared helpers. Unselected
P37/P38/P39/P41 use HTTP resource checks, P40 calls the external TripleChecker
service, and P30/P31/P32 also use WordNet. They are not silently enabled. No
external rule service needs a local replacement for the retained selection.

`oops.json` distinguishes `clean`, `findings` and `tool_error`, with `complete`,
rule coverage, source identity, limits, input hashes and worker Java version.
Findings retain upstream importance, name, explanation and affected resources.
P05/P12/P27's synthetic UUID wrapper resources are resolved to their actual
properties. Findings and resources are sorted and duplicate groups removed;
anonymous resources are represented as `[anonymous]`, without unstable blank-node
IDs. Ontology-wide findings are retained even without SOMA-specific resource IRIs.
There are no random report IDs or elapsed times in the normalized report.

Exit 0 means the selected checks completed, including runs with quality findings;
exit 2 means a technical/incomplete run. Escaping exceptions, missing output,
malformed output and unmet checker preconditions cannot pass. GitHub annotations
use the explicit [error/warning policy](../QUALITY-GATE.md), independent of
upstream importance; the aggregate quality gate blocks on error findings.
Annotation/report errors exit 2 instead
of being swallowed. Logs and reports are uploaded even on failure.

These are heuristic upstream checks, not correctness proofs. Several upstream
rules catch Jena conversion exceptions internally, sometimes recording warnings;
P13 also catches exceptions internally without exposing them. The adapter can
fail closed on exposed warnings/exceptions, but cannot reconstruct swallowed
upstream exceptions. P22 reports a first example for a naming inconsistency,
not an exhaustive list. No semantic changes to those rules are bundled here.

## Dependencies and build patches

Direct runtime dependencies include Jena core/arq 5.3.0, OWLAPI API/apibinding
5.5.1, commons-text 1.13.0, SLF4J 2.0.17/reload4j 1.2.26, Jersey 3.1.10,
JAXB 4.0.5, 3scale 3.0.2, extjwnl 2.0.5/WordNet data 1.2, picocli 4.7.6 and
commons-codec 1.15. Upstream also brings web-oriented transitive dependencies
even though no web server is started. The POM pins build plugins, including
compiler 3.13.0 and dependency-plugin 3.8.1. The runner needs only Python stdlib;
the old `requests` dependency is no longer needed for OOPS.

Dependency overrides replace older Jackson, Guava and commons-lang3 versions
from the pinned upstream POM.
The installer generates `source/soma-pom.xml` beside the original POM, updating
direct Jackson dependencies and importing Jackson BOM 2.23.0, and managing
Guava 33.8.0-jre and commons-lang3 3.22.0. Rule sources and upstream POM stay
unchanged in the checkout. `dependency_overrides` in the installation manifest makes this build
adaptation explicit; older manifests are rejected.

SOMA's deeper hierarchies exposed an upstream P06 `ConcurrentModificationException`:
the checker appends ancestors while iterating the same ArrayList with foreach.
The versioned `P06-traversal-v1` build correction uses an index over the growing
list, preserving transitive traversal and duplicate checks. It also replaces
P06's erroneous multiple `i++` operations for owl:Thing with `continue`.
The installer checks each patch preimage exactly once and writes a generated
`patched-src/P06.java` with the original license header. Only this class is
recompiled into the adapter directory, ahead of upstream classes on the classpath;
the original checkout stays unchanged. `source_patches` is checked in installation
manifests and recorded in reports. This is a traversal bugfix, not a replacement
pitfall definition; three-level acyclic and cyclic hierarchies are regression-tested.

## Validation

Tests cover a clean annotated class, P08 on an unannotated class, findings from
an imported ontology via a local catalog, all selected rule registrations,
stable repeated P05/P12/P27 reports, changed inputs/installations, malformed or
missing results, process failures and an actual timeout. The integration tests
force Java HTTP/HTTPS through unreachable localhost port 9, so the public website
cannot provide results. This is an HTTP/HTTPS reachability test, not an OS-level
network sandbox. Synthetic fixtures test technical behavior; SOMA findings are
not suppressed. Hosted CI execution is still separate from local validation.
