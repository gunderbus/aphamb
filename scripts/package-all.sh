#!/bin/sh
set -eu

if ! command -v gh >/dev/null 2>&1; then
    echo "GitHub CLI (gh) is required for one-command cross-platform packaging."
    echo "Install gh, authenticate with 'gh auth login', then run this script again."
    exit 1
fi

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)

cd "$REPO_ROOT"

gh workflow run package-all.yml
echo "Triggered GitHub Actions workflow 'package-all.yml'."
echo "Use 'gh run watch' to follow progress and download the .exe, .dmg, and .flatpak artifacts when it finishes."
