# CI foundation

`Evaluation` runs on every push and pull request, including documentation-only
changes. `Workflow lint` and `Compile SOMA` keep stable check names. There are no
path filters that could leave required checks pending. Configure required checks
in repository settings separately; these workflows do not change branch rules.

All third-party Actions use full commit SHAs with release comments. Dependabot
checks Actions, the Java Maven project and the Python requirements weekly.
Review updates before merging, including runtime and runner requirements.
The Maven Wrapper pins and verifies Maven independently of the runner installation.
The actionlint binary is pinned to v1.7.12 and its SHA-256; update its URL and
checksum together in `evaluation.yml`. Run actionlint locally before committing
workflow changes. The hosted lint job also uses the runner's ShellCheck.

Jobs use Ubuntu 24.04, JDK 25 and Python 3.12 directly, with explicit timeouts.
HermiT uses the pinned application dependency in `scripts/java/pom.xml` through
the OWLAPI adapter. The HermiT job validates all source/export documents and
reasons over the main roots plus automatically detected uncovered ontologies.
The current corpus needs five roots per set, with a 600-second timeout and
2048 MiB heap per worker. `coverage.json` makes the module assignment explicit. All per-input JSON
reports and worker logs are uploaded even when findings or tool errors fail the
job. HermiT is the sole CI reasoner. Konclude is available only for local
experiments; CI does not install or invoke it, collect its artifacts or require
its reports. CI Maven commands exclude its native and unit tests with
`-Dtest=!Konclude*`. See [the supported-input limits](../scripts/java/KONCLUDE.md).
Python packages are installed without sudo. Sudo installs TeX packages on
documentation runners.

Workflow permissions default to `contents: read`. PR jobs receive no secrets,
use `pull_request`, and never use `pull_request_target`. Every checkout disables
persisted credentials. No dependency or build caches are restored or saved:
PRs and trusted publication jobs cannot exchange mutable build caches. Artifacts
are transferred by exact name within the same run; documentation additionally
specifies the successful upstream run ID and checks out that run's head SHA.
Artifacts expire after seven days; required uploads reject missing files.
Diagnostic reports are uploaded even when preceding checks fail.

Evaluation runs cancel older runs for the same PR or ref. Documentation runs
cancel older runs for the same branch. Deployment runs serialize under one group
and never cancel an ongoing publication. The deployment build has read access;
only its final publication job has `contents: write`, using `GITHUB_TOKEN` rather
than a personal access token. Release tags pass through environment variables and
are restricted to a single safe path component before reaching any output path.
Publication preserves existing files and version archives (`clean: false`).

The always-running `Ontology quality gate` combines HermiT and local OOPS
reports with an explicit error/warning policy. Make that check required in branch
rules; semantic findings and technical failures remain blocking. See
[the gate contract and verification](../scripts/QUALITY-GATE.md).

Deployment still builds independently of Evaluation. Publication gating on all
semantic checks, release trust rules and replacement of legacy external
OntoMetrics remain outstanding.
