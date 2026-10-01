#!/usr/bin/env python3
import subprocess
import os
import shutil
import zipfile

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
ANDROID_JAR = "/data/data/com.termux/files/home/.android-sdk/platforms/android-33/android.jar"
BIN_DIR = os.path.join(BASE_DIR, "bin")
OBJ_DIR = os.path.join(BASE_DIR, "obj")
SRC_FILE = os.path.join(BASE_DIR, "src/com/termux/geminibridge/MainActivity.java")
MANIFEST = os.path.join(BASE_DIR, "AndroidManifest.xml")
KEYSTORE = os.path.join(BASE_DIR, "debug.keystore")

os.makedirs(BIN_DIR, exist_ok=True)
os.makedirs(OBJ_DIR, exist_ok=True)

# 1. Compile resources with aapt2
base_apk = os.path.join(BIN_DIR, "app.base.apk")
subprocess.check_call(["aapt2", "link", "-I", ANDROID_JAR, "--manifest", MANIFEST, "-o", base_apk])

# 2. Compile Java source
subprocess.check_call(["javac", "-cp", ANDROID_JAR, "-d", OBJ_DIR, SRC_FILE])

# 3. Dex with d8
subprocess.check_call(["d8", "--lib", ANDROID_JAR, "--output", BIN_DIR, os.path.join(OBJ_DIR, "com/termux/geminibridge/MainActivity.class")])

# 4. Package unsigned APK
unsigned_apk = os.path.join(BIN_DIR, "app.unsigned.apk")
dex_file = os.path.join(BIN_DIR, "classes.dex")
with zipfile.ZipFile(base_apk, "r") as zin:
    with zipfile.ZipFile(unsigned_apk, "w") as zout:
        for item in zin.infolist():
            zout.writestr(item, zin.read(item.filename))
        zout.write(dex_file, "classes.dex")

# 5. Sign APK
if not os.path.exists(KEYSTORE):
    subprocess.check_call([
        "keytool", "-genkey", "-v",
        "-keystore", KEYSTORE,
        "-storepass", "android",
        "-alias", "androiddebugkey",
        "-keypass", "android",
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", "10000",
        "-dname", "CN=Android Debug,O=Android,C=US"
    ])

signed_apk = os.path.join(BIN_DIR, "gemini-bridge.apk")
shutil.copyfile(unsigned_apk, signed_apk)
subprocess.check_call([
    "apksigner", "sign",
    "--ks", KEYSTORE,
    "--ks-pass", "pass:android",
    "--ks-key-alias", "androiddebugkey",
    "--key-pass", "pass:android",
    signed_apk
])

# 6. Verify signature
subprocess.check_call(["apksigner", "verify", signed_apk])
print("[+] gemini-bridge.apk built and signed successfully!")
