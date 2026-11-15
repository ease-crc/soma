#!/usr/bin/env python3
"""Install the official, checksum-pinned native Konclude release into a local directory."""
import argparse
import hashlib
import json
from pathlib import Path
import platform
import urllib.request
import zipfile

VERSION = "v0.7.0-1138"
RELEASES = {
    "windows": (
        "Windows-x64-MSVC-Dynamic-Qt5.15.2",
        "52ea75ebac2e996cb6bdb256271d1a772ec7b2ac1cce99c6da13a7d967698f98",
        "0d9bf7dc5b2d6e35e873c1b4fe1ef169b263fb18f6fe7183225ac65b17e31ed7",
        "Konclude.exe",
    ),
    "linux": (
        "Linux-x64-GCC-Static-Qt5.12.10",
        "37216c18f45a80165aec1e76aeefd62d933d6a3f8f134c5aca0707c676fda76e",
        "5484f16dcff71486a5deed9cf9cea8a0f7febf115aaa6915ad2e8c1cf16965e3",
        "Konclude",
    ),
}


def install(destination, system):
    if platform.machine().lower() not in ("amd64", "x86_64"):
        raise ValueError("Only the pinned x86-64 distributions are supported")
    suffix, archive_hash, binary_hash, executable = RELEASES[system]
    root = f"Konclude-{VERSION}-{suffix}"
    url = f"https://github.com/konclude/Konclude/releases/download/{VERSION}/{root}.zip"
    destination = destination.resolve()
    destination.mkdir(parents=True, exist_ok=True)
    archive = destination / f"{root}.zip"
    if not archive.exists():
        # Download to a temporary filename: an interrupted download must not become a cached release.
        partial = archive.with_suffix(".download")
        with urllib.request.urlopen(url, timeout=120) as response, partial.open("wb") as target:
            while chunk := response.read(1024 * 1024):
                target.write(chunk)
        if hashlib.sha256(partial.read_bytes()).hexdigest() != archive_hash:
            raise ValueError("Konclude download checksum mismatch")
        partial.replace(archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != archive_hash:
        raise ValueError("Konclude archive checksum mismatch")
    with zipfile.ZipFile(archive) as release:
        for member in release.infolist():
            target = (destination / member.filename).resolve()
            if not target.is_relative_to(destination):
                raise ValueError("Archive member escapes destination")
        release.extractall(destination)
    binary = destination / root / "Binaries" / executable
    if hashlib.sha256(binary.read_bytes()).hexdigest() != binary_hash:
        raise ValueError("Konclude executable checksum mismatch")
    if system == "linux":
        binary.chmod(0o755)
    (destination / "installation.json").write_text(json.dumps({
        "version": VERSION, "url": url, "archiveSha256": archive_hash,
        "binary": str(binary), "binarySha256": binary_hash,
    }, indent=2), encoding="utf-8")
    return binary


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path("build/tools/konclude"))
    parser.add_argument("--platform", choices=RELEASES, default=platform.system().lower())
    args = parser.parse_args()
    if args.platform not in RELEASES:
        parser.error("Only Windows and Linux native releases are pinned")
    print(install(args.output, args.platform))
