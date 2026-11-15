# Local Konclude backend

Konclude is a local experimental backend only. It is excluded from CI jobs,
CI Maven tests and the quality gate because the pinned release cannot completely
check the current SOMA theories. HermiT is the sole CI reasoner.

The shared reasoning CLI supports `--reasoner=konclude --binary=PATH`; HermiT
remains the default. Both adapters use the same strict local loader, corpus
snapshot, coverage planner, report schema and exit codes. Konclude directly checks
consistency, named class satisfiability, empty ObjectProperties **and** empty
DataProperties. There is no hidden HermiT fallback.

```sh
# Ubuntu 24.04: native upstream binary requires the legacy PCRE runtime.
sudo apt-get update && sudo apt-get install --yes libpcre3
python scripts/install-konclude.py
export KONCLUDE_BINARY="$PWD/build/tools/konclude/Konclude-v0.7.0-1138-Linux-x64-GCC-Static-Qt5.12.10/Binaries/Konclude"
bash ./mvnw -B -ntp -f scripts/java/pom.xml verify
java -cp scripts/java/target/IsDefinedInAdder-1.0-SNAPSHOT.jar main.reasoning.HermitCli \
  --reasoner=konclude --binary="$KONCLUDE_BINARY" \
  --suite=. --output=build/ci/reports/konclude --timeout-seconds=600 --memory-mb=2048
# Or select a single source/export with --input=FILE [--catalog=FILE].
```

On Windows, run `python scripts/install-konclude.py` and set `KONCLUDE_BINARY`
to the printed `Binaries/Konclude.exe` path; use `mvnw.cmd` and quote paths.
Pass that environment variable to Maven to enable the real native integration
tests. Without it, the six native tests are explicitly skipped; parser and
process unit tests still run locally. CI excludes both Konclude test classes
with `-Dtest=!Konclude*` and does not install or invoke Konclude. Local runs
preserve reports, logs and installation identity on failure.

## Provenance

The native [upstream release v0.7.0-1138](https://github.com/konclude/Konclude/releases/tag/v0.7.0-1138)
was published on 2021-06-19. Its binary identifies source revision `500e11d9`.
The installer downloads only the official HTTPS release assets and verifies
their exact SHA-256 and the extracted executable. The CLI independently checks
the executable hash before launching a worker; a missing binary, shell wrapper,
different version or modified executable yields `TOOL_ERROR`.

| Asset suffix | Archive SHA-256 | Binaries executable SHA-256 |
| --- | --- | --- |
| Windows-x64-MSVC-Dynamic-Qt5.15.2 | `52ea75ebac2e996cb6bdb256271d1a772ec7b2ac1cce99c6da13a7d967698f98` | `0d9bf7dc5b2d6e35e873c1b4fe1ef169b263fb18f6fe7183225ac65b17e31ed7` |
| Linux-x64-GCC-Static-Qt5.12.10 | `37216c18f45a80165aec1e76aeefd62d933d6a3f8f134c5aca0707c676fda76e` | `5484f16dcff71486a5deed9cf9cea8a0f7febf115aaa6915ad2e8c1cf16965e3` |

Each full asset name is `Konclude-v0.7.0-1138-<suffix>.zip`. These pins were
measured from the official release assets on 2026-11-15. Upstream supplies no
asset digest/signature in its release API, so this establishes HTTPS origin and
repeatable bytes, not an independent reproducible-build attestation. Konclude
ships GPLv3 and bundled-library license notices; the installer retains the full
distribution. Native Windows installation worked directly; no container was
needed. The Linux distribution is also a native executable despite the separate
Docker assets in the upstream release. Only x86-64 Windows/Linux are pinned.

## Execution and coverage

The worker loads the selected input and complete import closure using its local
catalog, rejects unmapped/cached mismatches and creates a separate import-free
OWL/XML ontology. It takes the property lists from exactly that input signature,
including imported and undeclared-but-used properties. For every non-bottom
ObjectProperty it defines a fresh class equivalent to
`ObjectSomeValuesFrom(p, owl:Thing)`; for every non-bottom DataProperty it defines
`DataSomeValuesFrom(p, rdfs:Literal)`. These definitions form a conservative
extension. Query IRIs avoid the entire original entity signature. The original
axioms and queries are classified **together**; `query-map.json` records both
property lists and their query mapping. No remote ontology imports are passed to
Konclude and no separately generated property file can become stale.

Before checking the profile, the temporary ontology normalizes
`ObjectMinCardinality(1,p,C)` to the exactly equivalent
`ObjectSomeValuesFrom(p,C)`, including nested and inverse-role expressions.
This lets the DUL `Place` restriction use a non-simple role without altering
the ontology's meaning. Other cardinalities and profile restrictions are
preserved. `normalization.json` records the rule and number of axiom replacements;
source files and exported public documents remain unchanged.
Logical checks omit annotation axioms, AnnotationProperty declarations and axiom annotations, which have no
effect under OWL Direct Semantics. This avoids Konclude's warning about ordinary
labels/comments. The normalization artifact records removed annotation-axiom
and annotation-declaration counts as well as stripped annotated-axiom counts;
all logical axioms remain present.

Consistency is checked first. The native `consistency` result must be exactly
`true` or `false`; false yields `INCONSISTENT` and no entity lists. A consistent
ontology is then classified and the namespace-aware XML parser follows
equivalence/subclass edges to `owl:Nothing`. It verifies the expected class/query
signature and paths to the top/bottom classes. It rejects missing/empty/truncated
XML, foreign/unknown axioms and DTDs. Only original named classes enter the class
findings; query classes map back to properties. A property-only finding exits 1.

The outer Java process supervises loading and both native calls within one
per-input timeout. Commands use argument lists; unique work directories prevent
reuse of old native results. Native stdout/stderr, command arguments, process
return codes and XML/boolean results remain visible in each report directory.
Nonzero exits, timeouts, missing output and native error/warning log lines fail
closed. Reports record version, binary hash, Java heap, thread count (2), input
hashes and total wall time. `--memory-mb` limits the Java worker heap; native RSS
has no application-enforced cap. Set operating-system resource limits separately if needed.

## Supported-input limits

Konclude's Functional Syntax reader silently ignores `DatatypeDefinition`, which
can hide datatype-induced emptiness. Its OWL/XML reader reports this as an error
but still exits 0. This integration therefore exports **OWL/XML** and treats native
error/warning lines as tool failures even with exit 0 and plausible results.
`HasKey` is similarly reported as unsupported by this version. SWRL builtin
atoms can be silently ignored, so all SWRL rules are explicitly rejected. An
OWL 2 DL profile check rejects unsupported global restrictions (e.g. asymmetric
non-simple properties and irregular chains) before native execution. Declarations
are added for every used entity, including query classes, before this check.
These limits affect consistency and class results as well as property results;
delegating only DataProperties would not repair them.

The datatype-range fixture is checked directly by Konclude and matches HermiT.
Unsupported datatype definitions yield an explicit tool failure, never a claim
that the DataProperty check completed. Regression tests also cover SWRL,
non-simple roles, imported and undeclared properties, paths with spaces, missing
or modified binaries, incomplete XML, failed launch, abort, timeout and error
logs with apparently valid output.
