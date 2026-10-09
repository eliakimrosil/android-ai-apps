<p align="center">
  <img src="assets/banner.svg" alt="KIM Android Apps Banner" width="100%">
</p>

<p align="center">
  <a href="https://android.com"><img src="https://img.shields.io/badge/Android-13%2B%20(API%2033)-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 13+"></a>
  <a href="docs/ARCHITECTURE.md"><img src="https://img.shields.io/badge/Architecture-Pure%20Native%20Java%2FXML-007396?style=for-the-badge&logo=openjdk&logoColor=white" alt="Pure Java/XML"></a>
  <a href="https://eliakimrosil.github.io/kim-android-apps/"><img src="https://img.shields.io/badge/Apps-28%20Native%20Utilities-00E5FF?style=for-the-badge" alt="28 Native Apps"></a>
  <a href="SECURITY.md"><img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20First-10B981?style=for-the-badge&logo=shield&logoColor=white" alt="100% Offline"></a>
  <a href="SECURITY.md"><img src="https://img.shields.io/badge/Trackers-0%20(Zero)-A855F7?style=for-the-badge" alt="Zero Trackers"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-38BDF8?style=for-the-badge" alt="MIT License"></a>
  <a href="https://eliakimrosil.github.io/kim-android-apps/"><img src="https://img.shields.io/badge/Web%20Store-Live%20Showcase-FF6B6B?style=for-the-badge&logo=google-chrome&logoColor=white" alt="Live Web Showcase"></a>
</p>

<p align="center">
  <b><a href="https://eliakimrosil.github.io/kim-android-apps/">🌐 Web App Store</a></b> •
  <b><a href="https://github.com/eliakimrosil/kim-android-apps/releases">📦 Releases &amp; Direct APKs</a></b> •
  <b><a href="docs/ARCHITECTURE.md">📐 Architecture</a></b> •
  <b><a href="docs/BESPOKE_DESIGN_GUIDELINES.md">🎨 Bespoke Design</a></b> •
  <b><a href="docs/BRANCH_WORKFLOW.md">🌿 Branch Workflow</a></b> •
  <b><a href="CONTRIBUTING.md">🤝 Contributing</a></b> •
  <b><a href="SECURITY.md">🔒 Security Charter</a></b>
</p>

---

## ⚡ Executive Summary

**KIM Android Apps** is an ecosystem of **28+ bespoke, privacy-first, ultra-lightweight Android applications**. Every utility is built natively using pure Android SDK 33 framework components (**Java + XML**) without heavyweight third-party runtime bloat, analytics spyware, or cloud lock-in.

Each app is engineered directly on physical Android ARM64 hardware (Lenovo Legion Y70, Qualcomm Snapdragon 8+ Gen 1) featuring **domain-specific UI craftsmanship**, **sub-second cold start times**, **ultra-compact APK binaries (< 2 MB)**, and **100% offline security**.

---

## 🏛️ Core Engineering Pillars

```
+---------------------------------------------------------------------------------------------------+
|                                  KIM ANDROID APPS PILLARS                                         |
+---------------------------------+---------------------------------+-------------------------------+
|     🎨 BESPOKE CRAFTSMANSHIP    |      ⚡ PURE NATIVE SPEED       |    🛡️ ABSOLUTE PRIVACY       |
|  Tailored domain aesthetics,    |  Pure Java + XML, < 2 MB APKs,  |  100% offline-first, zero     |
|  high-contrast Day/Night modes, |  < 80 ms launch times, zero     |  trackers, zero ads, zero     |
|  adaptive Material You icons.   |  framework bloat, low battery.  |  cloud telemetry harvesting.  |
+---------------------------------+---------------------------------+-------------------------------+
```

1. **Domain-Tailored Bespoke Craftsmanship**: No cookie-cutter Material templates. Broadcast utilities adopt control-room obsidian and live ruby tally glows; hardware monitors sport tactical HUD gauges; culinary sentinels feature oversized, high-contrast rings designed for messy cooking hands.
2. **Adaptive Vector Icons & Material You**: Every application features bespoke multi-layered vector adaptive icons with full support for Android 13+ Material You dynamic wallpaper theming (`ic_launcher_monochrome.xml`).
3. **Pure Native Performance**: No React Native, Flutter, or Jetpack Compose overhead. Pure Android SDK components ensure zero memory thrashing, smooth 60/120 FPS animations, and instantaneous launches.
4. **Zero-Data Privacy Charter**: Zero advertising SDKs, zero crash beacons, zero telemetry. Offline tools never request or use internet permissions.

---

## 📱 Master Applications Catalog

Explore the full directory of applications across 7 core functional domains:

### 🎥 1. Broadcasting, Video & Audio

| Icon | Application | Version | Domain & Features | Downloads & Links |
| :---: | :--- | :---: | :--- | :--- |
| <img src="assets/icons/kim-live-studio.svg" width="48" height="48"> | **KIM Live Studio**<br>`kim-live-studio` | `v1.2.0` | **Studio-Grade Mobile Live Streaming**<br>• Hardware `MediaCodec` H.264 &amp; AAC-LC RTMP/RTMPS streaming<br>• Floating Camera2 Facecam with in-flight 90° rotation &amp; resize<br>• Real-time broadcast telemetry HUD &amp; 5-segment LED VU meter | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.2.0-kim-live-studio/kim-live-studio.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/kim-live-studio)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/kim-live-studio/) |
| <img src="assets/icons/screenstream-rtsp.svg" width="48" height="48"> | **ScreenStream RTSP**<br>`screenstream-rtsp` | `v1.0.0` | **Low-Latency Screen Broadcaster**<br>• Local network RTSP screen mirror server<br>• Zero-latency Wi-Fi preview in VLC or OBS<br>• Real-time stream telemetry and client counter | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-screenstream-rtsp/screenstream-rtsp.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/screenstream-rtsp)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/screenstream-rtsp/) |
| <img src="assets/icons/offline-audio-drop-transcribe.svg" width="48" height="48"> | **DropScribe**<br>`offline-audio-drop-transcribe` | `v1.0.0` | **Offline Voice Drop &amp; Audio Transcribe**<br>• High-fidelity local voice recording<br>• On-device file queue management<br>• 100% private local audio storage | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-offline-audio-drop-transcribe/offline-audio-drop-transcribe.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/offline-audio-drop-transcribe)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/offline-audio-drop-transcribe/) |

---

### ⚡ 2. System, Hardware Diagnostics & Device Bridges

| Icon | Application | Version | Domain & Features | Downloads & Links |
| :---: | :--- | :---: | :--- | :--- |
| <img src="assets/icons/devsys-monitor.svg" width="48" height="48"> | **DevSys Monitor**<br>`devsys-monitor` | `v1.0.0` | **Real-Time Hardware &amp; SoC Telemetry**<br>• Per-core CPU frequency meters &amp; Snapdragon thermal gauges<br>• Live memory heap, swap, and storage bandwidth readouts<br>• Battery voltage, thermals, and real-time charging wattage | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-devsys-monitor/devsys-monitor.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/devsys-monitor)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/devsys-monitor/) |
| <img src="assets/icons/gemini-power-bridge.svg" width="48" height="48"> | **Gemini Power Bridge**<br>`gemini-power-bridge` | `v1.0.0` | **Hardware Key Interceptor for Lenovo ZUI**<br>• Custom physical power/side key hardware bridge<br>• Instant launch trigger for custom local commands or assistant<br>• Lightweight background service with 0% battery penalty | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-gemini-power-bridge/gemini-power-bridge.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/gemini-power-bridge)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/gemini-power-bridge/) |

---

### 🔒 3. Privacy, Security & Ephemeral Utilities

| Icon | Application | Version | Domain & Features | Downloads & Links |
| :---: | :--- | :---: | :--- | :--- |
| <img src="assets/icons/clip-scrub-sanitize-share.svg" width="48" height="48"> | **ClipScrub**<br>`clip-scrub-sanitize-share` | `v1.0.0` | **Private Link &amp; Text Sanitizer**<br>• Automatically strips tracking tokens (`utm_*`, `fbclid`, `gclid`)<br>• One-tap clean share target for incoming URLs<br>• Zero clipboard leaks, 100% on-device regex engine | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-clip-scrub-sanitize-share/clip-scrub-sanitize-share.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/clip-scrub-sanitize-share)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/clip-scrub-sanitize-share/) |
| <img src="assets/icons/stealth-drop-secret-pad.svg" width="48" height="48"> | **StealthDrop**<br>`stealth-drop-secret-pad` | `v1.0.0` | **Ephemeral Scratchpad &amp; Secret Vault**<br>• Zero-footprint ephemeral text editor<br>• Self-destructing scratchpad sessions upon app minimization<br>• Memory-only buffer with zero unencrypted disk cache | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-stealth-drop-secret-pad/stealth-drop-secret-pad.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/stealth-drop-secret-pad)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/stealth-drop-secret-pad/) |

---

### 🍳 4. Culinary, Cooking & Defrost Sentinels

| Icon | Application | Version | Domain & Features | Downloads & Links |
| :---: | :--- | :---: | :--- | :--- |
| <img src="assets/icons/defrost-safe-meat-prep-sentinel.svg" width="48" height="48"> | **ChillThaw**<br>`defrost-safe-meat-prep-sentinel` | `v1.0.0` | **Safe Meat Defrost Sentinel**<br>• USDA food-safety defrost countdown windows<br>• Refrigerator, cold water, and ambient defrost monitors<br>• 1-tap Quick Settings tile &amp; lock screen chronometer | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-defrost-safe-meat-prep-sentinel/defrost-safe-meat-prep-sentinel.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/defrost-safe-meat-prep-sentinel)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/defrost-safe-meat-prep-sentinel/) |
| <img src="assets/icons/roast-rest-meat-thermo-sentinel.svg" width="48" height="48"> | **RoastRest**<br>`roast-rest-meat-thermo-sentinel` | `v1.0.0` | **Carryover Meat Rest Sentinel**<br>• Offline culinary carryover temperature rise calculator<br>• Target rest timer preventing juice loss in steaks &amp; roasts<br>• Ongoing persistent lock screen chronometer | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-roast-rest-meat-thermo-sentinel/roast-rest-meat-thermo-sentinel.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/roast-rest-meat-thermo-sentinel)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/roast-rest-meat-thermo-sentinel/) |
| <img src="assets/icons/pan-flip-sear-timer.svg" width="48" height="48"> | **PanFlip**<br>`pan-flip-sear-timer` | `v1.0.0` | **Kitchen Sear &amp; Flip Sentinel**<br>• Hands-free pacing timer for perfect crusts and steaks<br>• High-contrast ring visible across the kitchen<br>• Quick Settings 1-tap start for busy home cooks | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-pan-flip-sear-timer/pan-flip-sear-timer.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/pan-flip-sear-timer)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/pan-flip-sear-timer/) |
| <img src="assets/icons/steep-brew-tea-timer.svg" width="48" height="48"> | **SteepPulse**<br>`steep-brew-tea-timer` | `v1.0.0` | **Multi-Infusion Tea &amp; Coffee Sentinel**<br>• Gongfu tea multi-infusion stepped stopwatch<br>• Specialty pour-over coffee bloom pacing<br>• Customizable water temp and leaf ratio presets | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-steep-brew-tea-timer/steep-brew-tea-timer.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/steep-brew-tea-timer)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/steep-brew-tea-timer/) |
| <img src="assets/icons/frost-thaw-prep-timer.svg" width="48" height="48"> | **ThawGuard**<br>`frost-thaw-prep-timer` | `v1.0.0` | **Offline Food Defrost Sentinel**<br>• Precision weight-based thaw estimation<br>• Danger zone temperature alert warnings<br>• Reliable offline alarms via Android AlarmManager | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-frost-thaw-prep-timer/frost-thaw-prep-timer.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/frost-thaw-prep-timer)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/frost-thaw-prep-timer/) |

---

### 🏃 5. Health, Fitness, Fasting & Commute Vigilance

| Icon | Application | Version | Domain & Features | Downloads & Links |
| :---: | :--- | :---: | :--- | :--- |
| <img src="assets/icons/fast-track-intermittent-fasting-sentinel.svg" width="48" height="48"> | **FastTrack**<br>`fast-track-intermittent-fasting-sentinel` | `v1.0.0` | **Offline Intermittent Fasting Sentinel**<br>• 16:8, 18:6, 20:4, and OMAD fasting protocols<br>• Persistent lock screen fasting chronometer<br>• Quick Settings tile 1-tap start/stop | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-fast-track-intermittent-fasting-sentinel/fast-track-intermittent-fasting-sentinel.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/fast-track-intermittent-fasting-sentinel)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/fast-track-intermittent-fasting-sentinel/) |
| <img src="assets/icons/workout-program-tracker.svg" width="48" height="48"> | **IronPulse**<br>`workout-program-tracker` | `v1.0.0` | **Offline Gym &amp; Workout Program Tracker**<br>• Zero-network workout logging for iron lifters<br>• 1RM calculations, set/rep counters, rest timers<br>• Offline SQLite database with local JSON export | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-workout-program-tracker/workout-program-tracker.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/workout-program-tracker)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/workout-program-tracker/) |
| <img src="assets/icons/steep-chill-ice-bath-timer.svg" width="48" height="48"> | **CryoPulse**<br>`steep-chill-ice-bath-timer` | `v1.0.0` | **Cold Plunge &amp; Ice Bath Sentinel**<br>• Haptic breath pacing for cold-water immersion<br>• Water temperature logging and adaptation curves<br>• High-visibility display readable through fog and water | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-steep-chill-ice-bath-timer/steep-chill-ice-bath-timer.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/steep-chill-ice-bath-timer)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/steep-chill-ice-bath-timer/) |
| <img src="assets/icons/snooze-guard-microsleep-commute.svg" width="48" height="48"> | **SnoozeGuard**<br>`snooze-guard-microsleep-commute` | `v1.0.0` | **Public Transit Commute Vigil**<br>• Dead-man vigilance timer preventing missed stops<br>• Device motion awareness via internal accelerometer<br>• Escalating multi-stage haptic wake alarms | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-snooze-guard-microsleep-commute/snooze-guard-microsleep-commute.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/snooze-guard-microsleep-commute)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/snooze-guard-microsleep-commute/) |
| <img src="assets/icons/aurafocus-minimalist-pomodoro-ambient-sound.svg" width="48" height="48"> | **AuraFocus**<br>`aurafocus-minimalist-pomodoro-ambient-sound` | `v1.0.0` | **Minimalist Pomodoro &amp; Ambient Sound**<br>• Distraction-free work/break interval chronometer<br>• Pure offline procedural ambient noise generator<br>• Minimalist OLED true-black aesthetic | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-aurafocus-minimalist-pomodoro-ambient-sound/aurafocus-minimalist-pomodoro-ambient-sound.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/aurafocus-minimalist-pomodoro-ambient-sound)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/aurafocus-minimalist-pomodoro-ambient-sound/) |
| <img src="assets/icons/offline-prescription-pill-refill-sentinel.svg" width="48" height="48"> | **DoseGuard**<br>`offline-prescription-pill-refill-sentinel` | `v1.0.0` | **Offline Med &amp; Refill Sentinel**<br>• Private local medication schedule alarms<br>• Pill inventory depletion and refill date projections<br>• 100% offline medical privacy guarantee | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-offline-prescription-pill-refill-sentinel/offline-prescription-pill-refill-sentinel.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/offline-prescription-pill-refill-sentinel)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/offline-prescription-pill-refill-sentinel/) |

---

### 🛠️ 6. Daily Utilities, Woodworking & Hardware Tools

| Icon | Application | Version | Domain & Features | Downloads & Links |
| :---: | :--- | :---: | :--- | :--- |
| <img src="assets/icons/glue-set-clamp-sentinel.svg" width="48" height="48"> | **ClampGuard**<br>`glue-set-clamp-sentinel` | `v1.0.0` | **Woodworking Glue &amp; Epoxy Cure Sentinel**<br>• PVA, polyurethane, and epoxy cure window calculators<br>• Ambient workshop temperature compensation<br>• 1-tap Quick Settings tile &amp; lock screen timer | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-glue-set-clamp-sentinel/glue-set-clamp-sentinel.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/glue-set-clamp-sentinel)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/glue-set-clamp-sentinel/) |
| <img src="assets/icons/tap-meter-parking-sentinel.svg" width="48" height="48"> | **MeterPulse**<br>`tap-meter-parking-sentinel` | `v1.0.0` | **Offline Parking Meter Sentinel**<br>• 1-tap parking expiration timer from Quick Settings<br>• Escalating notifications before meter expiration<br>• Visual color-coded countdown HUD | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-tap-meter-parking-sentinel/tap-meter-parking-sentinel.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/tap-meter-parking-sentinel)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/tap-meter-parking-sentinel/) |
| <img src="assets/icons/park-pin-offline-spot-finder.svg" width="48" height="48"> | **ParkPin**<br>`park-pin-offline-spot-finder` | `v1.0.0` | **Offline Vehicle Spot Anchor**<br>• GPS coordinate anchor without cloud mapping APIs<br>• Offline compass bearing and distance vector<br>• Floor level, zone, and parking stall note memo | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-park-pin-offline-spot-finder/park-pin-offline-spot-finder.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/park-pin-offline-spot-finder)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/park-pin-offline-spot-finder/) |
| <img src="assets/icons/laundry-cycle-sentinel.svg" width="48" height="48"> | **CycleGuard**<br>`laundry-cycle-sentinel` | `v1.0.0` | **Laundry Cycle Sentinel**<br>• Washer, dryer, and soak countdown pacing<br>• Wrinkle-guard reminder alerts<br>• Quick Settings tile 1-tap activation | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-laundry-cycle-sentinel/laundry-cycle-sentinel.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/laundry-cycle-sentinel)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/laundry-cycle-sentinel/) |
| <img src="assets/icons/offline-fuel-log-tracker.svg" width="48" height="48"> | **TankPulse**<br>`offline-fuel-log-tracker` | `v1.0.0` | **Offline Fuel &amp; Mileage Tracker**<br>• Exact L/100km or MPG efficiency calculation<br>• Total operating cost and odometer tracking<br>• Fully sandboxed SQLite local records | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-offline-fuel-log-tracker/offline-fuel-log-tracker.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/offline-fuel-log-tracker)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/offline-fuel-log-tracker/) |
| <img src="assets/icons/return-guard-receipt-countdown.svg" width="48" height="48"> | **ReturnGuard**<br>`return-guard-receipt-countdown` | `v1.0.0` | **Store Return Deadline &amp; Receipt Viewer**<br>• Days-remaining return countdowns for purchases<br>• High-contrast receipt image and policy storage<br>• Timely alerts before return windows close | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-return-guard-receipt-countdown/return-guard-receipt-countdown.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/return-guard-receipt-countdown)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/return-guard-receipt-countdown/) |
| <img src="assets/icons/who-has-my-tool-lending.svg" width="48" height="48"> | **WhoHasMy**<br>`who-has-my-tool-lending` | `v1.0.0` | **Tool &amp; Gear Lending Tracker**<br>• Keep track of who borrowed tools and workshop gear<br>• Return due date reminders and contact tags<br>• 100% offline local record | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-who-has-my-tool-lending/who-has-my-tool-lending.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/who-has-my-tool-lending)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/who-has-my-tool-lending/) |
| <img src="assets/icons/size-vault-family-clothing.svg" width="48" height="48"> | **SizeVault**<br>`size-vault-family-clothing` | `v1.0.0` | **Family Clothing &amp; Shoe Size Guide**<br>• Instant offline lookup for family shoe and clothing sizes<br>• US, UK, EU, and Asian size conversion charts<br>• Gift-shopping quick reference | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-size-vault-family-clothing/size-vault-family-clothing.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/size-vault-family-clothing)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/size-vault-family-clothing/) |

---

### 💼 7. Finance, Documents & Productivity

| Icon | Application | Version | Domain & Features | Downloads & Links |
| :---: | :--- | :---: | :--- | :--- |
| <img src="assets/icons/ph-vat-calculator.svg" width="48" height="48"> | **PH VAT Calculator**<br>`ph-vat-calculator` | `v1.0.0` | **Philippine BIR Tax &amp; Invoice Tool**<br>• 12% Value Added Tax calculation (inclusive &amp; exclusive)<br>• BIR withholding tax (EWT 1%, 2%, 5%) breakdown<br>• Clear tabular output for freelance and enterprise billing | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-ph-vat-calculator/ph-vat-calculator.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/ph-vat-calculator)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/ph-vat-calculator/) |
| <img src="assets/icons/snip-shelf-receipt-ocr.svg" width="48" height="48"> | **SnipShelf**<br>`snip-shelf-receipt-ocr` | `v1.0.0` | **Offline Warranty &amp; Manual Vault**<br>• Local catalog for product warranties and serial numbers<br>• On-device document snapshot viewer<br>• Expiration alerts for major home appliances | [⬇ Download APK](https://github.com/eliakimrosil/kim-android-apps/releases/download/v1.0.0-snip-shelf-receipt-ocr/snip-shelf-receipt-ocr.apk)<br>[🌿 Branch](https://github.com/eliakimrosil/kim-android-apps/tree/app/snip-shelf-receipt-ocr)<br>[🌐 Web Page](https://eliakimrosil.github.io/kim-android-apps/snip-shelf-receipt-ocr/) |

---

## 🌿 Repository Branch Architecture

This repository adopts a **modular branch-per-application architecture**:

```
github.com/eliakimrosil/kim-android-apps
│
├── main                     Flagship Ecosystem Hub, Master README, Catalog & Documentation
├── gh-pages                 Production Web Showcase at eliakimrosil.github.io/kim-android-apps/
│
├── app/kim-live-studio      Native source code for KIM Live Studio
├── app/devsys-monitor       Native source code for DevSys Monitor
├── app/pan-flip-sear-timer  Native source code for PanFlip
└── ...                      (28 dedicated application branches)
```

### Cloning a Specific Application
To clone and work on any individual app without pulling unnecessary history:

```bash
# Clone a single application branch
git clone --single-branch -b app/kim-live-studio https://github.com/eliakimrosil/kim-android-apps.git

# Navigate into the project and build
cd kim-android-apps
gradle assembleDebug
```

---

## 📦 How to Install Applications

### Option 1: Direct APK Download (Recommended)
Every application binary is compiled, signed, and hosted on **GitHub Releases Global CDN**:
1. Open the [GitHub Releases Page](https://github.com/eliakimrosil/kim-android-apps/releases).
2. Download the `.apk` file for your desired app.
3. Tap the downloaded file on your Android device to install.

### Option 2: Web App Store
Visit the official **[KIM Android Apps Web Portal](https://eliakimrosil.github.io/kim-android-apps/)** on your phone or desktop to search, filter by category, and download APKs in one tap.

### Option 3: Direct ADB Install (Developers)
If connected to your phone via USB or wireless ADB:
```bash
adb install -r <app-name>.apk
```

---

## 🛠️ Building from Source

All applications can be compiled using standard Android SDK tools on Linux, macOS, Windows, or directly on Android via Termux:

### Requirements
- Android SDK Platform `android-33`
- Java JDK 17
- Gradle 8.0+

### Compilation Steps
```bash
# 1. Switch to desired application branch
git checkout app/devsys-monitor

# 2. Compile debug APK
gradle assembleDebug

# 3. Output binary location
ls -lh build/outputs/*.apk
```

---

## 🔒 Privacy & Security Charter

We believe that basic mobile utilities should respect user sovereignty:

- **100% Offline by Default**: Applications do not phone home.
- **No Third-Party Analytics**: Zero Google Firebase, zero Facebook Graph, zero AppsFlyer.
- **Zero Ads**: No interstitial ads, no banner clutter, no sponsored tracking SDKs.
- **Local Storage Only**: App configuration and state are stored strictly within the app's sandboxed storage.

For detailed security policies or to report a vulnerability, review our [Security Charter](SECURITY.md).

---

## 🤝 Contributing

We welcome contributions, bug reports, and new offline utility suggestions! Please review our:
- [Contributing Guidelines](CONTRIBUTING.md)
- [Bespoke Design System Guide](docs/BESPOKE_DESIGN_GUIDELINES.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)

---

## 📄 License & Credits

All applications in this repository are licensed under the [MIT License](LICENSE).

Developed with precision by **[Eliakim Rosil](https://github.com/eliakimrosil)** using pure Android Framework components on Android ARM64.
