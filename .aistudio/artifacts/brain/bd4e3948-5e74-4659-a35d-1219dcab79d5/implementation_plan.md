# StudyVerse: Scroll Stability, High-Performance Motion & Website WebView Engine

Focused blueprint to eliminate accidental scroll-up page reloads, optimize hardware-accelerated animations, prevent memory allocations during rendering, and maximize overall WebView stability for `https://study-verse-lime.vercel.app/`.

> [!IMPORTANT]
> **Key Decisions & Focused Scope**
> - **Remove Server-Driven UI / Bottom Sheets**: As requested, extraneous server-driven UI overlays and bottom sheets are completely omitted. The app remains clean and dedicated strictly to opening your site smoothly.
> - **Scroll Fix & Swipe Resistance**: `SwipeRefreshLayout` is decoupled from accidental upward scrolls by requiring an intentional touch drag threshold at the root scroll boundary (`scrollY == 0`), applying a strict child scroll callback (`setOnChildScrollUpCallback`), respecting dynamic domain rules without Compose `update` block overrides, and replacing `clearCache(true)` with standard fast HTTP reloads.
> - **High-Performance Butter Wave Animations**: Path allocations in `Canvas` are converted to zero-allocation reused buffers (`remember { Path() }` with `rewind()`), eliminating GC frame drops and ensuring 60/120fps motion.
> - **WebView Speed Optimizations**: Enabling offscreen pre-rastering, hardware layer acceleration, and high render priority for ultra-fast, smooth page interactions.

---

## 1. Overview & Core Concept

### Why Upward Scrolling Triggered Page Reloads
1. Android's `SwipeRefreshLayout` uses a default `canChildScrollUp()` check that queries `WebView.canScrollVertically(-1)`.
2. When a user scrolls a page upwards (dragging fingers down towards the bottom of the screen to view content higher up), the moment the page reaches the top or when scrolling through internal scrollable elements (like CSS `overflow-y: scroll`), `canScrollVertically(-1)` becomes `false`.
3. Because the default pull distance was short (~64dp), any continued downward finger movement or scroll deceleration immediately triggered a page refresh.
4. Moreover, the Compose `update` lambda was resetting `container.isEnabled = !currentLoadedUrl.contains("docs.google.com/gview")`, which wiped out any custom `pullToRefresh = false` rule set by the website.
5. In addition, `clearCache(true)` was destroying the entire disk and memory cache on each refresh, making reloads slow and jarring.

### How It Is Resolved
- **Child Scroll Up Callback**: Implemented `setOnChildScrollUpCallback` checking both `view.scrollY > 0` and `view.canScrollVertically(-1)` to strictly block refreshes unless the page is completely at the top and stationary.
- **Increased Pull Threshold**: Extended the swipe trigger distance to `140dp` with a `160dp` slingshot distance, demanding a deliberate, intentional pull gesture before refreshing.
- **Rule Preservation**: Replaced blind container enabling with a persistent state `isPullToRefreshRuleEnabled` so domain rules are always respected.
- **HTTP Cache Preservation**: Removed `clearCache(true)` on refresh so assets are revalidated with HTTP 304 instead of redownloading 10MB+ bundles.
- **Zero Overscroll Conflict**: Configured `overScrollMode = View.OVER_SCROLL_NEVER`.

---

## 2. Animation & Rendering Optimizations

- **Zero-Allocation Canvas Paths**: `LoadingOverlay` previously allocated 6 `Path()` instances per frame at 60/120 FPS. These are now remembered and reused with `rewind()`, dropping garbage collection pauses to 0.
- **Fluid Exit Animation**: Refined `AnimatedVisibility` exit with `FastOutSlowInEasing` for seamless visual handoff to the web interface.
- **WebView Offscreen Pre-Rastering**: Set `settings.offscreenPreRaster = true` so modern web layouts scroll without checkerboarding.

---

## 3. Stability & Architecture

- **Clean Single-Destination Flow**: The app opens `https://study-verse-lime.vercel.app/` directly, handling deep links, PDF downloads with embedded viewer fallback, video fullscreen orientations, and color syncing.
- **Dynamic Rules System Intact**: Custom domain rules via JavaScript bridge (`AndroidApp.updateSettings`) remain 100% active and saved via SharedPreferences smart merge.
