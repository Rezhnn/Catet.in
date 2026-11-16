# Catet.in — Product Requirements Document & Implementation Plan

Platform: Android only · Status: Draft v2 · Date: 2026-11-16 · Audience: AI coding agent · Language: English

## 0. How to use this document (read first)

This document is the single source of truth for building **Catet.in**. Work through it in order. **Phase 0 (section 17) is mandatory before any feature code.**

Evidence labels used throughout:

- **\[VERIFIED\]** — read directly in the upstream README or CLAUDE.md at the time of writing.
- **\[UNVERIFIED\]** — claimed by the owner or inferred, but NOT yet confirmed in upstream code or runtime tests. You must verify it in Phase 0 / the designated spike and record the result in `docs/UPSTREAM_NOTES.md` or `docs/AI_EVAL.md`.
- **\[DECISION\]** — a product/engineering decision. If marked "OPEN", ask the owner before building on it; otherwise follow the stated default.

Owner's standing engineering rules (apply to every task):

1. **TDD, proven.** Write the failing test first, run it, watch it fail for the right reason, then implement, then run it green. Do not assume a solution is correct; prove it with a test run and keep the output as evidence.
2. **Assume your world knowledge is stale.** Before choosing a library, API, version, or Google/Android policy, search for current documentation. Do not rely on memory for versions, permissions behavior, or API signatures.
3. **No backwards compatibility** unless explicitly requested. When something is renamed or replaced (SMS → notification, `.pennywisebackup` → Catet.in format), update every downstream consumer. Do not leave shims, aliases, or deprecated paths.
4. **SOLID, DRY, KISS, Clean Code.** Small functions, meaningful names, no over-engineering.
5. **Error handling and logging.** Robust error handling. Logging uses low-cardinality, stable message strings with structured fields, e.g. `log.info("gmail.sync.completed", fields)`; never interpolate variable data into the message string. **Never log notification text, email bodies, amounts, account numbers, names, or tokens.**
6. **Performance & memory discipline:** Target baseline hardware is a **4 GB RAM** Android phone. Keep the core app lightweight; optimize memory and startup deterministically.

## 1. Product summary

**Catet.in** (Indonesian slang: "catat" + "-in", roughly "note it down for me") is a private, on-device Android expense and income tracker engineered to run stably with all features on **4 GB RAM** smartphones. It automatically builds a transaction timeline from (a) payment notifications posted by banking/e-wallet apps and (b) read-only Gmail messages (receipts, bank emails). Parsing, categorization, rules, dedupe, subscription detection, storage, and analytics are all **deterministic and local**. An optional on-device AI assistant (**Qwen3.5-0.8B**, preceded by a deterministic Indonesian keyword front-layer and isolated in a separate Android process with lazy load/unload) answers natural-language questions about the user's own data in **Indonesian**.

It is derived, feature-for-feature, from the open-source **PennyWise AI** project (https://github.com/sarim2000/pennywiseai-tracker), with four deliberate changes:

1. SMS parsing is **replaced by notification reading**. No SMS permission is used anywhere.
2. **Gmail read-only ingestion is added** as a second transaction source.
3. The assistant model changes from **Qwen 2.5 (1.5B)** to **Qwen3.5-0.8B**, fronted by a deterministic Indonesian keyword router, running in a dedicated `:ai` process with strict lazy load/unload when entering/leaving the AI Assistant tab.
4. **All user-facing application output is in Indonesian (Bahasa Indonesia)**, formatted for `id-ID` / IDR, and budgeted for **4 GB RAM** devices.

**Distribution and usage \[DECISION\]:** personal use only. Code lives in a **private GitHub repository**; the app is **sideloaded** as an APK. It is not published to Google Play. Do not add features whose only purpose is public distribution (billing, in-app review/update, store listings).

**Market & output language \[DECISION\]:** Indonesia. Default currency IDR (`Rp`), Indonesian banks and e-wallets, Indonesian-language notification and email text (with some English). **All user-facing application UI copy, system guidance, and AI assistant outputs MUST be in Indonesian.**

## 2. Goals and non-goals

### Goals

- **G1 — Zero-effort capture from notifications.** A payment notification from an allow-listed app becomes a correct transaction within seconds, with no user action.
- **G2 — Gmail read-only capture.** Receipts and bank emails from allow-listed senders become transactions via the Gmail API with the single scope `gmail.readonly`.
- **G3 — PennyWise-class feature parity** on top of the new pipeline (section 10).
- **G4 — 100% on-device.** Financial data never leaves the phone. Network use is limited to an explicit allow-list (section 11).
- **G5 — Lightweight, isolated AI assistant on Qwen3.5-0.8B.** Fronted by a deterministic Indonesian keyword router (so common phrases like *"pengeluaran bulan ini"* and *"belanja makan minggu lalu"* execute with zero LLM invocation), hosted in a separate Android process (`:ai`) so an OS low-memory kill of the LLM never kills the notification listener, and strictly lazy-loaded only when the user clicks the AI Assistant tab (unloaded on exit/memory pressure).
- **G6 — Correct money handling.** Currency-tagged amounts, no cross-currency sums, no duplicates when one payment arrives via notification and email.
- **G7 — Stable operation on 4 GB RAM devices.** All features (notification capture, Gmail backfill, analytics, and on-device AI chat) run reliably without OOM crashes or background listener death on a 4 GB RAM Android device.
- **G8 — Indonesian application output.** All UI screens, notifications, error states, and AI assistant responses are rendered in natural Indonesian.

### Non-goals

- Reading SMS (`READ_SMS`/`RECEIVE_SMS` must not appear in the merged manifest).
- iOS app, web app, Kotlin Multiplatform `shared` module.
- Google Play distribution, PennyWise Pro, license keys, Play Billing, in-app updates, in-app review.
- Any telemetry, analytics, crash-reporting SDK, cloud sync, or backend server.
- Any Gmail write scope (no send, modify, compose, labels, delete).
- Using an LLM for parsing, categorization, rules, subscription detection, or dedupe. **The LLM is only for the chat assistant when the deterministic Indonesian keyword front-layer does not match.**
- Loading the LLM at app startup, during background notification capture, or during Gmail sync.

## 3. Upstream analysis: what PennyWise is

Source: upstream README and CLAUDE.md (read in full) and AGENTS.md (a thin pointer to CLAUDE.md).

**Identity and license \[VERIFIED\].** Repository `sarim2000/pennywiseai-tracker`, licensed **AGPL-3.0**. Android 8.0+ (API 26), JDK 21, Android Studio Ladybug or newer, Kotlin 2.3.x per README badge.

**Modules \[VERIFIED\].** `app` (Android UI), `parser-core` (pure-Kotlin bank SMS parsers with no Android dependencies), `shared` (Kotlin Multiplatform), `iosApp`, `pennywise-web`.

**Stack \[VERIFIED\].** Kotlin, Jetpack Compose + Material 3, MVVM with Clean Architecture, StateFlow (unidirectional data flow), Hilt, Room, Coroutines, WorkManager (background SMS scanning), Google AI Edge **LiteRT-LM** running Qwen 2.5 for on-device AI. UI conventions: Material You dynamic color (Android 12+), light/dark semantic color roles, 8dp grid, M3 type scale, every screen wrapped in a shared scaffold (`PennyWiseScaffold`).

**Hard constraints from upstream CLAUDE.md \[VERIFIED\] — Catet.in inherits all of them:**

1. Money is always currency-tagged. Format only via `CurrencyFormatter.formatCurrency(amount, currencyCode)` or the `formatBalance()`/`formatAmount()` entity extensions. The currency-less overload is `@Deprecated(ERROR)` upstream because it defaults to INR.
2. **Never sum amounts across currencies.** Use `Iterable.sumByCurrency(...)` returning a map per currency and render with `CurrencyFormatter.formatByCurrency(...)`. `Money.+` throws on currency mismatch. Guarded by `CurrencyFormatterTest`.
3. **Every backup-serialized field must have a Kotlin default value** (Room entity columns and the wrapper models in `BackupModels.kt`), otherwise old backups cannot be restored. Enforced upstream by `BackupSchemaGuardTest`. Use `@Contextual` for `BigDecimal`/`LocalDate`/`LocalDateTime`, `@Serializable` for enums.
4. **Never put PII in code, comments, tests, or anywhere.**
5. Never hand-edit version numbers or changelogs; the release script owns them.
6. Parser tests must use the shared `ParserTestUtils` helpers.
7. New bank parsers extend the correct base class (`BaseIndianBankParser` / `UAEBankParser` / `BankParser`) and are registered in `BankParserFactory`.

**Verification gate \[VERIFIED\].** `./init.sh` (modes: `parser`, `app`, default = everything) runs the same compile/test gate as CI; CI additionally assembles a debug APK. UI/runtime changes also require an on-device check. `init.sh` locates a JDK 21.

**Release \[VERIFIED\].** `scripts/release.sh patch|minor|major --yes` owns `versionCode`/`versionName` and fastlane changelogs, builds APKs, tags, pushes, and cuts the GitHub release. There is a `standard` flavor and an F-Droid flavor that strips Google Play services.

**Feature set \[VERIFIED, from README\].**

- Smart SMS parsing with real-time detection; multi-currency with exchange-rate management.
- On-device AI assistant ("What did I spend on food?").
- Auto-categorization and a **Smart Rules Engine**: one or more `When` conditions combined with AND/OR plus a `Then` action. Condition fields: `AMOUNT`, `TYPE`, `CATEGORY`, `MERCHANT`, `NARRATION`, `SMS_TEXT`, `BANK_NAME`, `TIME`/`HOUR`, `DAY_OF_WEEK`, `DAY_OF_MONTH`, `DATE`, `ACCOUNT` (stored as a `BankName||Last4` composite key; only `EQUALS`/`NOT_EQUALS`). Operators include contains/equals/starts-with, `<`/`>`/`=`, before/after/at-or-after/at-or-before, is/is-any-of/is-not. Actions: Set, Append, Prepend, Clear, Block (prevent saving). Rules are stored locally as JSON.
- Budget tracking: multiple budget groups (Limit/Target/Expected), daily allowance, category-level budgets.
- Analytics: bar charts, line trends, heatmaps, category breakdowns, top merchants, custom date ranges.
- Multi-account and balance tracking with balance history.
- Subscription detection (recurring-payment detection with due-date alerts).
- Custom categories (colors, icons); manual transactions; search and filters (category, merchant, period, currency, type).
- Biometric app lock with timeout; home-screen widgets (budget progress, recent transactions); CSV export; full backup/restore (`.pennywisebackup`); Material You theming with cover styles; guided onboarding (spotlight tutorial).
- Pro tier (unlimited rules, PDF statement imports, CSV export, account merge, scheduled backups) — **not carried over**; Catet.in unlocks everything.

**Network surface of upstream \[VERIFIED\].** (1) Google Play services (billing, in-app updates, review) on the Play build; (2) exchange rates from `open.er-api.com` (carries only currency codes, cached locally); (3) a one-time \~1.5 GB model download from Cloudflare R2, SHA-256 pinned, only when the user opens AI chat and agrees; (4) an optional "report a parsing problem" flow that opens a PennyWise-operated site with the SMS text and an encrypted device identifier pre-filled; (5) plain links. No analytics/crash SDKs.

**Documentation map \[VERIFIED as referenced by CLAUDE.md; contents NOT yet read\].** `docs/architecture.md`, `docs/design.md`, `docs/state-management.md`, `docs/backup-format.md`, `docs/adding-bank-parsers.md`, `docs/parser-test-standards.md`, `docs/database-migrations.md`, `docs/BANK_SUPPORT.md`, `docs/supported-banks.json`, `docs/planned-features.md`, `docs/prd-unified-currency.md`, `docs/RELEASE.md`. CLAUDE.md also names three subagents: `backup-maintainer`, `parser-author`, `parser-triage`.

**Known gaps and discrepancies — resolve in Phase 0 \[UNVERIFIED\]:**

- This PRD was written from the README and CLAUDE.md only. The `docs/` directory and the source tree were **not** read. Anything this PRD says about internal class names beyond those listed above is a design proposal, not a fact.
- The owner's clarification states upstream also has voice dictation via native Android speech input, a `:feature:learnedcategories` module of learned category mappings, and "176 banks across 26 countries". None of these appear in the README or CLAUDE.md. The README says 157 banks / 24 countries and CLAUDE.md says 144 / 23 (the numbers disagree with each other). Verify in code; inherit voice dictation and learned categories **only if they exist**.
- How the current assistant builds its prompt (full transaction context vs. tool/query approach) is unknown. Read it before designing section 9.

## 4. Licensing and legal \[DECISION\]

- Upstream is **AGPL-3.0**. A fork or any derived code is also AGPL-3.0. For private personal use this imposes no practical burden; **if Catet.in is ever distributed or offered to others, source must be provided under AGPL-3.0.** Keep upstream `LICENSE`, copyright headers, and an attribution section in the README.
- Upstream has a `TRADEMARKS.md`. Do not use the PennyWise name, logo, banner, or store assets. Rename every user-visible string, package, class prefix (e.g. `PennyWiseScaffold` → `CatetScaffold`), and resource.
- **Default: Option A — fork and modify** (fast, keeps parser-core and test infrastructure; repository license stays AGPL-3.0). **Option B — clean-room rewrite** (study architecture only, write all code fresh, free to choose another license) is slower; choose it only if the owner later wants a non-AGPL license. OPEN: confirm Option A.
- **Model weights:** Qwen3.5-0.8B license (expected Apache-2.0 per Qwen model releases — **\[UNVERIFIED\] confirm the exact `LICENSE` file on the official Qwen Hugging Face repository in Phase 0** and record it in `NOTICE`).
- Gmail data handling must respect Google's API Services User Data Policy (limited use): data stays on device, is used only to provide the user-facing feature, and is not transferred or humanly read.

## 5. Scope matrix

**KEEP (re-point to the new ingestion pipeline):** multi-currency (default IDR); rules engine; auto-categorization; custom categories; budgets; analytics and charts; multi-account and balance tracking; subscription detection; manual transactions; search and filters; biometric lock; widgets; CSV export; backup/restore; Material You theming and cover styles; onboarding spotlight; `parser-core` as a pure-Kotlin module; the verification gate (`init.sh`) and CI.

**CHANGE:**

- SMS ingestion → notification ingestion (section 6). Remove the SMS permission, SMS receiver, SMS scanning worker, and the SMS inbox backfill.
- Parsers: Indian/UAE/etc. bank parsers → Indonesian banks and e-wallets. Parser input becomes a normalized `NotificationInput` or `EmailInput`, not an SMS.
- Rule field `SMS_TEXT` → `RAW_TEXT` (the text of the source artifact); `BANK_NAME` → institution name; add a `SOURCE` condition (NOTIFICATION / EMAIL / MANUAL).
- Assistant model, runtime, and process architecture → **Qwen3.5-0.8B** (4-bit) fronted by a deterministic Indonesian keyword router, running in an isolated `:ai` Android process with tab-triggered lazy load/unload (section 9).
- Backup file extension and format name → Catet.in's own; same default-value rule.
- Default locale/currency and application output language → `id-ID` formatting, IDR, and **Indonesian (`values-in` / default Indonesian strings) for all user-facing application output** \[DECISION\].
- App id, app name, launcher icon, theme names → Catet.in. Proposed application id `com.catetin.app` \[DECISION OPEN\].
- Database: new app, no existing installs → start a **fresh baseline schema (v1)**; do not carry upstream migrations or SMS-era columns (no backward compatibility).

**DROP:** `iosApp`, `pennywise-web`, KMP `shared` (first verify whether `app` depends on it; if so, inline only the needed code); PennyWise Pro, license-key flow, Play Billing, in-app updates, in-app review; the "report a parsing problem" flow that sends text to a third-party site (replace with a local "copy sanitized sample" action); Crowdin integration; fastlane store metadata; the F-Droid flavor (single flavor, sideload); Discord/support links.

**ADD:**

- Notification ingestion with an allow-list and **Source Health** screen (section 6).
- **Gmail read-only ingestion** (section 7), chunked for low memory footprint on 4 GB RAM devices.
- **Cross-source dedupe** and a **Review Queue** (section 8).
- **Deterministic Indonesian keyword intent router** in front of the LLM (section 9).
- **Process isolation (`android:process=":ai"`) and lazy load-unload** for the LLM runtime (section 9).
- **Unparsed bucket** for allow-listed notifications/emails the parsers did not understand (feeds parser development).
- A debug-only **sample capture** tool that exports PII-masked samples for fixtures (section 13).

**DEFER (optional, Phase 6):** PDF bank-statement import (upstream Pro feature); scheduled automatic backups; account merge.

## 6. Notification ingestion (replaces SMS)

Implement with Android's `NotificationListenerService`. Verify current platform behavior and policy by searching current Android documentation before implementing (see Phase 0 research tasks).

- **N-1 Listener and consent.** `CatetNotificationListener` extends `NotificationListenerService`; the manifest service entry requires the system-only `BIND_NOTIFICATION_LISTENER_SERVICE` permission. The user grants "Notification access" in system settings; the app deep-links to the notification-listener settings screen and detects whether access is granted.
- **N-2 Allow-list first, read second.** Maintain a user-editable allow-list of package names (banks, e-wallets, marketplaces). A notification from any package **not** on the allow-list is dropped immediately, before its text is read, stored, or logged. Ship a default allow-list populated from Phase 1 discovery (section 13), not from guesses.
- **N-3 Extraction.** From each allow-listed notification extract: package name, notification key, channel id, post time, title, text, big text, sub text, and text lines. Normalize whitespace and unicode; build one canonical `NotificationInput`.
- **N-4 Re-post suppression.** Apps often re-post/update the same notification. Compute an idempotency key from (package, notification key or stable text hash, post-time bucket) and drop repeats. Acceptance: posting the same notification five times yields one `SourceEvent` and at most one transaction.
- **N-5 Pipeline.** capture → normalize → parse (deterministic, `parser-core`) → rules engine → dedupe (section 8) → persist. The listener callback must do minimal work and hand off to a background worker or a bounded channel; no parsing or database work on the binder thread.
- **N-6 Parse outcomes.** Parsing returns a sealed result: `Parsed(transaction draft, confidence)`, `Ignored(reason)` for non-transactional content (OTP, login alerts, promos, marketing, "verification code"), or `Unparsed` for allow-listed content that looks financial but no parser understood. `Unparsed` items go to an **Unparsed bucket** screen with the raw text so the owner can create a rule, add a sample for a new parser, or dismiss.
- **N-7 Raw text retention \[DECISION\].** Rules (`RAW_TEXT`) and re-parsing need the original text. Default: keep raw text in the app-private database only, protected by the app lock, excluded from Android backup/device transfer, with a setting to purge raw text older than N days (default 90). OPEN: owner may prefer encryption-at-rest of this column; if so, evaluate a maintained encrypted-database option via current-docs search.
- **N-8 Historical limits.** Android does not provide past notifications to a newly enabled listener beyond those currently active. On first enable, ingest `getActiveNotifications()` once. Document in the UI (in Indonesian) that gaps before install are filled by Gmail backfill or manual entry. Do not promise historical import from notifications.
- **N-9 Source Health screen.** Shows (in Indonesian): notification-access granted yes/no; listener connected yes/no; per-allow-listed-app last capture time and counts (captured / ignored / unparsed); a Gmail connection status row; battery-optimization status for the app; and guided steps. Handle `onListenerDisconnected` by requesting rebind. Provide OEM-specific guidance text for aggressive background killers (Xiaomi, Oppo, Vivo, Samsung and similar) — **\[UNVERIFIED\] verify current behavior and recommended user steps on the owner's actual device in Phase 1.**
- **N-10 Sideload restriction \[UNVERIFIED\].** On recent Android versions, sideloaded apps may be blocked from enabling notification access until the user allows "restricted settings" for the app. Verify on the owner's device and Android version and add an Indonesian onboarding step if required.
- **N-11 Manifest hygiene.** No SMS, contacts, location, accessibility-service, or `QUERY_ALL_PACKAGES` permissions. A CI/lint check on the merged manifest enforces the forbidden list.
- **N-12 Process isolation from AI inference \[DECISION\].** `CatetNotificationListener` runs in the main application process (`< 120 MB` PSS target) and **must never** host or initialize the LLM engine. LLM inference runs strictly inside the separate `:ai` process (section 9, A-3) so an OOM / `lmkd` kill of the model never terminates `CatetNotificationListener`.

## 7. Gmail read-only ingestion

- **G-1 Scope.** Exactly `https://www.googleapis.com/auth/gmail.readonly`. No other Gmail scope, ever. (Context: Gmail read scopes are classified "restricted" by Google; public distribution would require verification plus an annual third-party security assessment. This is why Catet.in is personal-use only. Personal-use apps under 100 users are exempt from verification and may be used by clicking through the "unverified app" warning.)
- **G-2 Google Cloud setup (owner-performed, document in `docs/GMAIL_SETUP.md`).** Owner creates their own Google Cloud project, enables the Gmail API, configures the OAuth consent screen as External, adds the owner's account, creates an **Android** OAuth client (package name + signing-key SHA-1 of the build used on the phone). **Publishing status must be set to "In production"** (unverified). Apps left in "Testing" get refresh tokens that expire after 7 days. Verify this behavior and the exact console steps against current Google documentation during Phase 0.
- **G-3 Auth library \[DECISION OPEN, research task\].** Choose the current Google-recommended on-device authorization mechanism for Android that can obtain `gmail.readonly` access without a backend server (candidate: the Play Services identity authorization API; fallback: AppAuth with PKCE). Constraints: no backend; tokens never leave the device; sign-out and revoke must work; re-authorization must be recoverable without data loss. Record the chosen approach and the evidence in `docs/UPSTREAM_NOTES.md`.
- **G-4 Token handling.** Store any long-lived credential only in Android Keystore-backed storage. Never in plain SharedPreferences, logs, backups, or source control. On `invalid_grant`/revocation, set Gmail status to "Perlu hubungkan ulang" (Reconnect needed) and surface a banner; keep all previously ingested data.
- **G-5 Sync strategy (memory-bounded for 4 GB RAM).** WorkManager periodic work (platform minimum interval) plus a manual "Sinkronkan sekarang" (Sync now). Use incremental sync via the Gmail history mechanism with a stored `historyId`; when the stored history id is too old (not found), fall back to a bounded re-list. Fetch and decode messages in small streaming chunks (e.g., 20–25 messages per batch, persisting and releasing memory after each chunk) so 90/180-day backfills stay well within 4 GB RAM device limits. Respect rate limits with exponential backoff and jitter on 429/5xx. Fully offline-safe: failures never lose state.
- **G-6 Query narrowing.** Fetch only messages matching a user-editable **sender allow-list** (addresses/domains) and optional subject keywords, using Gmail search syntax server-side. Do not download the mailbox.
- **G-7 Message decoding.** Request full message format; decode base64url parts; walk multipart trees; prefer `text/plain`, fall back to `text/html` converted to text (strip scripts/styles, resolve entities, collapse whitespace); handle declared charsets. Output a canonical `EmailInput(messageId, threadId, internalDate, from, subject, bodyText)`.
- **G-8 Parsing.** Deterministic per-sender email parsers in `parser-core` behind the same result type as notifications (Parsed / Ignored / Unparsed). Emails can carry fields notifications lack (merchant, order/reference id, itemization, balance); the parser must extract them when present.
- **G-9 Storage and retention.** Persist messageId, threadId, internalDate, sender, parse status and parsed fields. Persist body text only as needed for rules/re-parse (extracted plain text, never raw HTML), under the same purge policy as N-7. Never store attachments.
- **G-10 Backfill window.** On first connect the user picks 30, 90 or 180 days (default 30). Initial backfill runs as resumable chunked work.
- **G-11 Disconnect.** Settings has "Putuskan koneksi Gmail" (Disconnect Gmail): revokes/clears credentials locally, stops work, keeps or deletes imported emails per user choice, and links the user to the Google account permissions page to revoke the grant.

## 8. Cross-source dedupe and Review Queue

The same payment commonly arrives as a notification from the e-wallet, a notification from the bank, and an email receipt. One real-world payment must equal one transaction.

- **D-1 Model.** Every captured artifact is a `SourceEvent` (type NOTIFICATION / EMAIL / MANUAL / IMPORT) linked to exactly one `Transaction`. A transaction may have many source events.
- **D-2 Idempotency.** Unique constraint on (source type, external id) so reprocessing the same artifact can never create a second transaction.
- **D-3 Match keys.** Strong key: a payment/reference number present in more than one artifact. Weak key: same direction + same amount + same currency + same institution (or paired institutions, e.g. wallet ↔ bank) + time within a configurable window (default ±10 minutes) + compatible account hint.
- **D-4 Policy.** Strong match → auto-merge. Weak match with a single candidate → auto-merge. Weak match with multiple candidates, or conflicting merchant/amount → create `NEEDS_REVIEW` entry in the **Review Queue** and do not merge silently.
- **D-5 Merge result.** Merged transaction takes the richest data per field (email usually wins merchant/reference; the earliest notification wins timestamp). All source events are kept for audit. **Unmerge** is supported.
- **D-6 Transfers and wallet top-ups.** A top-up from a bank to an e-wallet generates two legitimate artifacts in two institutions; model as a Transfer between own accounts, not an expense plus an income. Detect via paired-institution matching rules and surface ambiguous cases in the Review Queue.
- **D-7 Acceptance.** Table-driven tests (section 15) cover: exact duplicate, delayed email, same amount at different merchants within the window, two genuine identical purchases minutes apart (must stay two), refund, and top-up transfer.

## 9. On-device AI assistant (Qwen3.5-0.8B)

**Isolation rule (non-negotiable).** The LLM exists only for the chat assistant when the deterministic Indonesian keyword front-layer does not match. Parsing, categorization, rules, dedupe, subscription detection, budgets, and analytics are deterministic Kotlin. Enforce structurally: the `parser-core` and domain/data modules must not depend on the `ai` module, and an architecture test fails the build if they do. The tracker must be fully usable without ever downloading the model.

**Model facts & Phase 0 verification items:**

- **Qwen3.5-0.8B** is a ~0.8B-parameter compact multilingual instruct model suited for low-RAM mobile deployment, supporting Indonesian and English (**\[UNVERIFIED\]** verify exact parameter count, native context length, license file, and official sampling parameters from the `Qwen/Qwen3.5-0.8B` Hugging Face model card in Phase 0).
- **Runtime packaging \[UNVERIFIED — verify in Phase 0 / Phase 5 spike\]:** Check whether a pre-converted `.litertlm` package exists in `litert-community` or Google AI Edge LiteRT-LM for Qwen3.5-0.8B, alongside official 4-bit GGUF quantizations (`Q4_K_M` / `Q4_0`, expected ~500–650 MB) for `llama.cpp`. Note that Qwen3.5 architectures use hybrid Gated DeltaNet + GQA layers; confirm exact LiteRT-LM or `llama.cpp` version support in Phase 0 before locking the runtime engine.
- **Thinking-mode control \[UNVERIFIED — verify in Phase 0\]:** Qwen3.5 models may emit `<think>...</think>` reasoning blocks unless explicitly disabled via chat template flag (`enable_thinking=false`) or `/no_think` system instruction. Disable thinking mode by default to keep latency low and fit within the tight token/memory budget on 4 GB RAM devices.

**Requirements:**

- **A-1 Runtime choice \[DECISION, default below\].** Keep upstream's LiteRT-LM runtime if a verified `.litertlm` export for Qwen3.5-0.8B exists and runs stably within the 4 GB RAM memory ceiling; otherwise implement `llama.cpp` + 4-bit GGUF (`Q4_K_M` or `Q4_0`) with `mmap` enabled for predictable native memory usage. Put all inference behind a `LocalLlmEngine` interface so swapping implementations requires zero changes to UI, IPC, or query execution.
- **A-2 Model download & 4 GB RAM variant gating.** On demand only, after explicit consent in the AI screen. Show the exact file size (in Indonesian) before download (read from the model host at implementation time — do not hardcode a guess). Resumable; stored in app-private storage; deletable from Settings. Integrity: after the first verified download, compute the file's SHA-256 and pin it in `ModelCatalog`; verify on every download. **Default and sole recommended variant for 4 GB RAM devices: 4-bit quantized (`int4` / `Q4_K_M`).** Any larger variant (`int8` / `Q8_0`) must be hidden or blocked unless `ActivityManager.getMemoryInfo().totalMem >= 6 GB` so a 4 GB phone cannot trigger an OOM crash. Replace upstream's model hosting constants with the Qwen3.5-0.8B source; remove upstream's Qwen 2.5 artifact entirely.
- **A-3 Separate Android process (`android:process=":ai"`) \[DECISION / UNVERIFIED resilience\].**
  - **Architecture \[DECISION\]:** Host the LLM inference service (`AiInferenceService`) in a dedicated child process declared with `android:process=":ai"` in `AndroidManifest.xml` (do **not** use `android:isolatedProcess="true"` because isolated processes cannot directly read model files from app-private storage without complex FD passing, whereas a named `:ai` process shares the app UID/storage while maintaining an independent Linux PID and native/Dalvik heap). Communicate between the main UI process and `:ai` via a bound AIDL / `Messenger` interface wrapped behind `LocalLlmEngine`.
  - **OOM isolation hypothesis \[UNVERIFIED — verify in Phase 5\]:** Because `:ai` runs in its own OS process separate from the primary process hosting `CatetNotificationListener`, Android's Low Memory Killer (`lmkd`) can terminate `:ai` under RAM pressure without killing the main app or interrupting background notification capture. Verify this behavior empirically on a 4 GB RAM device/emulator in Phase 5.
  - **Graceful recovery:** If `:ai` dies (`onServiceDisconnected` / `DeadObjectException`), the main app catches the disconnect cleanly, marks the engine state as `UNLOADED`, displays a non-intrusive Indonesian message (*"Memori perangkat penuh, asisten AI diistirahatkan sementara. Ketuk untuk memuat ulang."*), and allows lazy re-initialization on the next user action.
- **A-4 Lazy load–unload lifecycle \[DECISION\].**
  - **Zero baseline overhead:** Launching Catet.in, capturing notifications, syncing Gmail, or viewing Home/Budgets/Analytics **never** starts the `:ai` process or loads `Qwen3.5-0.8B` into RAM.
  - **Load trigger:** Start `:ai` and load the model weights **only when the user actively clicks/enters the AI Assistant tab** (and only if the model is already downloaded; if not downloaded, show the Indonesian consent/download card without starting `:ai`).
  - **Unload triggers:**
    1. *Leaving the AI Assistant tab:* When the user switches to any other tab or navigates away, start a short grace timer (default 15 seconds) and then **unload the model from native memory and unbind/stop the `:ai` process**.
    2. *App backgrounded:* Immediately unload and stop `:ai` on `Lifecycle.Event.ON_STOP`.
    3. *System memory pressure:* Immediately unload and stop `:ai` upon `ComponentCallbacks2.onTrimMemory` (`TRIM_MEMORY_RUNNING_LOW`, `TRIM_MEMORY_RUNNING_CRITICAL`, or `TRIM_MEMORY_UI_HIDDEN`).
- **A-5 Deterministic Indonesian keyword front-layer (fast-path before LLM) \[DECISION\].**
  - Run a deterministic Indonesian keyword/phrase intent router (`IndonesianKeywordIntentRouter`) **first, before calling the LLM**.
  - Common, well-structured Indonesian queries — explicitly including *"pengeluaran bulan ini"*, *"belanja makan minggu lalu"*, *"pemasukan bulan ini"*, *"total jajan hari ini"*, *"pengeluaran transportasi minggu ini"*, *"transaksi terbesar bulan ini"*, and *"saldo saat ini"* — are parsed deterministically into the **exact same `QueryIntent` schema** used by the LLM, executed against Room, and rendered in Indonesian **without invoking or waiting for the LLM** (0 ms inference latency, 0 MB LLM RAM).
  - If a query is complex, conversational, or does not match the deterministic Indonesian grammar rules, route it to `Qwen3.5-0.8B` (once loaded). The deterministic router also serves as a safety fallback if LLM JSON generation fails after one repair retry.
- **A-6 "LLM plans, code computes" & Context budget.**
  - Flow: user question → `IndonesianKeywordIntentRouter` (fast-path) → if unmatched, `Qwen3.5-0.8B` produces a **structured query intent** (JSON validated against a strict schema: `metric`, `filters`, `period`, `grouping`, `currency`) → deterministic Kotlin executes it against Room (per-currency aggregation, honoring the hard constraints) → response is phrased in Indonesian (via deterministic Indonesian response templates for fast-path queries, or via `Qwen3.5-0.8B` receiving only the aggregated result summary); the UI also renders the figures directly from the computed result so numbers never depend on LLM wording.
  - **Context budget (4 GB RAM safe):** Cap runtime context window at **2048 tokens** on ≤ 4 GB RAM devices (up to 4096 tokens on ≥ 6 GB devices): system prompt + schema ≤ \~600 tokens; user question + recent turns ≤ \~500 tokens; aggregated tool result ≤ \~400 tokens; generation ≤ \~300 tokens. Truncate oldest turns first; never pass raw transaction lists into the prompt.
- **A-7 Structured output & Sampling.** Prefer constrained JSON output / function calling if supported by the runtime; validate against schema. Invalid LLM JSON → one repair retry → fall back to `IndonesianKeywordIntentRouter` partial match → else a clear Indonesian message (*"Maaf, saya belum memahami pertanyaan itu. Coba tanyakan misalnya: 'Pengeluaran bulan ini' atau 'Belanja makan minggu lalu'."*). Apply low-temperature sampling (`temperature` ~0.1, `top_p` ~0.9, `top_k` ~40, thinking disabled — confirm exact Qwen3.5-0.8B defaults in Phase 0).
- **A-8 Output language \[DECISION — LOCKED\].** **All assistant outputs, UI labels, cards, example prompts, and error/status messages MUST be in Indonesian (`id-ID`).** The system prompt instructs `Qwen3.5-0.8B` to reply strictly in concise, natural Indonesian.
- **A-9 Prompt-injection hygiene.** Merchant names, narrations, and email text are attacker-influenced data. Never concatenate them into instructions; pass them only as delimited data fields, strip control tokens (`<|im_start|>`, `<|im_end|>`, `<think>`, etc.), and keep the assistant strictly read-only (no tools with side effects).
- **A-10 Eval harness & 4 GB RAM profiling.** A golden set of at least 40 Indonesian (plus optional English code-switched) questions — covering both deterministic fast-path queries and LLM-routed queries — runnable as an on-device instrumented test. Track fast-path hit rate, intent accuracy, JSON validity rate, latency, and peak PSS RAM of both main and `:ai` processes on a **4 GB RAM** device/emulator. Report results in `docs/AI_EVAL.md`.

## 10. Inherited core features — acceptance summary

Behavior stays as upstream unless stated. For every feature: (1) read the upstream implementation and docs first, (2) re-point it from SMS-derived fields to `SourceEvent`/`Transaction`, (3) keep upstream tests green or update them deliberately, (4) add tests for source-awareness, Indonesian UI copy, and bounded memory usage on 4 GB RAM devices.

- **Accounts and balances.** Account identity = institution + last-4/account hint when present in the source text. Balance snapshots come from notifications/emails that include a balance. Wallets (GoPay, DANA, OVO, etc.) are accounts too. Unmerged duplicates in accounts are the owner's problem to merge manually; account merge is deferred.
- **Categorization and rules.** Same engine; fields renamed as in section 5; add `SOURCE` condition; migrate defaults to Indonesian merchant vocabulary (e.g. food delivery, ride-hailing, groceries, e-commerce, utilities, pulsa/data, e-toll, QRIS merchants).
- **Budgets.** Unchanged semantics (Limit/Target/Expected, daily allowance, category-level). IDR has no minor unit in everyday use; formatting follows `id-ID` (e.g. `Rp1.250.000`).
- **Analytics.** Unchanged charts. All totals per currency; mixed-currency lists render one figure per currency, never a blended sum. Paginate/window queries so large transaction histories never spike heap usage on 4 GB RAM phones.
- **Subscriptions.** Unchanged algorithmic recurring detection; email receipts from known subscription senders should improve detection but are not required for it.
- **Multi-currency.** Kept. Exchange-rate fetching stays optional and only runs when more than one currency is held; it is the only non-Google, non-model network call and must be user-disableable.
- **Security and UX.** Biometric lock with timeout (and app-switcher content hiding); widgets; CSV export; Material You; onboarding spotlight rewritten in **Indonesian** for the notification/Gmail setup flow.
- **Backup/restore.** New Catet.in backup format; every serialized field has a default value; schema guard test retained; backup never contains Gmail credentials or model files; optional exclusion of raw text.
- **Voice dictation / learned categories.** Inherit only if confirmed to exist upstream (section 3).

## 11. Architecture and platform constraints

**Modules.** Keep upstream's layout minus dropped modules: `:app` (UI, DI, workers, Android integrations, `:ai` service host) and `:parser-core` (pure Kotlin, zero Android dependencies: normalization, Indonesian amount/date parsing, institution registry, notification parsers, email parsers, parse-result types). Do not split further unless Phase 0 shows upstream already has additional feature modules (KISS).

**Proposed package structure in `:app` (adapt to upstream's actual conventions after reading `docs/architecture.md`):**

- `ingestion.notification` — listener (main process), allow-list repository, idempotency, bounded channel/worker hand-off.
- `ingestion.gmail` — auth, API client, sync state machine, streaming MIME decoding, chunked worker.
- `ingestion.pipeline` — orchestrates parse → rules → dedupe → persist; single entry point for every source.
- `ingestion.dedupe` — fingerprinting, matcher, merge policy.
- `ai` —
  - `ai.router` — `IndonesianKeywordIntentRouter` (deterministic front-layer in main process), `QueryIntent` schema & validator, deterministic Room `QueryExecutor`, Indonesian response templates.
  - `ai.client` — `LocalLlmEngine` client proxy, tab-lifecycle & `onTrimMemory` lazy load/unload coordinator, `ModelCatalog`.
  - `ai.service` — `AiInferenceService` declared in `AndroidManifest.xml` with `android:process=":ai"`, hosting the actual LiteRT-LM / `llama.cpp` native runtime so native heap stays strictly inside `:ai`.
- Existing feature packages (home, analytics, budgets, accounts, rules, categories, subscriptions, settings, backup) re-pointed.

**Key interfaces (proposal).** `TransactionParser<I>` with `canHandle(input)` and `parse(input): ParseResult`; `ParseResult` = `Parsed` | `Ignored` | `Unparsed`; `BankParserFactory` equivalent registers parsers per institution and per source type, following upstream's base-class conventions.

**Pattern.** MVVM + Clean Architecture, StateFlow unidirectional data flow, Hilt DI, Room, WorkManager, Coroutines — as upstream. Keep the version catalog unless a feature requires an upgrade; justify every upgrade with a current-docs search.

**Hardware baseline & 4 GB RAM memory budget \[DECISION\]:**

- **Baseline target device:** Android smartphone with **4 GB physical RAM** (API 26+).
- **Memory ceilings:**
  - Main process (`com.catetin.app`, hosting UI + `CatetNotificationListener` + Room + WorkManager): **< 120 MB PSS** during normal use (0 MB LLM native memory).
  - Child inference process (`com.catetin.app:ai`, active only while AI Assistant tab is open): **< 900 MB PSS peak** (total combined footprint < 1.05 GB).
- **Rules for 4 GB RAM stability:** Do not rely on `android:largeHeap="true"` to mask leaks; paginate transaction lists (`Paging 3` or bounded Room queries); process Gmail backfill in chunks of ≤ 25 messages; serialize background workers (`ExistingWorkPolicy.KEEP` / `APPEND_OR_REPLACE`) so notification parsing, Gmail sync, and backup never contend for peak heap simultaneously.

**SDK levels.** Keep upstream minSdk 26 unless a chosen API requires higher \[DECISION\]; set targetSdk/compileSdk to the current stable level after verifying current requirements.

**Permissions allow-list.** Service-level notification-listener binding; `INTERNET`; `POST_NOTIFICATIONS` (due-date/budget alerts); biometric. **Forbidden:** SMS, contacts, location, accessibility, `QUERY_ALL_PACKAGES`.

**Network allow-list (enforced by network security config plus a test).** Google OAuth/Gmail API endpoints; the model host (Hugging Face / configured mirror); the optional exchange-rate host. Nothing else. No analytics, crash reporting or ad SDKs.

**Build.** Single flavor, signed release build for sideload, debug build for development. Keep `./init.sh` as the verification gate and update it for renamed modules.

## 12. Data model (Room, fresh baseline v1)

Base everything on upstream's entities after reading them; the following are the additions/changes in intent. Follow upstream's `Money` and `BigDecimal` handling; do not invent a new money type.

- **Transaction** — id; amount + currency (always together); direction/type (EXPENSE, INCOME, TRANSFER); merchant; category; account id; occurredAt; narration; reference (nullable); status (CONFIRMED, NEEDS\_REVIEW); createdAt; updatedAt.
- **SourceEvent** — id; type (NOTIFICATION, EMAIL, MANUAL, IMPORT); externalId (notification idempotency key or Gmail messageId); sourceApp (package or sender); receivedAt; rawText (nullable, purgeable); parserId; parseStatus (PARSED, IGNORED, UNPARSED); transactionId (nullable); **unique (type, externalId)**.
- **Account** — institution; last4/hint; display name; currency; balance snapshots history.
- **Rule** — JSON conditions/actions as upstream; field enum updated (`RAW_TEXT`, `SOURCE`).
- **NotificationAllowlistEntry** — packageName; enabled; label; addedBy (DEFAULT/USER).
- **GmailSenderAllowlistEntry** — address or domain; subject keywords (nullable); enabled.
- **GmailSyncState** — accountHint; lastHistoryId; lastSyncAt; status (OK, RECONNECT\_NEEDED, ERROR).
- **ReviewItem** — id; kind (DUPLICATE\_CANDIDATE, LOW\_CONFIDENCE, UNPARSED); related ids; createdAt; resolution.
- **AssistantModelState** — modelId (`qwen3.5-0.8b`); variant (`int4`/`q4_k_m`); file path; sha256; downloadedAt (no chat history persisted by default \[DECISION\]).

**Rules for schema work.** Export Room schemas; every backup-serialized field has a default (hard constraint 3); no PII in test data (hard constraint 4); because there are no existing installs, define the baseline once and add migrations only after the first release build is installed on the owner's phone.

## 13. Indonesian parser specification and discovery

**Principle: discover from reality, do not hardcode from memory.** Package names, notification wording and email templates change. Every institution entry in the registry must be backed by real (sanitized) samples from the owner's device and inbox.

**Discovery mode (debug builds only).**

- *Notifications:* a screen listing packages that recently posted notifications with **package name and app label only (no text)**. The owner promotes packages to the allow-list; from then on, text is captured for those packages only. This keeps N-2 intact: non-allow-listed content is never read.
- *Gmail:* a screen showing the most frequent sender addresses/domains over the chosen window (header data only, with counts). The owner promotes senders to the sender allow-list.
- *Sample export:* a "Salin sampel tersanitasi" (Copy sanitized sample) action producing masked text for fixtures. Masking rules: account/card/phone numbers → random digits of the same length; personal names → `Test User`; reference numbers → random of the same pattern; amounts → plausible different values of the same magnitude and separator style; keep wording, punctuation and line breaks exactly (the parser must be tested on real structure).

**Candidate institutions \[UNVERIFIED — starting hypotheses only\].** Major Indonesian banks and digital banks (e.g. BCA/myBCA, Mandiri/Livin', BRI/BRImo, BNI, Jenius, blu, SeaBank, Jago) and e-wallets/marketplaces (e.g. GoPay/Gojek, OVO, DANA, ShopeePay/Shopee, LinkAja, Tokopedia, Grab). Build parsers for the **first three institutions the owner actually uses most**, then expand. OPEN: owner to name them.

**Amount and number parsing (must be unit-tested).** Support at least: `Rp1.250.000`, `Rp 1.250.000,50`, `IDR 1,250,000.00`, `Rp.50.000`, `Rp 50rb`-style shorthand only if observed in real samples. Indonesian convention uses `.` for thousands and `,` for decimals, but notifications are inconsistent; the parser must infer by position/length and reject ambiguous strings rather than guess. Currency defaults to IDR only when the text indicates Rupiah; other currencies must be detected and tagged (hard constraint 1).

**Direction and type cues (Indonesian and English), to be confirmed against samples:** outgoing cues such as "transfer berhasil", "pembayaran berhasil", "debit", "tarik tunai", "payment successful"; incoming cues such as "dana masuk", "Anda menerima", "kredit", "top up berhasil", "received". Ambiguous direction → `NEEDS_REVIEW`, never a guess.

**Negative patterns (return `Ignored`):** OTP/verification codes, login/security alerts, promotions, cashback marketing, payment reminders without a completed payment, failed/pending transactions (unless explicitly modeled), balance-only informational messages (these may update balance without creating a transaction).

**Time.** The notification post time is the default `occurredAt`; if the text/email carries a transaction timestamp, prefer it. Use the device time zone (Indonesia spans three zones; do not hardcode).

**Confidence.** High: amount, direction and institution all parsed. Medium: merchant missing. Low: any ambiguity → `NEEDS_REVIEW`.

**Per-institution authoring workflow (TDD).** (1) Collect at least 10 sanitized samples covering transfer out/in, QR/merchant payment, top-up, bill payment, cash withdrawal, refund, fee/interest, and at least 3 non-transactional negatives. (2) Commit fixtures. (3) Write failing parser tests using the shared test helpers. (4) Implement the parser on the correct base class. (5) Register it. (6) Run the parser gate and keep the output. Follow `docs/adding-bank-parsers.md` and `docs/parser-test-standards.md` after reading them.

## 14. Storyboards (user-journey walkthroughs)

Each journey answers: what does the user do, how do they enter, what do they fill in, what button do they press, what happens after, where do they land, how are they guided, what is stored, what changes visually. **All on-screen UI strings, notifications, prompts, and assistant responses in J1–J9 are displayed in Indonesian.** Use these as acceptance scenarios for UI work; adapt screen names to upstream's navigation after reading it.

### J1 — First launch and enabling notification capture

1. **User does:** opens Catet.in for the first time and wants automatic tracking.
2. **Enters via:** app launcher icon; first-run flag is false.
3. **Fills in:** nothing mandatory. Default currency pre-selected as IDR (`Rp`) with a change option.
4. **Presses:** "Mulai" (Get started) → "Aktifkan akses notifikasi" (Enable notification access).
5. **Happens:** app opens the system notification-access settings; user toggles Catet.in on (and, if required on this Android version, allows restricted settings first). On return, the app detects access granted.
6. **Lands on:** "Pilih aplikasi yang dipantau" (allow-list picker pre-filled from discovered financial apps, all unchecked until the owner confirms), then Home.
7. **Guidance:** Indonesian spotlight steps explain *why* access is needed and that only checked apps are read; a persistent Indonesian banner on Home shows "Akses notifikasi mati" with a fix button if revoked.
8. **Stored:** onboarding-complete flag; default currency; allow-list entries; no transactions yet.
9. **Visual change:** Source Health shows green "Aktif memantau" for notifications; Home empty state shows "Menunggu transaksi pertamamu" with a hint to make a small payment.

### J2 — A payment happens (zero-effort capture)

1. **User does:** pays with an e-wallet or bank app.
2. **Enters via:** no app interaction; the allow-listed app posts a notification.
3. **Fills in:** nothing.
4. **Presses:** nothing.
5. **Happens:** listener captures → parse → rules → dedupe → save (zero LLM process spawned). High-confidence results are confirmed automatically; ambiguous ones go to the Review Queue.
6. **Lands on:** wherever the user is; tapping the optional Catet.in notification opens the transaction detail.
7. **Guidance:** first-ever auto-capture shows a one-time Indonesian tip explaining the Review Queue and how to edit.
8. **Stored:** `SourceEvent` (type NOTIFICATION, parse status) + `Transaction` (+ category from rules/learned mappings) + balance snapshot if present.
9. **Visual change:** Home list gets a new row with a subtle "baru ditambahkan" highlight; month totals and budget rings update per currency.

### J3 — Connect Gmail (read-only)

1. **User does:** wants receipts and bank emails included.
2. **Enters via:** Pengaturan → Sumber Data → Gmail, or the Source Health "Hubungkan" row.
3. **Fills in:** chooses the Google account in the system sheet; picks backfill window (30/90/180 hari); confirms the sender allow-list (pre-filled from sender discovery).
4. **Presses:** "Hubungkan Gmail" → on Google's screen "Continue" (user clicks through the unverified-app warning once, documented in `docs/GMAIL_SETUP.md`) → "Allow".
5. **Happens:** consent returns a read-only grant; credentials go to Keystore-backed storage; the first backfill runs as resumable chunked background work (memory-bounded for 4 GB RAM).
6. **Lands on:** Gmail source detail showing progress, then back to Sumber Data.
7. **Guidance:** plain-language Indonesian text: read-only, on-device, only allow-listed senders are fetched, how to disconnect.
8. **Stored:** `GmailSyncState`, sender allow-list, `SourceEvent` rows (type EMAIL) and merged/new transactions.
9. **Visual change:** Gmail row goes grey → green with last-sync time; Home shows newly imported transactions; duplicates already seen via notifications are merged, not doubled.

### J4 — Resolve a possible duplicate in the Review Queue

1. **User does:** sees a badge on Home/Tinjauan.
2. **Enters via:** Home → "Perlu ditinjau (N)".
3. **Fills in:** nothing required; may edit merchant/category.
4. **Presses:** "Transaksi sama — gabungkan" or "Berbeda — simpan keduanya".
5. **Happens:** merge combines source events into one transaction (reversible via Unmerge); keep-both marks the pair as distinct so it is not suggested again.
6. **Lands on:** next review item, then an empty-queue state.
7. **Guidance:** each card shows both artifacts side by side in Indonesian (source, time, amount, merchant) and why they were flagged.
8. **Stored:** updated `Transaction`, `SourceEvent` links, resolved `ReviewItem`, a "distinct pair" marker.
9. **Visual change:** badge count decrements; Home total corrects if a duplicate is merged.

### J5 — Create a smart rule from a transaction

1. **User does:** wants all "Kopi…" merchants categorized as Makanan & Minuman.
2. **Enters via:** Transaction detail → "Buat aturan".
3. **Fills in:** condition (e.g. `MERCHANT contains`), optional extra conditions (AND/OR), action (`Set CATEGORY`), rule name.
4. **Presses:** "Pratinjau" then "Simpan aturan".
5. **Happens:** preview lists matching existing transactions; saving applies the rule to future captures and optionally to past matches.
6. **Lands on:** Rules list.
7. **Guidance:** inline Indonesian examples of each field/operator; preview count before saving.
8. **Stored:** `Rule` JSON; updated transactions if retro-apply chosen.
9. **Visual change:** matching transactions show the new category chip; category charts redistribute.

### J6 — Handle an unparsed notification or email

1. **User does:** notices "Belum terbaca (N)" on Source Health.
2. **Enters via:** Source Health → Belum terbaca.
3. **Fills in:** may manually set amount, type, merchant, category for the item; or marks "bukan transaksi".
4. **Presses:** "Buat transaksi", "Buat aturan", or "Abaikan".
5. **Happens:** manual creation links the `SourceEvent` to a new `Transaction`; dismiss marks it `IGNORED`; "Salin sampel tersanitasi" (debug) prepares a parser fixture.
6. **Lands on:** next unparsed item.
7. **Guidance:** raw text shown with detected amounts/keywords highlighted.
8. **Stored:** updated `SourceEvent.parseStatus`, new `Transaction` if created.
9. **Visual change:** unparsed counter decrements; Home gains the new transaction.

### J7 — Using the AI assistant (deterministic fast-path & lazy `:ai` process)

1. **User does:** taps the **Asisten AI** tab in bottom navigation and asks *"Pengeluaran bulan ini"* or *"Belanja makan minggu lalu"*, followed by a more complex question like *"Bandingkan pengeluaran jajan minggu ini sama minggu lalu"*.
2. **Enters via:** bottom navigation → **Asisten AI**.
3. **Happens on tab entry:** If `Qwen3.5-0.8B` (4-bit) is already downloaded, the app lazily binds `AiInferenceService` (`android:process=":ai"`) and loads the model in the background while keeping the input bar immediately usable. If not downloaded yet, an Indonesian card offers one-tap model download (showing size and Wi-Fi hint), while deterministic Indonesian queries work immediately even before download.
4. **Fills in & presses:** types question → taps "Kirim".
5. **Happens on send:**
   - *Fast-path (`"pengeluaran bulan ini"`, `"belanja makan minggu lalu"`):* `IndonesianKeywordIntentRouter` matches the phrase deterministically, executes the Room query immediately, and renders the Indonesian answer + IDR summary card in < 50 ms without waiting for `:ai`.
   - *LLM path (complex natural-language query):* `Qwen3.5-0.8B` in `:ai` produces a validated JSON `QueryIntent`, Kotlin computes exact per-currency totals, and the assistant replies in natural Indonesian with a computed figure card.
   - *Tab exit / memory pressure:* When the user switches back to Home or backgrounds the app, the model unloads and `:ai` stops after the grace period (or immediately on `onTrimMemory`), freeing RAM for the rest of the 4 GB device.
6. **Lands on:** the chat thread with the Indonesian answer card; "Kelola model AI" is in Pengaturan.
7. **Guidance:** Indonesian empty state with tappable example chips (*"Pengeluaran bulan ini"*, *"Belanja makan minggu lalu"*, *"Pemasukan bulan ini"*); clear note that answers come from local data.
8. **Stored:** `AssistantModelState` (modelId, variant, path, sha256); no chat history persisted by default.
9. **Visual change:** model status pill (*"Mode cepat aktif"* / *"Memuat Qwen3.5-0.8B…"* / *"Siap"*) → Indonesian answer bubble with per-currency IDR card.

### J8 — Set a budget

1. **User does:** wants a monthly limit for Makanan & Minuman.
2. **Enters via:** Anggaran → "+".
3. **Fills in:** budget type (Batas/Target/Estimasi), category, amount in IDR, period.
4. **Presses:** "Simpan".
5. **Happens:** current-period spend is computed from existing transactions in that currency; alerts are scheduled if enabled.
6. **Lands on:** Anggaran overview with the new ring.
7. **Guidance:** Indonesian helper text explains the three types and daily allowance.
8. **Stored:** budget record.
9. **Visual change:** progress ring/bar appears; Home widget (if installed) updates.

### J9 — Back up and restore

1. **User does:** wants a safety copy before changing phones.
2. **Enters via:** Pengaturan → Cadangkan & Pulihkan.
3. **Fills in:** chooses destination via the system file picker; toggles "sertakan teks mentah" (default off).
4. **Presses:** "Buat cadangan" / "Pulihkan".
5. **Happens:** a Catet.in backup file is written/read; restore validates the format and merges or replaces per the user's choice; Gmail credentials and model files are never included, so Gmail must be reconnected after restore.
6. **Lands on:** Pengaturan with an Indonesian success summary (counts only).
7. **Guidance:** clear Indonesian note on what is and is not in the backup.
8. **Stored:** nothing new on create; on restore, transactions/rules/budgets/accounts.
9. **Visual change:** Home repopulates; Gmail row shows "Perlu hubungkan ulang".

## 15. TDD and quality plan

**Task workflow (mandatory for every task):**

1. Write the failing test(s) that express the requirement.
2. Run them; confirm they fail for the expected reason; keep the output.
3. Implement the smallest change that makes them pass.
4. Run the gate (`./init.sh` scoped to what changed) and keep the output.
5. Refactor with tests green; run the gate again.
6. For runtime/UI work, verify on a real device or **4 GB RAM** emulator profile in both light and dark themes.
7. Record evidence (failing run, passing run, device/memory check) in the task/PR notes. A task is not done without it.

**Test data policy.** No real PII anywhere (hard constraint 4). Fixtures are sanitized per section 13 and stored under `parser-core/src/test/resources/fixtures/<institution>/<case>.txt`, one artifact per file with a short metadata header (source type, institution, expected result).

**Required suites:**

- *Parser unit tests* (pure Kotlin, `parser-core`): per institution and per case, using the shared test helpers; at least 3 negatives per institution; target ≥ 90% line coverage on `parser-core`.
- *Amount/number parsing:* table-driven and property-style tests for every format in section 13, including rejection of ambiguous strings.
- *Notification pipeline:* idempotency (N-4), allow-list drop-before-read (N-2), debounced re-posts, unparsed/ignored routing. Use unit tests with fake `StatusBarNotification` data plus an on-device instrumented test; \[UNVERIFIED\] a shell-based notification-posting helper may be usable for manual device checks — verify before relying on it.
- *Gmail:* MIME decoding with sanitized multipart fixtures (plain, HTML-only, nested, non-UTF-8 charsets); API client against a fake HTTP server; sync state machine (initial backfill, incremental, history-id expired → re-list, 429 backoff, `invalid_grant` → reconnect state, resume after process death, bounded batch size for 4 GB RAM).
- *Dedupe:* table-driven cases from D-7 plus fuzz-style tests asserting idempotency and "never merges different amounts".
- *Rules engine:* every field/operator/action, AND/OR logic, `RAW_TEXT` and `SOURCE` conditions, Block action.
- *Money:* inherited `CurrencyFormatterTest` plus tests that no total blends currencies; `id-ID` formatting.
- *Backup:* inherited schema-guard test (all serialized fields have defaults) and a round-trip test.
- *AI, keyword router, lifecycle, and process isolation:*
  - Unit tests for `IndonesianKeywordIntentRouter` proving exact deterministic intent extraction (zero LLM calls) for *"pengeluaran bulan ini"*, *"belanja makan minggu lalu"*, and related Indonesian phrasing variants.
  - Lifecycle unit/instrumented tests proving `LocalLlmEngine` does **not** load at app startup or on non-AI tabs, loads **only** when clicking/entering the AI Assistant tab, and unloads upon tab exit timeout, `ON_STOP`, and `onTrimMemory`.
  - Process-isolation & resilience test verifying `AiInferenceService` is declared with `android:process=":ai"` in the merged manifest and simulating `:ai` process death (`DeadObjectException`) while confirming the main process and `CatetNotificationListener` stay alive and recover cleanly.
  - **On-device eval harness** (A-10) on a **4 GB RAM** device/emulator verifying Indonesian output quality, intent accuracy, latency, and peak PSS memory ceilings.
- *Architecture/lint tests:* `parser-core` has no Android dependency; no module that handles money or parsing depends on the `ai` package; merged manifest contains none of the forbidden permissions and declares `AiInferenceService` in `:ai`; only allow-listed network hosts appear in the network security config.

**Definition of Done (per task):** failing-then-passing test evidence; the relevant gate green; on-device check for UI/runtime (including 4 GB RAM memory check where applicable); light and dark themes checked; Indonesian user-facing strings verified; no PII or secrets in the diff; no new network host or permission unless this PRD allows it; docs updated; no backward-compat shims left behind.

## 16. Security and privacy requirements

**Data inventory.** Financial transactions, raw notification/email text (purgeable), Gmail credential, sync state, optional model file. All stay in app-private storage on the device.

- **S-1 No exfiltration.** Only the network allow-list in section 11. No analytics/crash SDKs. A test fails the build if a new host or SDK appears.
- **S-2 Device backup exclusion.** Disable Android auto-backup/device-transfer for the database, credentials and model; the only export path is the in-app Catet.in backup the user triggers.
- **S-3 App lock.** Biometric lock with timeout; hide content in the app switcher; consider blocking screenshots on sensitive screens.
- **S-4 Credentials.** Gmail credentials only in Keystore-backed storage; never logged, backed up, or committed.
- **S-5 Logging.** Low-cardinality stable messages with structured fields; never log notification text, email bodies, amounts, account numbers, names or tokens. Add a test or lint rule that flags logging of known sensitive field names.
- **S-6 Repository hygiene (private ≠ safe).** `.gitignore` must exclude keystores and signing configs, `local.properties`, any Google client config/secret files, downloaded `*.litertlm`/`*.gguf` models, debug sample exports, and any real-data fixtures. Fixtures are sanitized only. Add a secret-scanning step (select a current tool by searching; run it as a pre-commit hook and in CI). If a secret is ever committed, treat it as compromised: rotate it; do not rely on deleting history.
- **S-7 Prompt-injection hygiene** as A-9.
- **S-8 Least privilege.** Gmail read-only scope; notification reading limited to allow-listed apps; forbidden-permission lint (N-11).
- **S-9 Data minimization.** Raw-text purge setting (N-7/G-9); no attachments; no HTML bodies; "Hapus semua data" (Delete all data) action in Settings that wipes database, credentials, and model.

## 17. Phased implementation plan

Sizing: S (small), M (medium), L (large) — relative effort, not time promises. Each phase ends with its exit criteria demonstrated with evidence. At the start of every phase, re-check current Android/Google/LiteRT-LM/`llama.cpp` documentation for anything that phase depends on.

### Phase 0 — Bootstrap and upstream study (M) — mandatory first

1. Create the private repository from the upstream code (Option A), keep `LICENSE`, add `NOTICE` and README attribution (section 4).
2. Run `./init.sh` on the untouched clone; record the baseline-green output (this proves the toolchain works before any change).
3. Read every document listed in section 3's documentation map plus CLAUDE.md, and read the source for: SMS ingestion flow, parser base classes and factory, rules engine and learned categories, subscription detection, backup models, Room entities/migrations, assistant prompt construction, LiteRT-LM version and model constants, Pro-gating and Play-services usage, and every network call. Write `docs/UPSTREAM_NOTES.md` containing a module/dependency map and a resolved answer for each \[UNVERIFIED\] item in this PRD (including whether voice dictation and learned categories exist, and whether `app` depends on `shared`).
4. Research tasks, each with sources recorded in the notes:
   - Current `NotificationListenerService` behavior and sideload "restricted settings" handling.
   - Current Google-recommended Android authorization for Gmail without a backend, and Google OAuth publishing-status behavior for personal-use apps.
   - Current **Qwen3.5-0.8B** official model card (license, context length, sampling, disabling `<think>` mode) and runtime support in **LiteRT-LM** (`.litertlm`) vs **`llama.cpp`** (4-bit GGUF `Q4_K_M`/`Q4_0`).
   - Android bound service `android:process=":ai"` IPC (`AIDL`/`Messenger`), storage access across same-UID processes, and `lmkd` process-termination isolation relative to `NotificationListenerService`.
5. Rebrand and strip per section 5 (CHANGE/DROP). One concern per commit; gate green after each; remove SMS code and SMS-specific tests outright (no shims); define the fresh baseline schema.
6. **Exit criteria:** app builds, installs and launches with an empty Home; gate green; merged manifest has no SMS permissions (guard test written first and shown failing before SMS removal); network allow-list test in place; `docs/UPSTREAM_NOTES.md` complete.

### Phase 1 — Notification ingestion MVP (L)

1. Discovery mode and allow-list (N-2, section 13) — test-first.
2. Listener, idempotency, bounded channel/worker hand-off, sealed parse results (N-1 to N-6, N-12).
3. Parser-core: Indonesian amount parsing, normalization, registry; parsers for the owner's first three institutions with sanitized fixtures (section 13 workflow).
4. Pipeline (parse → rules → persist; dedupe stubbed to idempotency only), `SourceEvent`/`Transaction` persistence.
5. Basic Home list, Unparsed bucket, Source Health (notification part), Indonesian onboarding J1/J2.
6. **Exit criteria:** real payments from each of the three apps create correct transactions with correct direction, amount, currency and time; re-posted notifications never duplicate; non-allow-listed apps are provably never read (test); capture latency and main-process baseline PSS memory (< 120 MB) measured on a **4 GB RAM** device/emulator; a 7-day soak with a manual miss-rate count recorded in `docs/SOAK_NOTES.md`.

### Phase 2 — Core parity on the new pipeline (L)

Rules (`RAW_TEXT`, `SOURCE`), categories with Indonesian vocabulary, accounts and balance history, budgets, analytics (paginated/memory-safe), subscriptions, search/filters, manual transactions, CSV export, backup/restore (new format), app lock, widgets, theming, `id-ID` formatting, **Indonesian UI strings across all screens**, onboarding polish. Work feature by feature using section 10 as the checklist; each feature starts with reading its upstream implementation. **Exit criteria:** every section 10 item ticked with tests; backup round-trip test green; inherited hard-constraint tests green; both themes and Indonesian UI strings verified on device.

### Phase 3 — Gmail read-only ingestion (L)

1. Write `docs/GMAIL_SETUP.md` and complete the owner's Google Cloud setup (publishing status "In production").
2. Auth spike and decision (G-3), recorded with evidence.
3. API client, streaming MIME decoding, sync state machine, memory-bounded chunked backfill (≤ 25 messages/batch), sender discovery and allow-list (G-5 to G-10).
4. Email parsers for the owner's top senders (fixtures first).
5. Source Health Gmail row, disconnect/revoke (G-11), reconnect banner, Indonesian journey J3.
6. **Exit criteria:** connect on the real phone; 30-day backfill completes and resumes after forced process death without memory spikes on 4 GB RAM; incremental sync works across at least three days; **the grant is still valid after more than 7 days** (the explicit check that the publishing-status setup worked); disconnect clears local credentials.

### Phase 4 — Dedupe and Review Queue (M)

Implement section 8 with table-driven tests first; Indonesian journey J4; unmerge; transfer/top-up modeling. **Exit criteria:** the owner's real overlapping week of notification + email data yields no silent wrong merges; ambiguous cases land in the Review Queue; idempotency proven by re-running the full pipeline over the same data.

### Phase 5 — AI assistant on Qwen3.5-0.8B (L)

1. **Timeboxed spike (`:ai` process + 4 GB RAM check):** load 4-bit **Qwen3.5-0.8B** (`.litertlm` or GGUF via `llama.cpp`) inside `AiInferenceService` (`android:process=":ai"`) on a **4 GB RAM** device/emulator; measure cold load time, peak `:ai` PSS RAM (< 900 MB target), tokens/second, and verify that killing `:ai` via `adb shell am kill` / low-memory pressure leaves the main process and `CatetNotificationListener` completely intact.
2. `LocalLlmEngine` IPC proxy, `AiInferenceService` (`:ai`), tab-triggered lazy load and aggressive unload lifecycle (tab exit timer, `ON_STOP`, `onTrimMemory`), `ModelCatalog` (4-bit gated for ≤ 4 GB RAM), resumable download with SHA-256 pinning, delete model.
3. `IndonesianKeywordIntentRouter` (deterministic front-layer handling *"pengeluaran bulan ini"*, *"belanja makan minggu lalu"*, and core Indonesian financial queries with zero LLM call), `QueryIntent` schema & validator, deterministic per-currency `QueryExecutor`, Indonesian response templates & result cards, LLM repair/fallback logic.
4. Chat UI in Indonesian and journey J7; architecture & manifest tests enforcing isolation (`parser-core`/domain isolated from `ai`; `AiInferenceService` in `:ai`).
5. Eval harness and `docs/AI_EVAL.md` on a 4 GB RAM device/emulator.
6. **Exit criteria:** the app is fully functional without downloading the model; baseline RAM stays < 120 MB until the user clicks the AI Assistant tab; *"pengeluaran bulan ini"* and *"belanja makan minggu lalu"* resolve deterministically without invoking the LLM; leaving the AI tab unloads the model and frees `:ai` RAM; `:ai` process kill does not stop notification capture; after download everything works in airplane mode with Indonesian responses; eval thresholds met (≥ 90% valid structured output and ≥ 80% correct intent on the golden set).

### Phase 6 — Hardening and release on 4 GB RAM baseline (M)

Verified OEM/battery guidance on the owner's device; end-to-end **4 GB RAM stress profiling** (simultaneous notification burst + Gmail sync + opening AI Assistant tab + running Indonesian queries + switching tabs and verifying RAM release); signing with the owner's keystore (never committed); manifest/network/secret guard tests in CI; documentation pass (`README`, `GMAIL_SETUP`, `UPSTREAM_NOTES`, `AI_EVAL`, `SOAK_NOTES`). Optional, only if the owner asks: PDF statement import, scheduled backups, account merge. **Exit criteria:** 14 days as the owner's daily driver on a 4 GB RAM device with zero OOM crashes, no listener death, no data loss, no duplicate transactions reported, and miss-rate within the owner's accepted threshold.

## 18. Risks and mitigations

- **R1 — Notification capture is unreliable on some Android skins (background killing).** Mitigate with Source Health, rebind handling, OEM guidance verified on the real device, process isolation of `:ai` so LLM memory never triggers listener eviction, and Gmail/manual entry to fill gaps. Never claim completeness in the UI.
- **R2 — Institutions change notification/email wording.** Unparsed bucket, fixture-driven parsers, quick "Salin sampel tersanitasi" loop, parser versioning.
- **R3 — Wrong merges or duplicates.** Conservative policy, Review Queue, unmerge, idempotency constraints, real-data validation in Phase 4.
- **R4 — Qwen3.5-0.8B packaging on LiteRT-LM is unavailable or unstable on 4 GB RAM devices.** Phase 5 spike, version pinning with evidence, `llama.cpp` + 4-bit GGUF (`mmap`) fallback behind `LocalLlmEngine`, and strict `:ai` process isolation.
- **R5 — LLM latency or verbose `<think>` output on 4 GB RAM phones.** Deterministic Indonesian keyword front-layer (`IndonesianKeywordIntentRouter`) bypasses the LLM entirely for frequent queries (*"pengeluaran bulan ini"*, *"belanja makan minggu lalu"*); thinking mode disabled on Qwen3.5-0.8B; eval gate on Indonesian golden set.
- **R6 — Context window & native heap pressure on 4 GB RAM.** Query-intent architecture (never prompt-stuff transactions), 2048-token cap on ≤ 4 GB devices, 4-bit quantization only, and tab-scoped lazy load/unload.
- **R7 — Gmail auth friction or token expiry.** "In production" publishing, reconnect flow, explicit >7-day verification.
- **R8 — Financial data leaking through the repository.** Section 16 S-6; sanitized fixtures only; secret scanning.
- **R9 — AGPL obligations if the app is ever shared.** Keep license/attribution; revisit Option B only if a different license is required.
- **R10 — Upstream drift.** Treat the fork as one-way; no automatic upstream merges. Optionally track upstream parser/infra fixes manually.
- **R11 — Prompt injection through merchant names or email text.** A-9 and a dedicated test with hostile strings.
- **R12 — Platform or policy changes.** Re-verify against current documentation at the start of each phase.
- **R13 — Sideload restrictions on notification access.** N-10 verification in Phase 0/1.
- **R14 — OS Low Memory Killer (`lmkd`) terminates `:ai` during chat on 4 GB RAM.** Mitigated by `:ai` process isolation (main app and notification listener stay alive), `onTrimMemory` proactive unloading, and graceful `DeadObjectException` recovery in the chat UI.

## 19. Open decisions for the owner

*(Resolved in Draft v2: assistant model = `Qwen3.5-0.8B`; deterministic Indonesian keyword front-layer = enabled; separate `:ai` Android process = enabled; lazy load/unload on AI Assistant tab click = enabled; application UI & assistant output language = Indonesian; target hardware baseline = 4 GB RAM.)*

Answer these remaining items before the phase that needs them; until answered, use the stated default and record it:

1. Fork (Option A, default) or clean-room rewrite (Option B)? — before Phase 0 step 1.
2. Application id (default `com.catetin.app`) and launcher icon direction — Phase 0.
3. The first three banks/e-wallets and top email senders to support — Phase 1 and 3.
4. Raw-text retention default (90 days) and whether the column should be encrypted at rest — Phase 1.
5. minSdk (default 26, matching upstream) — Phase 0.
6. Persist assistant chat history (default no) — Phase 5.
7. AI eval thresholds (default ≥ 90% valid output, ≥ 80% correct intent) — Phase 5.
8. Keep optional exchange-rate fetching (default yes, user-disableable) — Phase 2.

## 20. Operating rules for the implementing agent

1. Read this entire document, then complete Phase 0 before touching features. Your first output is `docs/UPSTREAM_NOTES.md`, not code.
2. Do not invent facts about upstream internals or unverified runtime behaviors. If this PRD and the code/device disagree, reality wins: record the discrepancy in `docs/UPSTREAM_NOTES.md` or `docs/AI_EVAL.md`, propose the smallest PRD change, and continue.
3. Ask the owner only about OPEN decisions in section 19 that block the current task; otherwise use the stated default and log it.
4. Search current documentation before adding or upgrading any dependency, API, permission, or policy-dependent behavior; cite what you found in the notes. Never rely on memory for versions.
5. TDD is mandatory and must be evidenced (section 15). No task is "done" on assertion.
6. No backward compatibility: rename and replace, then update every consumer. Leave no shims, aliases or dead SMS code.
7. Respect all inherited hard constraints (section 3), the LLM isolation rule, `:ai` process separation, lazy tab-triggered load/unload, Indonesian app output, and 4 GB RAM memory ceilings (sections 9 & 11).
8. Small, reviewable commits; one concern each; gate green before every commit. Never edit version numbers or changelogs by hand if the release script owns them.
9. Never commit secrets, signing material, real data, downloaded models, or debug sample exports. Never log sensitive fields.
10. Do not add permissions, network hosts, SDKs or telemetry beyond what this document allows without explicit owner approval.

## 21. References

- Upstream project (README, CLAUDE.md, docs/): https://github.com/sarim2000/pennywiseai-tracker
- Qwen3.5 official model hub (verify exact `Qwen/Qwen3.5-0.8B` model card, license, sampling, and GGUF quantizations in Phase 0): https://huggingface.co/Qwen
- Google LiteRT-LM (check `litert-community` for Qwen3.5-0.8B `.litertlm` support in Phase 0): https://ai.google.dev/edge/litert-lm
- `llama.cpp` repository & Android build documentation (fallback GGUF inference runtime): https://github.com/ggml-org/llama.cpp
- Android multi-process services (`android:process`) & memory management (`ComponentCallbacks2` / `onTrimMemory`): https://developer.android.com/guide/components/processes-and-threads and https://developer.android.com/topic/performance/memory
- Gmail scope classification and verification (secondary sources; confirm against Google's own documentation in Phase 0): https://developer.nylas.com/docs/dev-guide/provider-guides/google/google-verification-security-assessment-guide and https://www.unipile.com/google-oauth-refresh-token/