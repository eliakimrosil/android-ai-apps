# Contributing to KIM Android Apps

Thank you for your interest in contributing to the **KIM Android Apps** ecosystem! We welcome contributions that maintain our high standards of domain craftsmanship, pure native performance, and offline privacy.

---

## 🏛️ Guiding Engineering Principles

Before opening a pull request, please ensure your work complies with our core tenets:

### 1. Pure Native Android Framework
- Build strictly using native Android framework APIs (**Java + XML**) targeting Android 13+ (`android-33` / `compileSdkVersion 33`).
- **No Third-Party Bloat**: Avoid heavy dependencies, large UI runtimes, or unneeded libraries. Keep APK sizes ultra-compact (< 500 KB to ~2 MB).
- Fast cold launch times (< 100 ms) and minimal background RAM consumption.

### 2. Bespoke Domain Craftsmanship
- **No Generic Cookie-Cutter Templates**: Every app must be visually and ergonomically designed to match its domain:
  - *Broadcast & Streaming*: Control-room surfaces, live ruby indicators, monospace telemetry HUDs.
  - *Diagnostics & Monitoring*: Precision meters, high-tech HUD cards, tactical readouts.
  - *Food, Culinary & Timers*: High-contrast countdown rings, big touch targets for messy kitchen hands, persistent lock screen chronometers, Quick Settings tiles.
  - *Fitness & Sports*: High-energy bold typography, rapid haptics, glanceable states.
- **Working Day / Night / Auto Theming**: Every app must have tested, functional Dark and Light modes with proper contrast tokens (`colors.xml` and `values-night/colors.xml`).
- **Adaptive Vector Icons**: Every app must have bespoke multi-layered vector icons:
  - `ic_launcher_background.xml`
  - `ic_launcher_foreground.xml` (centered in 20-88 dp safe zone)
  - `ic_launcher_monochrome.xml` (for Android 13+ Material You dynamic theming)
  - 512x512 high-resolution master SVG.

### 3. Absolute Privacy & Offline-First
- Zero telemetry, zero external analytics SDKs, zero ads.
- Do not request `android.permission.INTERNET` unless strictly required for user-directed local network or streaming functions.

---

## 🌿 Repository Branch Architecture

This repository uses a **modular branch-per-application structure**:

- **`main`**: Ecosystem hub containing the master catalog, documentation, community files, and CI workflows.
- **`gh-pages`**: Production web showcase and Google Play policy hosting at [eliakimrosil.github.io/kim-android-apps](https://eliakimrosil.github.io/kim-android-apps/).
- **`app/<slug>`**: Dedicated standalone branch for each individual Android app. Contains isolated source trees, Gradle build scripts, and resources.

---

## 🛠️ Contribution Workflow

1. **Fork the Repository**:
   Fork [eliakimrosil/kim-android-apps](https://github.com/eliakimrosil/kim-android-apps).

2. **Select or Create an App Branch**:
   - To improve an existing app:
     ```bash
     git checkout app/<app-slug>
     git checkout -b feature/my-enhancement
     ```
   - To propose a new app, please open an issue using the [New App Proposal Template](https://github.com/eliakimrosil/kim-android-apps/issues/new?template=new_app_proposal.yml) first.

3. **Verify Local Builds**:
   Compile the app locally via Gradle:
   ```bash
   gradle assembleDebug
   ```
   Ensure zero compiler warnings and verify that the generated APK installs and runs cleanly without crashes:
   ```bash
   adb install -r build/outputs/*.apk
   ```

4. **Commit Following Conventional Commits**:
   - `feat(app-slug): description`
   - `fix(app-slug): description`
   - `docs(app-slug): description`
   - `style(app-slug): description`

5. **Submit a Pull Request**:
   Target the corresponding `app/<app-slug>` branch (or `main` for global documentation/catalog updates).
