# Gemini Power Bridge for Lenovo ZUI (China ROM)

Configures the physical Power Button long-press on Lenovo ZUI (China ROM) to launch Google Gemini seamlessly without replacing your ROM.

## How It Works

1. **Lenovo ZUI Hook**: On ZUI China ROM, setting `Settings.Global.zui_longpress_power_levoice = 1` instructs `PhoneWindowManager` to dispatch an internal broadcast/intent `com.lenovo.levoice.action.VOICE_POWER_WAKEUP` instead of the power menu.
2. **Bridge App (`com.termux.geminibridge`)**: A minimal, transparent activity registered to handle `com.lenovo.levoice.action.VOICE_POWER_WAKEUP`. When triggered, it immediately starts Google Gemini (`com.google.android.apps.bard`) and finishes itself with no visible delay.
3. **Disabling Lenovo Assistant**: The default Chinese assistant (`com.lenovo.menu_assistant`) is disabled for user 0 so there is no disambiguation popup ("Open with...").
4. **Power Menu Access**: The standard Android key chord `Power + Volume Up` is configured to `POWER_VOLUME_UP_BEHAVIOR_GLOBAL_ACTIONS` (`settings put global key_chord_power_volume_up 2`) so you can still easily access Power Off, Restart, and Emergency options.

## Quick Controls

- **Enable Gemini Power Button**:
  ```bash
  ~/projects/GeminiPowerBridge/enable.sh
  ```
- **Revert to Default Power Menu**:
  ```bash
  ~/projects/GeminiPowerBridge/disable.sh
  ```

## Key Mapping Summary

| Gesture | Action |
| :--- | :--- |
| **Long Press Power Button** | Opens Google Gemini |
| **Power + Volume Up** | Opens Power Menu (Power Off / Restart) |
| **Quick Settings Top Right** | Power Menu icon (alternative) |
