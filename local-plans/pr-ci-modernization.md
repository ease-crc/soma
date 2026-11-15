# Modernize ontology exports and enforce local quality checks

This PR makes ontology processing reproducible and reports semantic findings separately from tool failures.

- Separate read-only sources, in-memory transformations and exports; validate local imports, reload serialized exports and record hashes/provenance.
- Integrate exports into Maven `verify` and update CI/documentation/deployment artifact paths.
- Replace legacy HermiT parsing with bounded OWLAPI workers; add checksum-pinned native Konclude. Both check consistency, unsatisfiable classes and empty object/data properties across covering source/export roots.
- Replace the public OOPS service with a pinned local build, audited dependency overrides and a tested P06 fix.
- Aggregate reports into an always-running quality gate: reasoner findings and OOPS errors block; warnings remain visible, with a reviewed baseline.
- Keep CLI parsing, suite planning and worker setup separate; centralize gate status handling and avoid redundant ontology lookups.
- Modernize runners/dependencies, pin Actions, restrict permissions and add regression/contract tests and maintenance documentation.

Current limitations: existing SOMA findings and reasoner failures keep the gate red, including unsupported Konclude constructs. Ontology repairs are separate work. Deployment still runs independently of Evaluation; publication gating and branch-protection settings remain follow-ups.

Validation: Maven package and all 44 Java tests passed, including native Konclude integration. All 19 Python tests passed across unit/OOPS and packaged-JAR integration runs; a native Konclude CLI smoke test also passed. actionlint and diff whitespace checks passed. Full SOMA reasoning and hosted CI were not rerun for this review.
