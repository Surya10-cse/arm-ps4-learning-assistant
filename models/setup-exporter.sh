#!/usr/bin/env bash
# Run on Ubuntu 22.04/24.04 or WSL2, Python 3.10/3.11, ample RAM/disk.
set -euo pipefail
destination="${1:?Usage: setup-exporter.sh /absolute/new/exporter-directory}"
git clone --branch v1.0.0 --depth 1 --recurse-submodules https://github.com/pytorch/executorch.git "$destination"
cd "$destination"
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
./install_executorch.sh
bash examples/models/llama/install_requirements.sh
python -m pip install -e extension/llm/tokenizers/
git rev-parse HEAD > exporter-commit.txt
python -m pip freeze > exporter-environment.txt
printf '%s\n' 'Exporter ready. Keep this environment and its freeze file for reproducibility.'
