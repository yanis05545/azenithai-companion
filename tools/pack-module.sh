#!/usr/bin/env bash
# Memasukkan APK hasil build ke zip module -> app/AzenithAI-Companion.apk
#
#   tools/pack-module.sh <module.zip> [apk] [out.zip]
#
#   apk  default: app/build/outputs/apk/debug/app-debug.apk
#   out  default: <module>-with-app.zip
set -euo pipefail

MOD="${1:?pemakaian: $0 <module.zip> [apk] [out.zip]}"
APK="${2:-app/build/outputs/apk/debug/app-debug.apk}"
OUT="${3:-${MOD%.zip}-with-app.zip}"

[ -f "$MOD" ] || { echo "module zip tidak ada: $MOD" >&2; exit 1; }
[ -f "$APK" ] || { echo "APK tidak ada: $APK (build dulu: ./gradlew assembleDebug)" >&2; exit 1; }
command -v zip >/dev/null && command -v unzip >/dev/null || { echo "butuh zip & unzip" >&2; exit 1; }

OUT="$(cd "$(dirname "$OUT")" && pwd)/$(basename "$OUT")"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT

unzip -q "$MOD" -d "$TMP"
[ -f "$TMP/module.prop" ] || { echo "module.prop tidak ada di root zip: bukan zip module yang valid" >&2; exit 1; }

mkdir -p "$TMP/app"
cp "$APK" "$TMP/app/AzenithAI-Companion.apk"

rm -f "$OUT"
( cd "$TMP" && zip -qr9 "$OUT" . -x '*.DS_Store' )
echo "OK -> $OUT"
