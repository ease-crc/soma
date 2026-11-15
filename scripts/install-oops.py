#!/usr/bin/env python3
"""Build the pinned upstream API and adapter in an isolated classpath."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import shutil
import xml.etree.ElementTree as ET

from oops_common import COMMIT, DEFAULT_INSTALL, DEPENDENCY_OVERRIDES, REPOSITORY, ROOT, SOURCE_PATCHES


def run(command, **kwargs):
    subprocess.run([str(part) for part in command], check=True, **kwargs)


def build_pom(source):
    """Keep rule sources unchanged; override only dated vulnerable dependency versions."""
    namespace = "http://maven.apache.org/POM/4.0.0"
    ET.register_namespace("", namespace)
    tag = lambda name: f"{{{namespace}}}{name}"
    tree = ET.parse(source / "pom.xml")
    root = tree.getroot()
    for dependency in root.findall(f'{tag("dependencies")}/{tag("dependency")}'):
        if dependency.findtext(tag("groupId"), "").startswith("com.fasterxml.jackson"):
            dependency.find(tag("version")).text = DEPENDENCY_OVERRIDES["jackson"]
    management = ET.SubElement(ET.SubElement(root, tag("dependencyManagement")), tag("dependencies"))
    for group, artifact, version, bom in (
        ("com.fasterxml.jackson", "jackson-bom", DEPENDENCY_OVERRIDES["jackson"], True),
        ("com.google.guava", "guava", DEPENDENCY_OVERRIDES["guava"], False),
        ("org.apache.commons", "commons-lang3", DEPENDENCY_OVERRIDES["commons-lang3"], False),
    ):
        dependency = ET.SubElement(management, tag("dependency"))
        for name, value in (("groupId", group), ("artifactId", artifact), ("version", version)):
            ET.SubElement(dependency, tag(name)).text = value
        if bom:
            ET.SubElement(dependency, tag("type")).text = "pom"
            ET.SubElement(dependency, tag("scope")).text = "import"
    destination = source / "soma-pom.xml"
    tree.write(destination, encoding="utf-8", xml_declaration=True)
    return destination


def patched_p06(source, destination):
    """Fix upstream's growing-list iteration and owl:Thing index increments."""
    original = source / "src/main/java/es/upm/fi/oeg/oops/checkers/P06.java"
    text = original.read_text(encoding="utf-8")
    replacements = (
        ('for (final OntClass currentClass : superClasses) {',
         'for (int ancestorIndex = 0; ancestorIndex < superClasses.size(); ancestorIndex++) {\n'
         '                final OntClass currentClass = superClasses.get(ancestorIndex);'),
        ('                // TODO FIXME Too many i++ around here; looks fishy\n'
         '                if (classes.size() > i++) {\n'
         '                    ontoClass = classes.get(i++);\n'
         '                } else {\n'
         '                    break;\n'
         '                }', '                continue;'),
    )
    for before, after in replacements:
        if text.count(before) != 1:
            raise RuntimeError("P06 patch preimage does not match pinned source")
        text = text.replace(before, after)
    directory = destination / "patched-src"
    directory.mkdir(exist_ok=True)
    target = directory / "P06.java"
    target.write_text(text, encoding="utf-8")
    return target


def install(destination, java_home):
    destination = destination.resolve()
    destination.mkdir(parents=True, exist_ok=True)
    marker = destination / "installation.json"
    marker.unlink(missing_ok=True)
    source = destination / "source"
    if not source.exists():
        run(["git", "clone", REPOSITORY, source])
    git = ["git", "-c", f"safe.directory={source.as_posix()}", "-C", source]
    dirty = subprocess.check_output([str(p) for p in [*git, "status", "--porcelain"]], text=True)
    if any(line != "?? soma-pom.xml" for line in dirty.splitlines()):
        raise RuntimeError("OOPS checkout has local changes; use a fresh destination")
    run([*git, "checkout", "--detach", COMMIT])
    for generated in (source / "target", destination / "adapter"):
        if generated.exists():
            if generated.is_symlink() or not generated.resolve().is_relative_to(destination):
                raise RuntimeError(f"Unsafe generated directory: {generated}")
            shutil.rmtree(generated)
    env = dict(os.environ, JAVA_HOME=str(java_home), MAVEN_USER_HOME=str(ROOT / "build/maven-home"))
    wrapper = ROOT / ("mvnw.cmd" if os.name == "nt" else "mvnw")
    command = [wrapper] if os.name == "nt" else ["bash", wrapper]
    run([*command, "-B", "-ntp", "-f", build_pom(source), "compile",
         "org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies",
         "-DincludeScope=runtime", "-DoutputDirectory=target/lib"], env=env)
    adapter = destination / "adapter"
    adapter.mkdir(exist_ok=True)
    classpath = os.pathsep.join(map(str, [source / "target/classes", source / "target/lib/*"]))
    run([java_home / "bin/javac", "--release", "17", "-cp", classpath, "-d", adapter,
         ROOT / "scripts/oops/OopsWorker.java",
         patched_p06(source, destination),
         ROOT / "scripts/java/src/main/java/main/GuardingXmlCatalogIriMapper.java",
         ROOT / "scripts/java/src/main/java/main/reasoning/LocalReasoningOntology.java"])
    files = sorted(p for directory in (adapter, source / "target/classes", source / "target/lib")
                   for p in directory.rglob("*") if p.is_file())
    manifest = {p.relative_to(destination).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
    marker.write_text(json.dumps({"repository": REPOSITORY, "commit": COMMIT,
                                "dependency_overrides": DEPENDENCY_OVERRIDES,
                                "source_patches": SOURCE_PATCHES, "files": manifest},
                                indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(destination)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--destination", type=Path, default=DEFAULT_INSTALL)
    parser.add_argument("--java-home", type=Path, default=os.environ.get("JAVA_HOME"), required=not bool(os.environ.get("JAVA_HOME")))
    options = parser.parse_args()
    install(options.destination, options.java_home.resolve())
