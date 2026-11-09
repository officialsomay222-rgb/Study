# Seamless In-App Ad-Key Activation & Smart Chrome Redirect Engine

This plan upgrades the StudyVerse browser engine so ad-based key activation flows complete directly inside the app without getting kicked out to Chrome, while catching activation callbacks from external Chrome and adding a manual **Open in Chrome** control in the movable Web Browsing Ball menu.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Why Keys Were Activating Only in Chrome Before — And How This Solves It:**
> Ad-shortener and key-verification networks use `intent://...#Intent;scheme=https;package=com.android.chrome;end` links to force Android to leave the app and open external Chrome. When the ad countdown finishes, the final activation callback runs inside Chrome's isolated storage (`localStorage` / cookies) instead of the app's WebView.
>
> By unwrapping `http`/`https` intent links directly inside the app's WebView, masking WebView detection signals so ad networks treat the app like standard Chrome, and registering StudyLime deep-link interception so final activation redirects return to the app, keys will activate directly inside the APK.

- **Confirmed Decision 1 — Default Intent Redirect Behavior**: Keep all `http`/`https` `intent://` and `googlechrome://` links inside the in-app WebView by default so ad-key countdowns and redirects never break out to external Chrome unintentionally.
- **Confirmed Decision 2 — Automatic Ad-Key Activation**: Ensure the entire ad-watching and auto-activation redirect chain executes within the app's persistent WebView session (`localStorage` + `CookieManager.getInstance().flush()`), plus register deep-link handling for `study-verse-lime.vercel.app` so even if a flow starts or finishes in external Chrome, the final redirect can open back inside the app.
- **Confirmed Decision 3 — Web Browsing Ball Control**: Add an **Open in Chrome** button inside the movable Web Browsing Ball menu so any external page can be launched in external Chrome on demand.

---

## 1. Overview & Core Concept

- **What It Does**:
  1. **In-App `intent://` & Chrome-Scheme Unwrapper**: Intercepts `intent://`, `googlechrome://`, and `googlechromes://` URLs requested by ad-key sites, extracts the underlying `https://` target URL (or `browser_fallback_url`), and loads it seamlessly inside the app's WebView. True native app intents (such as Telegram `tg://`, WhatsApp `whatsapp://`, UPI, or Play Store `market://`) continue to open their respective native apps.
  2. **True-Chrome Browser Fingerprint Compatibility**: Injects a lightweight pre-document script (`window.chrome` runtime object, clean `navigator.webdriver = false`, and popup window bridge) so ad networks and verification pages recognize the browser as standard mobile Chrome and never get stuck on "Open in Chrome" screens.
  3. **Instant Cookie & Storage Persistence + StudyLime Deep Link**: Flushes cookies immediately whenever an activation redirect completes and registers `study-verse-lime.vercel.app` web deep links so verification redirects returning from external Chrome open directly inside the app's WebView.
  4. **"Open in Chrome" Action in Floating Ball Menu**: Adds a dedicated **Open in Chrome** action inside the movable Web Browsing Ball menu on external sites.
- **Target Audience / Persona**: Students using StudyVerse who unlock study modules via ad-based key verification and external study portals.
- **Key Value**: Eliminates the frustration of watching an ad only to have the key activate in external Chrome instead of the app.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Seamless In-App Key Activation Flow**:
     - User taps "Generate Key / Watch Ad" on StudyLime or a study module.
     - When the ad shortener triggers a Chrome `intent://` redirect, the app stays smoothly inside the in-app browser instead of jumping out to the Chrome app.
     - Once the ad finishes, the automatic redirect back to the study site happens inside the same WebView session, immediately unlocking the study content and flushing cookies to disk so the key stays active even after restarting the app.
  2. **External Chrome Redirect & Return Flow**:
     - If the user manually taps **Open in Chrome** from the movable Web Browsing Ball menu, the current page launches in external Chrome (`com.android.chrome`).
     - When a link in external Chrome redirects back to `https://study-verse-lime.vercel.app/...`, Android routes the activation URL back into the StudyVerse app's WebView.
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Obsidian Liquid Glass (`#1B2436` to `#0A0F1A` vertical gradient surfaces with crisp cyan `#38BDF8` and emerald `#34D399` accents).
  - *Component Styling & Layout*: Inside the movable Web Browsing Ball menu, the quick-action row expands cleanly to include **Back**, **Forward**, **Top**, and **Chrome** (or a dedicated full-width **Open in Chrome** action pill) with 48dp+ touch targets and clear visual feedback.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Unwrapping HTTPS `intent://` Links Inside WebView vs. Launching External Chrome**
  - *Chosen Approach*: Parse `intent://` URIs via `Intent.parseUri`; if the target scheme is `http`/`https` or contains a `browser_fallback_url` (or `S.browser_fallback_url`), load that `https://` URL directly inside the in-app WebView. Only launch external activities for non-web schemes (`tg://`, `whatsapp://`, `mailto:`, `tel:`, `upi://`, `market://`).
  - *Why*: Ad-key systems rely on cookies and `localStorage` set during the initial step before the ad and verified on the final step after the ad. Keeping the entire chain inside the same WebView guarantees 100% automatic key activation.
  - *Alternatives Considered*: Using Chrome Custom Tabs for the entire app was rejected because it would remove the custom full-screen StudyLime mode, movable Floating Ball, and inbuilt high-speed PDF reader.

- **Decision 2: Multi-Window Popup Bridge (`onCreateWindow`) for Ad/Key Verification**
  - *Chosen Approach*: When an ad or key verification page calls `window.open()`, allow http/https navigation to load smoothly while discarding empty `about:blank` ad-tracker popups so the user is never trapped on a blank screen.
  - *Why*: Many key shorteners open invisible popups on button click while redirecting the main page to the next verification step.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        StudyVerse Browser Engine                        │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        ▼                           ▼                           ▼
┌───────────────────┐     ┌───────────────────┐     ┌─────────────────────┐
│ Smart Intent &    │     │ Chrome Stealth &  │     │ Movable Web Ball    │
│ Deep-Link Router  │     │ Storage Sync      │     │ Chrome Menu         │
├───────────────────┤     ├───────────────────┤     ├─────────────────────┤
│ • Unwraps         │     │ • Spoofs standard │     │ • Refresh Page      │
│   intent:// with  │     │   window.chrome   │     │ • Swipe-to-Refresh  │
│   http/https into │     │   & navigator     │     │   Protection Switch │
│   in-app WebView  │     │   properties      │     │ • Back / Forward /  │
│ • Converts        │     │ • Flushes         │     │   Scroll Top        │
│   googlechrome:// │     │   CookieManager   │     │ • Open in Chrome    │
│   to https://     │     │   on every page   │     │   (Manual external  │
│ • Catches         │     │   commit/finish   │     │   Chrome launcher)  │
│   StudyLime URLs  │     │   so activated    │     │                     │
│   from external   │     │   keys persist    │     │                     │
│   browsers        │     │                   │     │                     │
└───────────────────┘     └───────────────────┘     └─────────────────────┘
```

- **Interactive Component & State Mapping**:
  - **Intent URL Resolver**: Extracts `https://` or `http://` targets from `intent://...#Intent;scheme=https;...;end`, `S.browser_fallback_url`, and `googlechrome://navigate?url=...` and routes them into `webView.loadUrl(...)` while keeping external app deep links (`tg://`, `whatsapp://`, etc.) working natively.
  - **Incoming Web Deep-Link Handler**: If the activity receives an `ACTION_VIEW` intent with an `http`/`https` URL (that is not a PDF/Word file), it loads that URL directly into the active `WebView` so any external Chrome redirect back to StudyLime transfers seamlessly into the app.
  - **Open in Chrome Action**: Launches the current `webView.url` in `com.android.chrome` (falling back to the system default browser if Chrome is not installed) with `FLAG_ACTIVITY_NEW_TASK`.
