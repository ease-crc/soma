# Java dependency review — 2026-11-13

Baseline: upstream/master `5d27187e587a7b91e82361e3c52b05eb5d933dd3`, branch
`ci/modernization`. Upstream SHA, open PRs #322/#323 and recent failed CI runs
were rechecked before implementation. No remote changes were made.

## Selected toolchain and direct dependencies

JDK 25 LTS was selected after compiling and running OWLAPI/HermiT tests with
JetBrains Runtime 25.0.4.1. Maven 3.9.16 is pinned by the official only-script
wrapper 3.3.4 and its distribution SHA-256 (computed after verification against
Central's SHA-512). Enforcer checks both Java and Maven versions.

Current stable Maven Central versions were checked during this change:

| Dependency | Version | Use |
| --- | --- | --- |
| OWLAPI distribution | 5.5.1 | Ontology loading and processing |
| Protege XML catalog | 1.0.5 | Existing catalog mapper |
| SnakeYAML | 2.7 | Safe, plain-data YAML configuration |
| SLF4J API/provider/bridges | 2.0.20 | Logging; 2.1 alpha intentionally excluded |
| JUnit Jupiter | 6.1.3 | Test only |
| HermiT | 1.4.5.519 | Application dependency for the B2 OWLAPI adapter |

Spring Boot, its parent/plugin, Spring DI, Logback and direct JetBrains annotations
were removed. Guava is needed transitively by OWLAPI, managed at 33.8.0-jre
(newer than PR #323's 33.7.1). Commons Lang is no longer a direct dependency;
3.22.0 is managed should it appear transitively (newer than PR #322's 3.18.0).
The old Log4j 1.2 dependency from XML catalog is excluded and replaced with
`log4j-over-slf4j`. Maven lifecycle plugins have explicit stable versions.

## Security review

Captured before/after dependency trees distinguish direct and transitive edges.
An OSV batch query covered all 94 resolved application and test artifacts.
Initial findings in transitive Jackson 2.16.1 and test-only Mime4j 0.7.2 were
resolved with the current Jackson 2.x BOM 2.23.0 and Mime4j 0.8.15. Jackson 3
uses different coordinates/APIs and cannot replace OWLAPI's Jackson 2 imports.
The final query returned no known advisories for these 94 coordinates/versions.
This is a dated advisory lookup, not proof of absence of vulnerabilities; it
excludes Maven plugin internals, the JDK and external CLI reasoners.

Other transitives follow the selected OWLAPI/HermiT releases; wholesale major
upgrades of their internal APIs are not implied by the direct dependency update.
B2 promotes HermiT and its Axiom transitives from test to application scope,
without changing their reviewed versions. Compatibility is checked by the B1
semantic fixtures and the B2 subprocess tests; full SOMA classification is a
separate suite run with per-input reports. No outstanding OSV finding is suppressed.

Local evidence (intentionally ignored): `build/local-plans/a2-before.txt`,
`a2-after.txt`, `a2-osv.json`, `a2-build.log`, `a2-final-build.log`.

Reproduce the dependency inventory from the repository root:

```sh
bash ./mvnw -B -ntp -f scripts/java/pom.xml org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree
```

A3 subsequently replaced the Protege catalog implementation with a strict JDK
XML parser, removing `org.protege.xmlcatalog` and its `log4j-over-slf4j` bridge.
Jackson Databind is now declared directly for manifest/report JSON; its version
is unchanged under the already reviewed Jackson BOM. The Maven verify lifecycle
uses the pinned `exec-maven-plugin` 3.5.0 to launch the packaged CLI. These plugin
internals remain outside the application advisory scan described above.

A4 still owns workflow security and obsolete actions. Hosted CI has not been
triggered here.
