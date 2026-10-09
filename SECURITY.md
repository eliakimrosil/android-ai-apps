# Security & Privacy Policy

## 🔒 Zero-Data & Offline-First Charter

All applications in the **KIM Android Apps** suite are engineered with an uncompromising **Zero-Data & Privacy-First** philosophy:

1. **Zero Cloud Telemetry**: None of our utilities include analytics SDKs, trackers, performance beacons, or ad networks (no Firebase Analytics, no Google AdMob, no Facebook SDK, no AppsFlyer).
2. **Local Sandboxing**: All user data, configuration, timers, history, and tokens are stored strictly within the private Android application sandbox (`/data/data/<package>/`).
3. **No Unnecessary Permissions**: Applications only declare the minimum runtime permissions essential for their specific core function. Offline utilities do not request or use the `android.permission.INTERNET` permission unless explicitly required for network broadcast (e.g., streaming in KIM Live Studio or ScreenStream RTSP directly to user-specified local/remote servers).
4. **Zero Account Lock-in**: No account creation, login, or cloud sync is ever required.

---

## 🛡️ Supported Versions

We actively maintain the latest releases of each application in this repository.

| Version Tag | Supported | Architecture | Min SDK | Target SDK |
| :--- | :--- | :--- | :--- | :--- |
| `v1.2.x` | ✅ Yes | ARM64 / aarch64 | Android 8.0 (API 26) | Android 13+ (API 33) |
| `v1.1.x` | ✅ Yes | ARM64 / aarch64 | Android 8.0 (API 26) | Android 13+ (API 33) |
| `v1.0.x` | ✅ Yes | ARM64 / aarch64 | Android 8.0 (API 26) | Android 13+ (API 33) |

---

## 🚨 Reporting a Vulnerability

We take the security and integrity of our software seriously. If you discover a security vulnerability, security flaw, or privacy leak in any application in this suite:

1. **Do not create a public GitHub Issue.**
2. Send a security report directly via email to:
   **`eliakimrosil@gmail.com`**
3. Include the following details in your report:
   - Application name and package identifier
   - Affected version tag / branch
   - Detailed description of the vulnerability
   - Step-by-step reproduction instructions or Proof-of-Concept (PoC)
   - Estimated impact and severity assessment
4. **Response Timeline**:
   - Initial acknowledgement within **24 hours**.
   - Triage, reproduction, and remediation within **72 hours**.
   - Coordinated release patch published to GitHub Releases and tagged accordingly.

Thank you for helping keep the KIM Android Apps suite secure and private.
