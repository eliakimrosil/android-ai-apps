#!/data/data/com.termux/files/usr/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK="$DIR/bin/gemini-bridge.apk"

echo "[1/4] Ensuring Gemini Bridge is installed..."
if ! adb -s 127.0.0.1:5555 shell pm list packages | grep -q "com.termux.geminibridge"; then
    adb -s 127.0.0.1:5555 install -r "$APK"
fi

echo "[2/4] Disabling Lenovo Chinese menu assistant..."
adb -s 127.0.0.1:5555 shell pm disable-user --user 0 com.lenovo.menu_assistant

echo "[3/4] Routing long press power button to voice wakeup..."
adb -s 127.0.0.1:5555 shell settings put global zui_longpress_power_levoice 1

echo "[4/4] Setting Power + Volume Up chord to Power Menu (Shutdown / Restart)..."
adb -s 127.0.0.1:5555 shell settings put global key_chord_power_volume_up 2

echo ""
echo "=== Gemini Power Long-Press Enabled Successfully ==="
echo "• Long press Power Button -> Launches Google Gemini"
echo "• Power Button + Volume Up -> Opens Power Menu (Restart / Shut down)"
