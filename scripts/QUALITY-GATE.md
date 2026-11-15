# Ontology quality gate

`Ontology quality gate` is the aggregate Evaluation check. It runs after workflow
lint, compilation, HermiT and local OOPS, including when dependencies
fail or are skipped. Configure this check as **required** in the repository's
branch rules to prevent merging a broken PR. Local workflow changes do not edit
GitHub branch protection. Deployment currently runs independently of Evaluation.

HermiT is the sole required reasoner. Konclude is excluded from CI and the gate;
its adapter remains available for local experiments. See
[the supported-input limits](java/KONCLUDE.md). Known semantic findings and tool
errors remain blocking; there are no exceptions for known unsatisfiable classes.

## Contract and outputs

The gate reads artifacts from this workflow run, checks the suite protocol,
source/export coverage, public roots, per-root results and suite exit codes,
and requires every selected root's consistency, named-class coherence, empty
ObjectProperty and empty DataProperty checks. Inconsistent inputs stop at
consistency. All entity lists and additional reasoner quality findings block.
Missing, malformed, contradictory or incomplete reports fail closed.

- Exit 0 / `pass`: complete mandatory checks, no blocking findings.
- Exit 1 / `findings`: a completed semantic check found a defect.
- Exit 2 / `tool_error`: infrastructure, unsupported input, timeout, missing
  output, failed tests or another incomplete check. Semantic findings from other
  complete checks remain in the report when a technical error takes precedence.

Producer jobs expose technical step conclusions separately: failed adapter
tests, gate tests, annotations or uploads cannot be explained away by a semantic
reasoner failure. A failed/cancelled/skipped build or artifact download also
blocks the gate. No job uses `continue-on-error`.

`quality-gate.json` normalizes engine, input, status, technical errors and
findings. `summary.md` is also appended to the GitHub step summary. Both are
uploaded as `Ontology Quality Gate` even on failure. Raw per-root reports,
worker logs, coverage/input identities and OOPS installation identity remain in
the existing engine artifacts, also uploaded on failure. The aggregate job
does not rerun expensive reasoning.

## OOPS policy

OOPS checks exported `SOMA-HOME.owl` with its complete local import closure,
using all 20 selected rules. It is a complementary heuristic
check; reasoner checks cover all source/export roots. The project severity policy
is explicit and independent of upstream importance labels:

| Severity | Rules | Reason |
| --- | --- | --- |
| Error | P05, P06, P19, P25, P26, P27, P28, P29 | Wrong inverses, cycles, incompatible domains/ranges and inconsistent property characteristics/relations require repair. |
| Warning | P02, P03, P04, P07, P08, P10, P11, P12, P13, P20, P21, P22 | Naming, documentation, connectivity, missing schema constraints and modeling heuristics require human review. |

Subcodes such as P08-A and P13-S inherit their parent rule's severity. Unknown
rules, missing completed rules, unmet preconditions and technical OOPS failures
are tool errors. There is no public-service fallback. `oops.py` remains the raw
runner (0 for completed checks, 2 for technical failures); `oops-github.py`
renders matching error/warning annotations, while the aggregate gate enforces
blocking error findings.

[warning-baseline.json](oops/warning-baseline.json) records the 2026-11-15 local
SOMA-HOME run at the pinned OOPS commit, including input SHA-256 identities and
441 warning records: P04 1, P08-A 333, P11 19, P12 1, P13-N 33, P13-S 49,
P20 4, P22 1. A warning is known only if its code and complete resource set
match. New warnings are visible as unbaselined; warnings do not block. The same
run contains **24 blocking errors** (P05 4, P19 2, P25 9, P26 9); these are not
baselined or waived. Edit the baseline only after reviewing a new local report;
CI never updates it automatically.

## Local verification

```sh
python -m unittest discover -s scripts/tests -v
# After Maven package / local OOPS installation; JDK 25 must be on PATH:
QUALITY_GATE_JAR=scripts/java/target/IsDefinedInAdder-1.0-SNAPSHOT.jar \
OOPS_INTEGRATION=1 python -m unittest discover -s scripts/tests -v
# Download this run's artifacts into separate directories named after artifacts:
python scripts/quality_gate.py --artifacts=build/quality-input --output=build/quality-gate
```

The integration test runs the real HermiT CLI on a consistent document, global
inconsistency, an unsatisfiable class, an empty ObjectProperty, an empty
DataProperty and a missing import. It passes those actual worker results through
the aggregate gate and verifies exit 0/1/2. Unit tests cover report corruption,
missing coverage, every policy rule, warnings, technical step failures despite
semantic findings and the executable CI command. The HermiT CI job always enables
this integration test. A hosted PR run can validate the full GitHub orchestration
after pushing; local checks do not represent a hosted run.
