# KIM Live Studio

**KIM Live Studio** is a high-performance, studio-grade Android live streaming application engineered for mobile creators, streamers, and presenters. Broadcast your screen, camera overlay, and microphone audio directly to any standard RTMP or RTMPS server with zero third-party intermediaries.

---

## 🚀 Key Features

- **Direct RTMP & RTMPS Protocol Ingest**: Streams directly to YouTube Live, Twitch, Kick, Facebook Live, TikTok, or custom RTMP/RTMPS endpoints (standard port 1935 and TLS-encrypted port 443).
- **Floating Facecam PiP Overlay**:
  - Movable screen overlay powered by Android Camera2.
  - **In-flight 90° Rotation** (`↻`) cycling across all 4 orientations (0° Portrait, 90° Landscape, 180° Inverted, 270° Reverse Landscape) while preserving the window aspect ratio.
  - **Camera Flipping** (`⇄`) between front and rear cameras.
  - **Window Sizing** (`⛶`) cycling through Compact, Studio Medium, and Large views.
  - **Broadcast Tally Indicator** (`●`) glowing Studio Cyan in standby and Studio Ruby (`● LIVE`) during broadcast.
- **Dynamic Stream Orientation**: Supports 16:9 Landscape, 9:16 Portrait, and Auto Canvas stream orientations.
- **Pure Hardware Encoding**: Powered by Android `MediaCodec` H.264/AVC hardware acceleration for smooth 60 FPS, 30 FPS, or 24 FPS frame rates with minimal CPU overhead.
- **Studio Telemetry HUD**: Real-time readout of live uptime, output bitrate (kbps), actual FPS cadence, and dropped frame counts.
- **Broadcast Audio & VU Meter**: Low-latency microphone capture with a 5-segment tactical LED audio VU meter.
- **Bespoke Broadcast Aesthetics**: Control-room dark mode with tactical obsidian surfaces, studio cyan accents, and live ruby highlights.

---

## 🛠️ Architecture & Tech Stack

- **Platform**: Native Android (`android-33`)
- **Language**: Pure Java + Android Framework (zero bloated third-party dependencies)
- **Video Capture**: `MediaProjection` API + `Camera2` Subsystem
- **Video Encoding**: Android `MediaCodec` (H.264 Baseline/High Profile)
- **Audio Encoding**: `AudioRecord` + `MediaCodec` AAC-LC
- **Muxer**: Pure native Java RTMP/FLV client with direct socket connection

---

## 📦 Building from Source

```bash
# Compile and package debug APK
gradle assembleDebug

# Install directly to connected device
gradle installDebug
```

Output APK will be generated at:
`build/outputs/KimLiveStudio-debug.apk`

---

## 🔒 Privacy & Safety Guarantee

- **Direct Ingest**: Video and audio packets flow straight to your designated RTMP server with zero cloud proxies.
- **Zero Tracking**: No user tracking, analytics, telemetry collection, or background harvesting.
- **Secure On-Device Storage**: Stream keys and broadcast configuration remain strictly sandboxed on your device.

---

## 📄 License

Proprietary &copy; AI Studio / Eliakim Rosil. All rights reserved.
