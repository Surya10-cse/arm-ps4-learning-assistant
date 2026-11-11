# Engineering audit — existing product, 2026-11-11

Scope: all production Kotlin (app/core/data/inference), Compose UI, Gradle/manifest/resources, version-1 Room schema, JVM/Robolectric/instrumentation/Python tests, JSON contracts, exporter/setup/probe/benchmark/packaging scripts, CI and existing documentation. No application code was modified before this audit. Existing four modules and installed learner data will be preserved.

## Actual environment

ADB sees the user's Realme P4 Pro as RMX5116, SoC property SM7750, ARM64-only ABI, Android 16. `/proc/meminfo` reported 11,512,612 kB kernel-visible total and 4,836,560 kB available at initial inspection; `/data` reported approximately 175 GiB available. These are observations, not an inference compatibility result. Previous Narzo/Dimensity documentation is obsolete for the current device.

The app is installed. Its private model directory has no model. No weights/PTE exist in the project or standard Llama cache. Python has torch but not executorch; no installed WSL distribution was listed. Previous user context confirmed no authorized weights. Export, tokenizer compatibility, operator coverage and real Llama responses remain BLOCKED until authorized artifacts are supplied. Never replace them with another model, fake text or a cloud request.

## Prioritised findings before implementation

| ID | Priority | Finding and consequence | Planned correction |
|---|---|---|---|
| A01 | P0 | No authorized Llama checkpoint/tokenizer or PTE provisioned. Five modes are prompt instructions wired to native execution, not verified model functionality. | Keep explicit BLOCKED status; verify native API/source, strengthen conversion/provisioning and add real-model test gates. |
| A02 | P0 | Structured tokens are displayed while streaming, and invalid raw JSON is saved/displayed. This violates validation-before-display/storage. org.json also accepts non-standard JSON syntax. | Buffer structured output privately; strict parser; persist/render only validated content; record a safe failure status. |
| A03 | P0 | `stop()` reads a native reference concurrently with `resetNative()`. Callback cleanup can stop a subsequent run after the gate is released. Cleanup state can overwrite another operation's state. | Guard native ownership, bind cancellation to a run, serialize load/generate/reset and release off the UI thread. |
| A04 | P1 | BKT adds the learning transition even to an incorrect response. At sufficiently low priors an error increases mastery. Guess probability is fixed despite support for 2–6 options. | Monotonic incorrect update; use actual choice count; add boundary and evidence tests. |
| A05 | P1 | Budget permits 5 minutes, but all new lessons take >=10; roadmap can be empty for a new learner. Due reviews disappear if a prerequisite estimate drops. | Budget-sized study chunks; retain due recall separately from new-learning prerequisite gating. |
| A06 | P1 | Explicit practice on a skill irrelevant to the current goal returns no question, yet UI announces evidence recorded. All skills are selectable. | Allow explicit focused practice for any catalogued skill; distinguish no items from completed evidence. |
| A07 | P1 | `continueAssessment` trusts the passed session, permitting stale session state to be written after goal/focus changes. Active assessment and topic are not automatically restored on cold app startup. | Resolve authoritative active session; restore assessment and saved selection; test stale-session rejection and restart. |
| A08 | P1 | Session/question deduplication works transactionally, but repeats across sessions reuse 32 items; many correct answers occupy option 2. Difficulty adaptation has very low weight. | Add reviewed parallel forms, stable session-specific option ordering, exposure-aware selection and stronger difficulty fit; retain psychometric limitations. |
| A09 | P1 | Room schema is v1, with no migration implementation/test. Existing learner data must survive this upgrade. | Add a non-destructive v1→v2 migration and migration tests before introducing profile fields. |
| A10 | P1 | Import manifest uses coercing getters; pointer validation is loose. Process death can leave large orphan directories. Exception after pointer commit can delete the new active model. | Strict manifest/types/filenames, transactional activation/rollback, bounded staging and startup orphan recovery, failure tests. |
| A11 | P1 | Prompt character truncation can split Unicode and the final UTF-8 bound rejects useful multilingual input/history instead of budgeting optional context. Prior scores are labelled mastery without observation context. | Code-point/UTF-8-aware budgeting, explicit unassessed context and relevant recent history. |
| A12 | P1 | No physical integration/offline tests have run; old reports still say no device. | Install updated build, execute Room and UI integration tests in an isolated validation package, verify offline core journey/restart and capture evidence. |
| A13 | P1 | Only post-generation PSS and post-run thermal status are collected; there is no peak-memory/CPU/battery sampling. No actual inference benchmark exists. | External timestamped ADB sampling with honest sampled-peak/CPU/charge-counter limits; never label app baseline as Llama performance. |
| A14 | P1 | Release build is unsigned and no team signing configuration exists. Version is still 0.1.0/code 1. | Increment version; produce unsigned release candidate and secure external-keystore signing instructions; do not mislabel a debug key as a team release identity. |
| A15 | P2 | Dashboard numerator counts mastered skills outside the current goal; no goal-specific accuracy/coverage/trend or graph view exists. Lesson time is an estimate, not tracked time. | Compute analytics from real scoped observations; add prerequisite map, measured evidence labels and transparent empty states. |
| A16 | P2 | Preferences lack learner level and target context. UI is a single long Compose source, uses Unicode navigation glyphs, exposes technical raw statuses and can flash onboarding during database initialization. | Extend profile through migration, improve navigation/accessibility/loading, preserve existing screen flow, and keep model engineering details in settings. |
| A17 | P2 | No timeout for stalled native generation; streaming is only persisted at completion; VM cleanup may outlive its cancelled scope incorrectly. | Bounded wall-time stop, explicit interrupted/limited statuses, durable recovery and application-owned release scope. |
| A18 | P2 | Packaging hardcodes test counts/date, omits a release artifact and exports old reports; benchmark summaries combine sessions and may accept invalid/nonfinite values. Export provenance checks omit staged/submodule modifications. | Derive reports from artifacts/results; include current completion matrix/raw evidence/catalogue; validate metrics/provenance and consistent comparison settings. |
| A19 | P2 | Unused `ui-tooling-preview` dependency, `background` import and Python imports; no custom JNI/C++ exists. | Remove unused dependencies/imports; retain official packaged JNI, avoiding unnecessary native bridge code. |

## What is already genuine and should remain

The four-module architecture, local curated lessons/questions, deterministic DAG/BKT-inspired planner, atomic Room answer updates, duplicate protection, due-recall early-practice fix, five contextual tutor prompts, native ExecuTorch calls, bounded ZIP extraction/checksums and no-network manifest are real implementations. No production mock inference or fabricated benchmark rows were found. Hardcoded curriculum, policy parameters, initial priors and benchmark settings are intentional configuration/content, not measured learner statistics. They need validation and honest labels, not replacement with random values.

## Execution order and release gates

1. Capture physical hardware evidence and baseline tests; preserve existing app data.
2. Fix inference ownership/JSON/import correctness and deterministic learning defects with regression tests.
3. Add migration, learner context, evidence analytics and focused UI improvements.
4. Build debug/release candidates, run host and actual-device integration tests, validate core offline/restart behavior.
5. Package current source, catalogue, diagrams, test/evidence reports and demo; maintain `docs/COMPLETION_MATRIX.md`.

Real Llama export/generation, five-mode output quality, offline inference and quantization performance cannot pass without authorized weights or a trusted compatible bundle. Team-signed distribution additionally needs an external team keystore. All independently testable work continues despite those blockers.
