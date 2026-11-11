# Offline validation record

**PASS: physical offline core learning on the Realme P4 Pro 5G. BLOCKED: real offline Llama inference.** Airplane mode was verified on, Wi-Fi and Bluetooth off; the actual test run passed and original radio settings were restored. Raw evidence: `reports/offline-device-validation.json`, device JUnit XML and `reports/DEVICE_VALIDATION.md`.

## Static and host validation

- App manifest intentionally has no INTERNET/network permission; verify the final merged APK manifest as part of packaging.
- Source contains no HTTP client, cloud LLM SDK, remote inference URL, or mock model implementation.
- Core tests exercise deterministic mastery, prerequisite gating, question selection, roadmap time budgeting, revision and prompt boundaries.
- Python tests verify packaging hashes, exact ZIP contents, missing-artifact rejection and JSON syntax.
- Room instrumentation tests executed on the real phone, including v1→v2 migration, duplicate protection, invalid-choice rollback and durable reopen. Compose tests executed the real profile→diagnostic→roadmap→focused practice→mastery update journey in an isolated validation app.
- A separate Robolectric host Android/SQLite suite exercises concurrent submissions, the scored roadmap journey, process reopen/recovery and invalid-answer rollback. Passing host simulation is useful storage evidence but does not establish physical phone behavior.

See `reports/BUILD_VALIDATION.md` for final host results.

## Physical phone checklist

| Test | Required evidence |
|---|---|
| Fresh onboarding and goal persistence | PASS in actual Compose journey; production update retains learner rows |
| Adaptive diagnostic | PASS: actual UI choices, explanations and stored evidence |
| Skill-gap and roadmap | PASS: actual scored practice unlocks eligible recommendations; host budget/prerequisite tests |
| Official ARM64 JNI linkage | PASS: actual class initializer loads packaged native library on phone |
| Import real paired Llama bundle | BLOCKED: no authorized artifacts; checksum/traversal/type/runtime-rejection rollback tests pass |
| Teach/Socratic/Debug/Practice/Review | BLOCKED: actual model test explicitly skipped |
| Structured tutor output | Strict parser tests PASS; live model output quality/validity rate BLOCKED |
| Reassessment and roadmap adaptation | PASS in real UI journey |
| Revision | Deterministic clock-injected policy tests PASS; physical revision writes persist; long-term retention unvalidated |
| Airplane mode cold core start | PASS; inference remains BLOCKED |
| Loaded-model background/stop/reload | BLOCKED by missing model; core lifecycle/reopen passes |
| Missing/corrupt/unsafe model | PASS for missing model, checksum, traversal, coerced version and rollback; real incompatible PTE coverage pending |
| Restart/recovery | PASS for activity recreation/new ViewModel, transactional database reopen and production force-stop/relaunch; native streaming process-kill remains BLOCKED |
| Storage pressure and native failure | PARTIAL: bounded import/errors implemented; OS-kill/low-memory/device-storage stress not validated |
| ARM inference measurements | BLOCKED; raw core-UI baseline is explicitly not an inference result |

Reproduce with `python scripts/offline_validation.py --serial YOUR_SERIAL`. Tests target `ai.adapt.app.validation` and never clear production learner data. Debug signing is for testing; competition distribution needs a team-owned key outside the repository. Signing a release does not complete the blocked model gates.
