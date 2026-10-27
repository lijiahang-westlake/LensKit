#!/bin/bash
# LensKit APK 构建脚本（无 Gradle：aapt2 + javac + d8 + zipalign + apksigner）
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
# 可用环境变量覆盖：LENSKIT_BUILD（中间产物）/ ANDROID_SDK_ROOT / JAVA_HOME
BUILD="${LENSKIT_BUILD:-C:/Users/Public/lenskit_build}"
SDK="${ANDROID_SDK_ROOT:-$BUILD/android-sdk}"
if [ -z "$JAVA_HOME" ]; then JAVA_HOME="$BUILD/jdk"; fi
BT="$SDK/build-tools/34.0.0"
PLAT="$SDK/platforms/android-34/android.jar"
SRC="$ROOT/app"
OUT="$BUILD/out"

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

if [ ! -f "$BT/aapt2.exe" ]; then
  echo "找不到 Android build-tools：$BT" >&2
  echo "请设置 ANDROID_SDK_ROOT 指向已安装 build-tools/34.0.0 与 platforms/android-34 的 SDK 目录" >&2
  exit 1
fi

echo "== [0/7] 同步主源文件到 assets + 复制到 ASCII 路径（规避中文路径坑） =="
MASTER="$ROOT/../lenskit/index.html"
if [ -f "$MASTER" ]; then
  cp "$MASTER" "$SRC/assets/index.html"
  echo "   已同步 lenskit/index.html -> app/assets/index.html"
fi
BUILDSRC="$BUILD/src"
rm -rf "$BUILDSRC"
mkdir -p "$BUILDSRC"
cp -r "$SRC" "$BUILDSRC/app"
SRC="$BUILDSRC/app"

echo "== [1/7] 清理并准备目录 =="
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "== [2/7] aapt2 编译资源 =="
"$BT/aapt2.exe" compile --dir "$SRC/res" -o "$OUT/res.zip"

echo "== [3/7] aapt2 链接 =="
"$BT/aapt2.exe" link \
  -o "$OUT/app.unaligned.apk" \
  -I "$PLAT" \
  --manifest "$SRC/AndroidManifest.xml" \
  -A "$SRC/assets" \
  -R "$OUT/res.zip" \
  --java "$OUT/gen" \
  --min-sdk-version 29 \
  --target-sdk-version 34 \
  --auto-add-overlay

echo "== [4/7] javac 编译 =="
find "$SRC/java" "$OUT/gen" -name "*.java" > "$OUT/sources.txt"
javac -encoding UTF-8 -source 1.8 -target 1.8 -Xlint:-options \
  -classpath "$PLAT" -d "$OUT/classes" @"$OUT/sources.txt"

echo "== [5/7] d8 dex =="
find "$OUT/classes" -name "*.class" > "$OUT/classes.txt"
"$BT/d8.bat" --lib "$PLAT" --release --min-api 29 \
  --output "$OUT/dex" $(cat "$OUT/classes.txt")

echo "== [6/7] 合入 classes.dex + zipalign =="
python "$ROOT/tools/pack.py" "$OUT/app.unaligned.apk" "$OUT/app.withdex.apk" "$OUT/dex/classes.dex"
"$BT/zipalign.exe" -f -p 4 "$OUT/app.withdex.apk" "$OUT/app.aligned.apk"

echo "== [7/7] 签名 =="
KS="$BUILD/debug.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias lenskit \
    -storepass lenskit123 -keypass lenskit123 \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=LensKit, OU=Dev, O=Lijiahang, C=CN"
fi
"$BT/apksigner.bat" sign \
  --ks "$KS" --ks-pass pass:lenskit123 --key-pass pass:lenskit123 \
  --out "$OUT/LensKit.apk" "$OUT/app.aligned.apk"

"$BT/apksigner.bat" verify --print-certs "$OUT/LensKit.apk" | head -5
cp "$OUT/LensKit.apk" "$ROOT/LensKit.apk"
echo "== 完成: $ROOT/LensKit.apk =="
