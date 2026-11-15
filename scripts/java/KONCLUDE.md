# Native Konclude integration (B3)

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
process unit tests still run. The Konclude CI job always installs the pinned
release and supplies the variable. It preserves reports, logs and installation
identity even when semantic findings or tool failures block the job.

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

The worker loads the selected input and complete importclosure using its local
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
has no application-enforced cap. CI runner memory limits still apply.

## Supported-input limits found during independent review

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

The B1 datatype-range fixture is checked directly by Konclude and matches HermiT.
Unsupported datatype definitions yield an explicit tool failure, never a claim
that the DataProperty check completed. Regression tests also cover SWRL,
non-simple roles, imported and undeclared properties, paths with spaces, missing
or modified binaries, incomplete XML, failed launch, abort, timeout and error
logs with apparently valid output.

## Reassessment of PR #231 and #247

The full retrieved patches and discussion are preserved in
[KONCLUDE-PR-REVIEW.txt](KONCLUDE-PR-REVIEW.txt) (GitHub API snapshot 2026-11-15).
[PR #231](https://github.com/ease-crc/soma/pull/231) remains open at head
`1446cc6ecda76b9bf48bea7fb6d0e34c36f5104c`; base is
`b9613c9ef989bc2eeb9b936cdee0c283b71e7041`. All six changed files and 37 issue
comments were retrieved; there were no inline review comments or reviews.
[PR #247](https://github.com/ease-crc/soma/pull/247) was merged into that work at
`bb9256c9ebc66f941ac4dae710feed4781e048a5` (head
`7fac7d9634bfc22cef0c391aa6dd72687800c77d`), replacing a nonexistent local action
with an unpinned Docker invocation. Its one changed file and empty discussion
were also captured.

This integration retains the original existential-query idea and addresses the
discussion's actual failures: unsupported RDF/XML, DUL/import availability,
missing input inside the container, stale/mismatched property lists, bracketed
IRIs, `SOMA-HOM` typo and nonexistent binary. It reuses A3/B2 coverage instead of
adding the PR's JGraphT/Spring property exporter. There is no `_parseHomebrew`,
shell-composed invocation, remote import assumption or optional property check.

## Measurement and reasoner choice (2026-11-15)

[KONCLUDE-COMPARISON.json](KONCLUDE-COMPARISON.json) preserves per-root statuses,
timings, findings and all 34 input SHA-256 hashes. The complete HermiT measurement
from 2026-11-14 and the new Konclude run used **identical** source/export files
and catalogs, verified by exact snapshot equality. Both used 600 seconds and
2048 MiB worker heap per selected input. Five covering roots were attempted for
each of sources and exports; suite discovery completed for both adapters.

| Result over 10 roots | HermiT | Konclude |
| --- | ---: | ---: |
| PASS | 2 | 0 |
| FINDINGS | 5 | 0 |
| TOOL_ERROR | 3 | 10 |
| Completed semantic checks | 7 | 0 |
| Sum of per-root wall time | 2504.180 s | 51.195 s |

The short Konclude times measure rejection, **not successful classification**.
The two Allen roots fail the DL profile check. The other eight reach native
execution but Konclude reports unsupported datatype-definition/key axioms and
returns exit 0 anyway; this adapter correctly reports incomplete `TOOL_ERROR`.
SOMA/ALL/HOME/ELAN contain six DatatypeDefinition axioms and one HasKey; USD
contains one DatatypeDefinition. Those axioms cannot be dropped without changing
the checked theory. The normalizer handled DUL's equivalent minimum-one
restrictions, and removing annotation-only content avoided unrelated warnings.
The native result therefore provides no basis for a classification speed claim.
HermiT found eight distinct unsatisfiable classes and three empty object
properties; its Allen inputs fail and exported HOME times out at 600 seconds.

**Decision: keep HermiT as the default and Konclude as an explicit alternative.**
Konclude passes the B1 contract including direct DataProperty emptiness, but
cannot completely check the current SOMA theories. HermiT currently offers more
coverage and simpler cross-platform packaging as a pinned Maven dependency.
Konclude adds platform-specific release artifacts and a legacy Linux PCRE
runtime. Any future default switch needs a release supporting the actual axioms,
complete matching findings and a successful timing comparison. Ontology repairs,
dropping unsupported logical axioms or weakening gates are outside B3.

Final Windows JDK 25.0.4.1 / Maven 3.9.16 `verify`: **43 tests, zero failures,
errors or skips**, successful packaging and full export/reload. Both the native
Windows installer and Linux installer verified their cached official release
archives and executables. Linux native CLI tests use WSL Ubuntu 26.04.1 / OpenJDK
25.0.5.0.3 with Ubuntu noble libpcre3 8.39-15build1 extracted locally; package
SHA-256 `62b2ea8d178a59495df00b777660377e0fb8d716be3fc552439796f77f515d43` matches
the [official Ubuntu package metadata](https://packages.ubuntu.com/noble/amd64/libpcre3/download).
The initial Linux attempt identified the missing runtime; a subsequent cold
NTFS-backed worker exceeded the former 30-second test budget. Native integration
tests now allow 120 seconds; the explicit timeout regression still uses 1 ms.
The Linux native/unit test selection passed **11 tests with zero failures,
errors or skips**. The final AnnotationProperty-declaration refinement was
additionally checked by the focused native regression on both Windows and Linux (one test each).

Local raw evidence (ignored build artifacts): `build/b3-release-verify.log`,
`build/b3-acceptance-soma.log`, `build/ci/reports/konclude-b3-acceptance/`,
`build/b3-linux-acceptance.log`, `build/b3-annotation-verify.log`,
`build/b3-annotation-linux.log`, and `build/b3-evidence/`. `actionlint` and
`git diff --check` pass. An independent subagent reviewed all implementation,
tests, installer and CI changes; its findings were fixed and re-reviewed.
The hosted GitHub job can run after this commit is pushed; it has not been
executed remotely in this local task. The full SOMA gate remains red because
of the explicit ontology/tool findings above, even when adapter tests pass.
