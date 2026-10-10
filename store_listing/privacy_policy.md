# Privacy Policy for KR Live Studio

*Last Updated: October 9, 2026*

This privacy policy applies to the Android mobile application **KR Live Studio** (the "Application"), developed and published by Eliakim Rosil ("we", "us", or "our") under the KimRosil App Studio developer portfolio (https://apps.kimrosil.com).

The Application is distributed via the Google Play Store, official APK releases, and https://apps.kimrosil.com/kim-live-studio/.

---

### 1. Offline-First Architecture & Sandboxed Local Storage
The Application is engineered as an offline-first mobile utility: *Broadcast screen, camera & audio directly to RTMP live platforms*
- **Personal Information:** We do NOT collect, harvest, store, or transmit personal identity details (such as names, email addresses, phone numbers, contacts, or location history) to any proprietary developer servers.
- **Sandboxed Local Storage:** Any user inputs, notes, item records, history logs, or user preferences are stored strictly on your local device within Android's sandboxed storage (`SharedPreferences`, Room, or local SQLite database). Your personal application records never leave your physical device.
- **Data Deletion:** Uninstalling the Application or clearing its storage via Android Settings (`Settings > Apps > KR Live Studio > Storage > Clear Data`) permanently and irreversibly deletes all locally saved records.

### 2. Advertising & Monetization (Google AdMob)
To support ongoing independent development and keep our utility applications free to users, the Application may display advertisements served by **Google AdMob** (a service provided by Google LLC).

When advertisements are displayed, the Google Mobile Ads SDK may automatically process certain pseudonymous device information strictly for advertising delivery, contextual ad frequency capping, fraud prevention, and performance diagnostics:
- **Device & Advertising Identifiers:** Google Advertising ID (AAID / GAID) for ad attribution and frequency capping.
- **Approximate Location:** General, non-precise location inferred from your IP address (country or city level) to serve regionally relevant ads.
- **Diagnostics & SDK Performance:** Ad interaction events, crash diagnostics, and device characteristics (e.g. device model, Android OS version).

For more information on how Google processes and protects advertising data, please review:
- **Google Privacy Policy:** https://policies.google.com/privacy
- **Google Advertising Technologies:** https://policies.google.com/technologies/ads
- **How Google uses information from sites or apps that use our services:** https://policies.google.com/technologies/partner-sites

#### User Controls & Opting Out of Personalized Ads
You can reset or delete your Google Advertising ID or opt out of personalized advertisements at any time directly through your Android device settings:
1. Open Android **Settings**.
2. Go to **Google** → **Ads** (or **Security & Privacy** → **Privacy** → **Ads** on Android 12+).
3. Tap **Delete advertising ID** or enable **Opt out of Ads Personalization**.

### 3. Permissions & Device Access
The Application requests only minimal, standard Android platform permissions strictly required to perform local utility operations:
- **`CAMERA`**: Scan QR codes or capture local document/receipt images for real-time OCR. All image frames are processed in-memory locally and are never stored or exfiltrated.
- **`FOREGROUND_SERVICE`**: Maintain continuous background counting or streaming operations without system termination.
- **`FOREGROUND_SERVICE_CAMERA`**: Used strictly for local on-device operation of the requested feature.
- **`FOREGROUND_SERVICE_MEDIA_PROJECTION`**: Used strictly for local on-device operation of the requested feature.
- **`FOREGROUND_SERVICE_MICROPHONE`**: Used strictly for local on-device operation of the requested feature.
- **`INTERNET`**: Load remote network streams (for screen casting utilities) and serve advertisements via the Google Mobile Ads SDK (AdMob).
- **`POST_NOTIFICATIONS`**: Display on-device alarm notifications and timer countdown alerts when timers expire. No alert content is transmitted remotely.
- **`RECORD_AUDIO`**: Capture local audio signals for on-device voice transcription or real-time VU monitoring. Audio streams are processed strictly on-device.
- **`SYSTEM_ALERT_WINDOW`**: Display optional floating timer or telemetry overlay windows over other applications at user request.
- **`VIBRATE`**: Provide tactile haptic alarms and cue alerts upon timer completion.

Any requested device permissions are executed strictly on-device to power the requested utility features. The Application never sells, brokers, or exfiltrates permission-derived data.

### 4. Children's Privacy (COPPA & Families Compliance)
The Application is designed as a general-audience utility and is not directed at children under the age of 13 (or under 16 in the European Union). We do not knowingly collect personal information from children. If you believe personal data has inadvertently been processed, please contact us immediately so we can promptly address the matter.

### 5. Master Privacy Policy & Developer Profile
This privacy policy is part of the unified KimRosil App Studio developer portfolio. For our developer-wide data safety commitments, please visit:
- **Master Developer Privacy Policy:** https://apps.kimrosil.com/privacy.html
- **Official Showcase:** https://apps.kimrosil.com/kim-live-studio/

### 6. Contact Information
If you have any questions, inquiries, or support requests regarding this Privacy Policy or your data rights, please contact:
- **Developer:** Eliakim Rosil
- **Email:** eliakimrosil@gmail.com
- **Website:** https://apps.kimrosil.com
- **Project Repository:** https://github.com/eliakimrosil/kimrosil-app-studio
