#!/bin/bash
set -e
cd "$(dirname "$0")"

SDK="$(pwd)/../../task-island/sdk36/android-36/android.jar"
OUT="build"
rm -rf "$OUT" gen
mkdir -p "$OUT" gen

aapt2 compile --dir res -o "$OUT/res.zip"
aapt2 link -o "$OUT/base.apk" -I "$SDK" --manifest AndroidManifest.xml --java gen --auto-add-overlay "$OUT/res.zip"

find src gen -name '*.java' > "$OUT/sources.txt"
javac -source 11 -target 11 -classpath "$SDK" -d "$OUT/classes" @"$OUT/sources.txt"
jar cf "$OUT/classes.jar" -C "$OUT/classes" .

mkdir -p "$OUT/dex"
d8 --release --lib "$SDK" --min-api 29 --output "$OUT/dex" "$OUT/classes.jar"

cd "$OUT"
zip -q -j base.apk dex/classes.dex
cd ..

apksigner sign --ks island.keystore --ks-key-alias island \
  --ks-pass pass:island123 --key-pass pass:island123 \
  --out hud-left.apk "$OUT/base.apk"

echo "built: $(pwd)/hud-left.apk"
