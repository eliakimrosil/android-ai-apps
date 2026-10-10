# Bespoke App Design & Adaptive Icon Standards

This specification outlines the visual craft standards for all applications created or maintained within the **KimRosil App Studio** suite.

---

## 🎨 Beyond Cookie-Cutter Templates

Generic Android apps often suffer from monotone, interchangeable designs with pastel pill buttons and flat grey cards. In KimRosil App Studio, every app is designed with **bespoke, domain-tailored craftsmanship**:

### Aesthetic Archetypes by Domain

1. **Broadcasting & Live Streaming (e.g. KR Live Studio, ScreenStream RTSP)**
   - **Visuals**: Studio control-room aesthetic, deep obsidian (#0E0E12) surfaces, hairline border separation.
   - **Accents**: Studio Cyan (#00E5FF) and Live Ruby (#FF1744) tally glowing badges.
   - **Controls**: Tactile switches, monospace bitrate and FPS telemetry readouts, 5-segment LED VU audio meters.

2. **System & Device Telemetry (e.g. DevSys Monitor, Gemini Power Bridge)**
   - **Visuals**: High-tech cyber HUD with elevated dark navy/obsidian cards.
   - **Accents**: Electric cyan (#00F0FF), laser green (#00E676), and warning amber (#FFB300).
   - **Controls**: Precision progress gauges, live CPU frequency monitors, hardware thermal cards.

3. **Culinary & Food Sentinels (e.g. RoastRest, ChillThaw, PanFlip, SteepPulse)**
   - **Visuals**: Warm culinary amber/slate tones with large high-visibility timers.
   - **Ergonomics**: Oversized touch targets (> 64 dp) designed for messy cooking hands.
   - **Integration**: 1-tap Android Quick Settings tiles, persistent ongoing Lock Screen chronometers.

4. **Health, Fasting & Fitness (e.g. IronPulse, FastTrack, CryoPulse, AuraFocus)**
   - **Visuals**: High-energy athletic glyphs, neon pulse accents (lime, electric cyan, pulse red).
   - **Typography**: Bold numerals legible from across a gym floor or plunge tub.
   - **Haptics**: Distinctive vibration cadences for cycle transitions and breath pacing.

---

## 📱 Adaptive Vector Icon Standard

Every app must include complete, high-resolution adaptive vector icon drawables:

```xml
<!-- res/mipmap-anydpi-v26/ic_launcher.xml -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
```

### Layer Specifications:
- **`ic_launcher_background.xml`**:
  - Full 108x108 dp viewport.
  - Rich subtle gradients or tonal geometry harmonized with the domain palette.
- **`ic_launcher_foreground.xml`**:
  - Main icon glyphs must remain within the centered **(20,20) to (88,88)** 66 dp safe zone to ensure no clipping across circular, squircle, and pebble launcher masks.
  - Multi-element composition: Primary glyph + domain highlight accents.
- **`ic_launcher_monochrome.xml`**:
  - Clean, high-contrast silhouette for Android 13+ Material You dynamic wallpaper theming.
- **High-Resolution Master SVG**:
  - 512x512 vector SVG stored in `assets/icons/<slug>.svg` for store listing and web catalog.
