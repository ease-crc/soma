"""Pinned OOPS source identity and local rule selection."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPOSITORY = "https://github.com/oeg-upm/OOPS.git"
COMMIT = "11572ea6b31cba29dd3f8a443e24311652f059de"
DEPENDENCY_OVERRIDES = {"jackson": "2.23.0", "guava": "33.8.0-jre", "commons-lang3": "3.22.0"}
SOURCE_PATCHES = ["P06-traversal-v1"]
# Previous selection with duplicate P25 removed; no replacement rules added.
RULES = (2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 19, 20, 21, 22, 25, 26, 27, 28, 29)
DEFAULT_INSTALL = ROOT / "build/tools/oops"
