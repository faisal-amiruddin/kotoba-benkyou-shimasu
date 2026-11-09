#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
if ! command -v node >/dev/null 2>&1; then
  echo "Node.js belum terpasang. Install Node.js versi LTS dari https://nodejs.org."
  exit 1
fi
if [ ! -d node_modules/msedge-tts ]; then
  npm install
fi
node server.js
