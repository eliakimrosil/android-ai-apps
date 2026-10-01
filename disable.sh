#!/data/data/com.termux/files/usr/bin/bash
set -e

echo "[1/3] Restoring default ZUI power long-press behavior (Power Menu)..."
adb -s 127.0.0.1:5555 shell settings put global zui_longpress_power_levoice 2

echo "[2/3] Restoring key chord (Power + Volume Up) to default..."
adb -s 127.0.0.1:5555 shell settings put global key_chord_power_volume_up 1

echo "[3/3] Re-enabling Lenovo menu assistant..."
adb -s 127.0.0.1:5555 shell pm enable --user 0 com.lenovo.menu_assistant

echo ""
echo "=== Restored Default Power Button Behavior ==="
echo "• Long press Power Button -> Opens default Power Menu"
