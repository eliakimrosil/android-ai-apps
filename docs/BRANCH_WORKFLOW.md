# Repository Architecture & Distribution Model

The **kim-android-apps** repository serves as the **Official Public Distribution Hub & Showcase** for the Kim Android Apps ecosystem.

---

## 🏛️ Architecture Overview

```
                                      kim-android-apps (Public Hub)
                                            |
         +----------------------------------+----------------------------------+
         |                                                                     |
     [ main ]                                                             [ gh-pages ]
 Public Showcase & Docs                                               Public Web Store Portal
 - Master README                                                      - eliakimrosil.github.io
 - apps.json Catalog                                                    /kim-android-apps/
 - assets/icons/                                                      - App Landing Pages
 - Proprietary Freeware EULA                                          - Privacy Policies
 - Issue & Feature Tracker                                            - 1-Tap APK Downloads
```

### 1. `main` Branch
- **Role**: Flagship repository presentation, master index of all applications, architecture documentation, issue templates, and catalog metadata.
- **Licensing**: Proprietary Freeware EULA (All Rights Reserved).

### 2. `gh-pages` Branch
- **Role**: Hosts the live GitHub Pages web store and HTTPS privacy policy endpoints for Google Play Developer Console compliance.
- **URL**: [https://eliakimrosil.github.io/kim-android-apps/](https://eliakimrosil.github.io/kim-android-apps/)

### 3. Source Code Isolation
- Application source code is developed natively and securely stored on the local developer environment and private repositories.
- The public GitHub repository does not expose internal source trees, protecting the author's intellectual property while maintaining free binary distribution.

---

## 🏷️ Release Tagging Convention

Every application release binary is published to GitHub Releases under an immutable semantic tag:

- **Format**: `v<version>-<app-slug>`
- **Examples**:
  - `v1.2.0-kim-live-studio`
  - `v1.0.0-defrost-safe-meat-prep-sentinel`
  - `v1.0.0-glue-set-clamp-sentinel`
- **Release Assets**: Direct CDN download URLs in the format:
  `https://github.com/eliakimrosil/kim-android-apps/releases/download/v<version>-<slug>/<slug>.apk`
