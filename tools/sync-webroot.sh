#!/usr/bin/env bash
# Menyalin webroot/index.html dari zip module ke assets app (salinan cadangan).
# App tetap memuat webroot dari module yang terpasang; salinan ini hanya fallback.
#
#   tools/sync-webroot.sh <module.zip>
set -euo pipefail
MOD="${1:?pemakaian: $0 <module.zip>}"
DST="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets/webroot/index.html"
mkdir -p "$(dirname "$DST")"
unzip -p "$MOD" webroot/index.html > "$DST"
echo "OK -> $DST ($(wc -c < "$DST") byte)"
