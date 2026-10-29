#!/usr/bin/env bash
# scripts/cleanup-generated.sh
# Remove locally generated benchmark artifacts and frontend build outputs.
# These are reproducible via scripts/benchmark.sh and npm run build.
set -euo pipefail

echo "==> Removing generated benchmark artifacts..."
rm -rf benchmark/runs benchmark/comparisons

echo "==> Removing frontend generated artifacts..."
rm -rf frontend/node_modules frontend/dist frontend/test-results frontend/playwright-report frontend/.angular

echo "==> Removing Maven wrapper artifacts..."
rm -f .mvn/wrapper/maven-wrapper.jar.new

echo "==> Removing IDE droppings..."
rm -rf .idea .vscode *.iml *.ipr *.iws .settings .project .classpath .factorypath

echo "==> Done. Run 'git status' to verify."
