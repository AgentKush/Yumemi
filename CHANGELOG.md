# Yumemi Changelog

A comprehensive list of all changes, improvements, and fixes made to the Yumemi manga reader app.

**Yumemi** is a fork of the Kotatsu manga reader, enhanced with extensive performance optimizations, reliability improvements, and quality-of-life features.

---

## Table of Contents

- [Major Features](#major-features)
- [Performance Optimizations](#performance-optimizations)
- [Network & Connectivity](#network--connectivity)
- [Database & Storage](#database--storage)
- [Code Quality & Bug Fixes](#code-quality--bug-fixes)
- [Configuration & Settings](#configuration--settings)

---

## Major Features

### Improvement #11: Source Health Monitor
*Commit: 3b00220 | Date: 2026-01-22*

A comprehensive system for tracking manga source reliability and performance metrics.

**Database Layer:**
- `SourceHealthEntity`: Stores health metrics per source including success/failure counts, response time statistics (avg, min, max) using exponential moving average, consecutive failure tracking, and last error message storage
- `SourceHealthDao`: CRUD operations with health-aware queries for filtering by reliability
- Migration 28→29 creates the source_health table with proper indexing

**Repository Layer:**
- `SourceHealthRepository`: High-level API for querying health data
- `HealthSummary`: Aggregate statistics across all tracked sources
- Health status enum: UNKNOWN, HEALTHY, DEGRADED, POOR, CRITICAL

**Health Metrics:**
- Success rate calculation: (success_count / total) × 100
- Reliability score (0-100): 70% from success rate + 30% from response time scoring
- Response time scoring: <1s=30pts, <2s=25pts, <3s=20pts, etc.
- Health status thresholds: ≥80=HEALTHY, ≥60=DEGRADED, ≥40=POOR, <40=CRITICAL

**Integration:**
- `SourceHealthTracker`: Automatically records metrics for operations
- Empty result detection for identifying potential source issues

---

### Improvement #10: Enhanced App Update Check
*Commit: bb33f23 | Date: 2026-01-22*

Automatic background checking for app updates with rich notifications.

**Core Components:**
- `AppUpdateCheckWorker`: Background worker using WorkManager with configurable intervals (6/12/24/48/72 hours)
- `AppUpdateNotifier`: Rich notification system displaying version, APK size, and changelog preview
- `AppUpdateDismissReceiver`: Handles notification dismissal

**Settings:**
- `isAutoUpdateCheckEnabled`: Toggle background checks (default: true)
- `updateCheckIntervalHours`: Frequency selection
- `isUpdateCheckWifiOnly`: Restrict to WiFi (default: false)
- `skipVersion`/`isVersionSkipped`: Skip specific versions

**Features:**
- WiFi-only option for data-conscious users
- Exponential backoff retry (max 3 attempts)
- Respects stable/unstable release preferences
- Android 13+ notification permission handling

---

### Improvement #8: Download Resume Support
*Commit: e2f97b5 | Date: 2026-01-22*

Resume interrupted downloads instead of starting over.

**Components:**
- `ResumableDownloader`: HTTP Range request support with server capability detection
- `DownloadStateTracker`: Persistent download state with 7-day auto-cleanup
- Thread-safe implementation with mutex protection

**Features:**
- Detects server support via Accept-Ranges header
- Resumes from last byte position on retry
- Falls back gracefully when resume not supported
- Validates Content-Range responses

---

### Improvement #7: DNS Prefetching
*Commit: 65a0879 | Date: 2026-01-22*

Proactive DNS resolution for faster page loads.

**Components:**
- `DnsPrefetcher`: OkHttp Dns implementation with 5-minute TTL cache
- `DnsPrefetchManager`: Source-aware prefetching for enabled sources

**Features:**
- Proactive refresh before cache expiry
- Pre-configured common CDN domains (jsdelivr, cloudflare, googleapis)
- Source-specific CDN domains (MangaDex, Webtoon)
- Network-aware (skips when offline)
- Seamless integration with existing DNS-over-HTTPS

---

### Improvement #6: Network Quality Adaptive Behavior
*Commit: f2f8190 | Date: 2026-01-22*

Dynamic app behavior based on real-time network conditions.

**Components:**
- `NetworkQuality` enum: OFFLINE, POOR, MODERATE, GOOD, EXCELLENT
- `NetworkQualityMonitor`: Real-time network assessment
- `AdaptiveNetworkSettings`: Dynamic configuration provider
- `BandwidthTrackingInterceptor`: Automatic download speed tracking

**Adaptive Settings:**
| Network Quality | Connect Timeout | Read Timeout | Concurrent Downloads | Preload Pages |
|-----------------|-----------------|--------------|----------------------|---------------|
| EXCELLENT       | 10s             | 30s          | 6                    | 5             |
| GOOD            | 15s             | 45s          | 4                    | 3             |
| MODERATE        | 20s             | 60s          | 2                    | 2             |
| POOR            | 30s             | 90s          | 1                    | 1             |
| OFFLINE         | -               | -            | 0                    | 0             |

**Features:**
- WiFi/cellular/ethernet detection
- Cellular network type detection (2G/3G/4G/5G)
- Bandwidth estimation from downloads
- Observable network quality state

---

### Improvement #5: Scrobbler Offline Queue
*Commit: 0c9ebd9 | Date: 2026-01-22*

Queue scrobbling operations when offline for later sync.

---

### Directory Sharing for Local Manga
*Commit: e4ef5bc | Date: 2026-01-21*

Share local manga stored as directories (containing images) in addition to CBZ files.

**Features:**
- Support for CBZ, CBR, ZIP file types
- Share all files in a directory as multiple streams
- Proper MIME type detection
- Hidden file filtering

---

### Enhanced Adblock with CSS Element Hiding
*Commit: 17e87f1 | Date: 2026-01-21*

More powerful ad blocking with CSS-based element hiding.

**CSS Element Hiding (`##` rules):**
- Parse `##selector` rules and store CSS selectors
- Generate injectable JavaScript to hide matched elements
- Creates `<style>` tag with `display:none` rules

**Domain Modifiers:**
- Support `domain=example.com|~exclude.com` syntax
- Include and exclude domain restrictions
- Efficient base domain extraction

---

## Performance Optimizations

### Improvement #9: Database Query Optimization
*Commit: a44909c | Date: 2026-01-22*

Comprehensive database indexing for faster queries.

**New Indexes (Migration 27→28):**
- `manga`: source, title, public_url
- `history`: deleted_at, updated_at, created_at, percent, composite
- `tracks`: chapters_new, last_chapter_date, last_check_time
- `favourites`: composite deleted_at + created_at
- `bookmarks`, `scrobblings`, `local_index`, `suggestions`: manga_id
- `tags`: title
- `track_logs`: manga_id, created_at
- `stats`: manga_id, started_at

**DatabaseOptimizer Utility:**
- ANALYZE for query planner statistics
- VACUUM for database compaction
- Size and fragmentation statistics
- Index listing and integrity checking
- Query plan explanation (debug builds)

---

### Adaptive Prefetch Queue Limit
*Commit: 84f7698 | Date: 2026-01-21*

Dynamic prefetch limits based on device state.

**Factors:**
- Available RAM (2-10 pages range)
- Metered networks: 50% reduction
- Power save mode: 50% reduction

---

### Configurable Parallel Chapter Checking
*Commit: 098c31b | Date: 2026-01-22*

Tune how many chapters are checked simultaneously.

**Settings:**
- `KEY_TRACKER_PARALLELISM`: 1-12 parallel checks (default: 6)
- Per-manga mutex locking for thread safety
- Flow-based batch processing

---

### Configurable Cache TTL
*Commit: e529452 | Date: 2026-01-22*

Tune memory cache expiration times.

| Cache Type | Default | Range |
|------------|---------|-------|
| Details    | 5 min   | 1-60 min |
| Pages      | 10 min  | 1-120 min |
| Related    | 10 min  | 1-120 min |

*Note: Requires app restart to take effect.*

---

### Memory Management Improvements
*Commit: 129bef3 | Date: 2026-01-21*

Proper memory pressure handling in page holders.

- `TRIM_MEMORY_MODERATE` or higher: Recycle image view
- `TRIM_MEMORY_BACKGROUND` or higher: Apply downsampling

---

## Network & Connectivity

### Persistent Proxy Blacklist
*Commit: 1d56aa7 | Date: 2026-01-22*

Remember failed image proxies across app restarts.

**Features:**
- SharedPreferences storage with timestamps
- 6-hour TTL for blacklist entries
- Thread-safe synchronized access
- Clear/remove individual entries

---

### Exponential Backoff for Rate Limits
*Commit: 4088fb9 | Date: 2026-01-22*

Smarter retry logic for rate-limited requests.

**Features:**
- Configurable max retries (default: 3)
- Formula: `min(maxDelay, initialDelay × 2^retryCount) ± 25% jitter`
- Respects Retry-After header
- Default delays: 1s initial, 30s max

---

### Persistent Mirror Blacklist
*Commit: c906f70 | Date: 2026-01-22*

Remember failed mirror sources.

**Features:**
- 24-hour TTL for blacklist entries
- SharedPreferences persistence
- Manual clear/remove methods
- Graceful handling of removed sources

---

### WebView Proxy Error Localization
*Commit: aafde60 | Date: 2026-01-21*

User-friendly error messages for proxy issues.

- New `ProxyWebViewUnsupportedException` class
- Localized string `proxy_webview_not_supported`
- Proper exception mapping for display

---

## Database & Storage

### Database Schema Migration Safety
*Commit: 639ddfa | Date: 2026-01-22*

Graceful handling of incompatible database schemas.

- `fallbackToDestructiveMigration` for Room
- Prevents crashes from old Kotatsu installations
- Fresh database creation when migrations fail

---

### Configurable Compression Formats
*Commit: 74dba9f | Date: 2026-01-22*

Flexible image compression options.

**Formats:**
- PNG (lossless)
- JPEG (lossy, quality 0-100)
- WEBP (lossy, modern efficiency)
- WEBP_LOSSLESS

**Features:**
- API level handling for WebP formats
- Quality parameter support
- Backward compatible API

---

## Code Quality & Bug Fixes

### TODO/FIXME Cleanup
*Commits: 0df96fa, f95e139 | Date: 2026-01-21*

Systematic cleanup of technical debt.

**Fixed Issues:**
- `MultiSelectListPreference.setDefaultValueCompat()` now properly checks for null/empty values
- Missing chapter handling with "Select chapter" Snackbar action
- Proper documentation for captcha retry logic
- Download notification cache behavior documented

---

### Reader Menu Info Action
*Commit: 388ed31 | Date: 2026-01-21*

Fixed non-functional info button in reader.

- Added `Callback` interface to `ReaderMenuProvider`
- Implemented `onOpenMangaInfo()` in `ReaderActivity`
- Proper navigation to manga details screen

---

### External Plugin Related Manga Support
*Commit: f4dc2ec | Date: 2026-01-22*

Support for related manga from external plugins.

**Features:**
- New content URI: `content://{authority}/manga/related/{url}`
- `isRelatedMangaSupported` capability flag
- Graceful fallback for unsupported plugins

---

### OkHttp 5.x Compatibility
*Commit: 0733353 | Date: 2026-01-21*

Updated to proper public API.

- Replaced deprecated `PlatformRegistry.applicationContext`
- Now uses `OkHttp.initialize()` for OkHttp 5.x

---

### Java 17 Target Update
*Commit: cd4df25 | Date: 2026-01-22*

Modern Java compatibility.

- Updated from Java 11 to Java 17
- Optimized DNS prefetch initialization

---

### Parser Update
*Commit: ac2e165 | Date: 2026-01-22*

Updated kotatsu-parsers to v1.2 tagged release for better stability.

---

## Configuration & Settings

### Configurable JS Timeout
*Commit: 42510b3 | Date: 2026-01-22*

Tune WebView JavaScript evaluation timeout.

- `KEY_JS_TIMEOUT`: 2000-30000ms (default: 4000ms)
- Helps with complex CloudFlare challenges

---

## Summary Statistics

| Category | Count |
|----------|-------|
| Major Features | 7 |
| Performance Optimizations | 5 |
| Network Improvements | 5 |
| Database Enhancements | 3 |
| Bug Fixes | 8 |
| **Total Commits** | **28** |

---

## Building

```bash
./gradlew assembleDebug
```

## License

See [LICENSE](LICENSE) for details.

---

*Last updated: January 22, 2026*
