# Privacy Policy for KIM Live Studio

*Last Updated: October 2026*

This privacy policy applies to the Android mobile application **KIM Live Studio** (the "Application"), developed and published as a direct live broadcasting utility for Android.

---

### 1. Direct Broadcasting & Zero Data Harvesting
The Application connects directly to user-specified RTMP/RTMPS ingest endpoints to stream live screen video and audio.
- **Direct Transmission Only:** Broadcast streams are transmitted directly from your device to the streaming ingest destination specified in your settings. There are no intermediate cloud servers, relay proxies, or developer collection servers.
- **Personal Information:** We do NOT collect, harvest, store, or transmit personal details (such as names, email addresses, phone numbers, or physical locations).
- **Device Identifiers:** We do NOT collect Android Advertising IDs (AAID), IMEI numbers, IP addresses, or hardware fingerprints.
- **Zero Analytics SDKs:** The Application contains NO third-party analytics SDKs (e.g. Firebase Analytics, Google Analytics, Adjust, AppsFlyer) and transmits zero telemetry.

### 2. Local Device Storage
Any user inputs, stream keys, server endpoints, and application preferences are stored strictly on your local device within Android's sandboxed storage (`SharedPreferences`).
- Your stream keys and configurations never leave your device except when authenticating directly with your specified RTMP ingest server.
- Uninstalling the Application or clearing its app storage via Android Settings permanently deletes all local records.

### 3. Permissions & Device Access
The Application requests only the specific Android permissions necessary to perform its live streaming functions:
- **Foreground Service & Screen Capture (MediaProjection):** Required to capture and encode display activity during active broadcasts.
- **Record Audio (Microphone):** Required to capture voice commentary during live sessions.
- **Camera:** Optional permission used solely for the picture-in-picture floating facecam overlay.
- **System Alert Window (Display over other apps):** Optional permission used to display the floating camera preview while navigating other apps.
- **Post Notifications:** Used to provide the ongoing foreground service notification required by Android for background capture tasks.

### 4. Third-Party Services & Advertisements
The Application contains **zero third-party advertisements**, zero ad tracking libraries, and zero data-broker integrations. No data is shared with or sold to third parties under any circumstances.

### 5. Children's Privacy (COPPA Compliance)
The Application does not target or knowingly collect data from children under the age of 13. Because no personal data is collected or transmitted, the Application complies with global child privacy standards.

### 6. Contact Information
If you have any questions, inquiries, or support requests regarding this Privacy Policy, please reach out via our GitHub repository:
- **Repository:** https://github.com/eliakimrosil/android-ai-apps
- **Project:** kim-live-studio
