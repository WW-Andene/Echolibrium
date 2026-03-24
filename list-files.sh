#!/usr/bin/env bash
# Lists the first 100 file-system entries in the project, excluding common generated/dependency directories.
find . -not -path '*/node_modules/*' -not -path '*/.git/*' -not -path '*/dist/*' -not -path '*/.expo/*' | sort | head -100
