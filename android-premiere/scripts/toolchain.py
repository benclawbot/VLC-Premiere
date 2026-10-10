"""Resolve local Java and Apktool tools without machine-specific paths."""
from pathlib import Path
import os
import shutil
import sys


ROOT = Path(__file__).resolve().parents[2]
TOOLS = ROOT / 'android-premiere' / 'tools'


def java_tool(name):
    candidates = []
    if os.environ.get('JAVA_HOME'):
        candidates.append(Path(os.environ['JAVA_HOME']) / 'bin' / name)
    candidates.append(TOOLS / 'jdk' / 'bin' / name)
    found = shutil.which(name)
    if found:
        candidates.append(Path(found))
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise SystemExit(f"Java {name} not found. Install Java 17 and set JAVA_HOME, or place a JDK at {TOOLS / 'jdk'}.")


def apktool_jar():
    configured = os.environ.get('APKTOOL_JAR')
    candidates = [Path(configured)] if configured else []
    candidates.append(TOOLS / 'apktool.jar')
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise SystemExit(f"Apktool 3.0.3 jar not found. Set APKTOOL_JAR to its path (expected by default at {TOOLS / 'apktool.jar'}).")


if __name__ == '__main__':
    if len(sys.argv) != 2 or sys.argv[1] not in {'java', 'javac', 'keytool', 'apktool'}:
        raise SystemExit('Usage: toolchain.py java|javac|keytool|apktool')
    print(apktool_jar() if sys.argv[1] == 'apktool' else java_tool(sys.argv[1]))
