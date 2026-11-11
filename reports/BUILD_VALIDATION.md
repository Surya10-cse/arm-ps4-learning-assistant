# Build and validation — 2026-11-11T11:44:35+05:30

Existing modules preserved; product version 0.2.0. Host checks and actual-device core learning passed. Authorized Llama artifacts are absent: PTE generation, five-mode quality, offline inference and INT4/INT8 performance remain BLOCKED.

| Check | Actual result |
|---|---|
| JVM/Robolectric | 34 passed |
| Python | 11 passed |
| Actual P4 Pro Android tests | 10 passed, 1 skipped; individual cases below |
| Offline core | PASS_CORE; airplane on / Wi-Fi off / Bluetooth off; original radios restored |
| Existing learner update | Row counts preserved; schema 1 → 2 |
| Debug lint | 0 errors, 10 warnings |
| Release lint | 0 errors, 10 warnings |
| Catalogue | 8 competencies, 64 curated questions |
| Permissions / JNI package | No INTERNET; actual ARM64 ExecuTorch library |
| Release signing | BLOCKED: unsigned non-debuggable candidate; external team keystore not configured |

## Artifacts

- debug: 41711551 bytes; SHA-256 `c9de892d7210dc03d80b0d69c01ecf8491d4f25427cd52aa001070df090e61be`; signed=True; debuggable=True
- release: 34438136 bytes; SHA-256 `eb9e5b726d84e95e2ce7a8b0677952b7b4b7fba532a8aec2fe93e913891f0a85`; signed=False; debuggable=False

## Host suites

- ai.adapt.core.AnalyticsTest: 2 tests, 0 skipped
- ai.adapt.core.LearningTest: 18 tests, 0 skipped
- ai.adapt.core.TutorTest: 3 tests, 0 skipped
- ai.adapt.inference.TutorJsonTest: 6 tests, 0 skipped
- ai.adapt.data.HostRepositoryTest: 5 tests, 0 skipped

## Actual-device cases

- PASS: ai.adapt.data.MigrationTest.versionOneLearnerAndEvidenceSurviveVersionTwoUpgrade
- PASS: ai.adapt.data.RepositoryTest.invalidChoiceRollsBackAllLearningWrites
- PASS: ai.adapt.data.RepositoryTest.answerIsAtomicIdempotentAndDurableAcrossReopen
- PASS: ai.adapt.app.LearnerJourneyTest.interruptedAssessmentResumesInsteadOfScoringTwice
- PASS: ai.adapt.app.LearnerJourneyTest.actualUiJourneyScoresAdaptsAndSurvivesActivityRecreation
- SKIPPED: ai.adapt.app.LocalRuntimeGateTest.realProvisionedLlamaExecutesAllFiveModesWithoutChangingMastery
- PASS: ai.adapt.app.LocalRuntimeGateTest.officialExecuTorchNativeLibraryInitializesOnArm64
- PASS: ai.adapt.app.LocalRuntimeGateTest.missingModelReportsActionableFailureWithoutGeneratingText
- PASS: ai.adapt.app.ModelImportTest.traversalAndCoercedManifestVersionAreRejected
- PASS: ai.adapt.app.ModelImportTest.checksumFailureCannotReachRuntimeOrActivate
- PASS: ai.adapt.app.ModelImportTest.runtimeRejectionRollsBackPreparedFiles

## Lint warnings

- debug: GradleDependency — A newer version of androidx.compose:compose-bom than 2025.04.01 is available: 2026.09.00
- debug: GradleDependency — A newer version of androidx.activity:activity-compose than 1.10.1 is available: 1.13.0
- debug: GradleDependency — A newer version of androidx.lifecycle:lifecycle-viewmodel-compose than 2.9.0 is available: 2.11.0
- debug: GradleDependency — A newer version of androidx.lifecycle:lifecycle-runtime-compose than 2.9.0 is available: 2.11.0
- debug: GradleDependency — A newer version of androidx.room:room-runtime than 2.7.1 is available: 2.8.5
- debug: GradleDependency — A newer version of androidx.compose:compose-bom than 2025.04.01 is available: 2026.09.00
- debug: GradleDependency — A newer version of androidx.test:runner than 1.6.2 is available: 1.7.0
- debug: GradleDependency — A newer version of androidx.test.ext:junit than 1.2.1 is available: 1.3.0
- debug: GradleDependency — A newer version of androidx.test:core than 1.6.1 is available: 1.7.0
- debug: ChromeOsAbiSupport — Missing x86_64 ABI support for ChromeOS
- release: GradleDependency — A newer version of androidx.compose:compose-bom than 2025.04.01 is available: 2026.09.00
- release: GradleDependency — A newer version of androidx.activity:activity-compose than 1.10.1 is available: 1.13.0
- release: GradleDependency — A newer version of androidx.lifecycle:lifecycle-viewmodel-compose than 2.9.0 is available: 2.11.0
- release: GradleDependency — A newer version of androidx.lifecycle:lifecycle-runtime-compose than 2.9.0 is available: 2.11.0
- release: GradleDependency — A newer version of androidx.room:room-runtime than 2.7.1 is available: 2.8.5
- release: GradleDependency — A newer version of androidx.compose:compose-bom than 2025.04.01 is available: 2026.09.00
- release: GradleDependency — A newer version of androidx.test:runner than 1.6.2 is available: 1.7.0
- release: GradleDependency — A newer version of androidx.test.ext:junit than 1.2.1 is available: 1.3.0
- release: GradleDependency — A newer version of androidx.test:core than 1.6.1 is available: 1.7.0
- release: ChromeOsAbiSupport — Missing x86_64 ABI support for ChromeOS

Versions remain pinned. ChromeOS x86 support is outside the ARM-phone target. No lint baseline suppresses errors. A skipped real-model test is not successful inference.
