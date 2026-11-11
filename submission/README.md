# ADAPT-AI

Offline Android personalised learning assistant for ARM Bharat AI SoC Challenge PS4. Kotlin, Compose Material 3, MVVM, Room and genuine ExecuTorch Llama integration. No cloud inference or mock tutor.

**Core learning has been tested physically on the Realme P4 Pro 5G. Real Llama inference is still blocked by missing authorized model artifacts.** Offline UI/scoring/persistence evidence and the independent native-runtime gate are recorded in the validation reports. Neither an APK nor a native-library load proves PTE generation or Llama performance.

## Open and build

Open this directory in Android Studio. Use JDK 21, Android SDK 35 and the included Gradle wrapper. Dependencies must be downloaded once to build; the installed app's core works offline. Model provisioning is a separate offline import step.

```powershell
# This workspace already contains isolated tools under ignored .tools/.
powershell -ExecutionPolicy Bypass -File scripts/build.ps1
python -m unittest discover -s tests -v
```

On another machine with JDK/SDK configured:

```sh
./gradlew :core:test :inference:testDebugUnitTest :data:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :data:assembleDebugAndroidTest :app:assembleValidationAndroidTest :core:exportCatalogue
```

Debug APK: `submission/ADAPT-AI-debug.apk`. Install with `adb install -r submission/ADAPT-AI-debug.apk`; do not uninstall to update. The app targets ARM64 Android 9/API 28 or newer and preserves the existing `adapt-v1.db` file through the v1→v2 Room migration. Release output is `submission/ADAPT-AI-release-unsigned.apk` until an external team keystore is configured. It is a non-debuggable release candidate, not an installable signed release or a completed competition acceptance result.

For team signing, configure `ADAPT_SIGNING_STORE_FILE`, `ADAPT_SIGNING_STORE_PASSWORD`, `ADAPT_SIGNING_KEY_ALIAS` and `ADAPT_SIGNING_KEY_PASSWORD` outside source control, then run `scripts/sign-release.ps1`. Do not paste passwords into chat or commit a key. The build reads these variables without adding secrets to command-line arguments. Final model/device acceptance remains required after signing.

For a new Windows environment, `python scripts/bootstrap.py` downloads JDK 21.0.7, Gradle 8.11.1, SDK command-line tools and the pinned AAR into `.tools/`. Use SDK manager to install `platform-tools`, `platforms;android-35`, `build-tools;35.0.0` and accept applicable SDK licenses. `scripts/build.ps1` uses these local tools without altering system settings. Android Studio can use its own configured SDK/JDK instead.

## Product flow

1. Choose Android developer, ML engineer or Edge AI engineer and a daily time budget.
2. Complete an adaptive diagnostic; inspect mastery, gaps and misconception tags.
3. Open a prerequisite-aware roadmap activity and its offline lesson.
4. Import an authorized exported Llama bundle in Device, then use Teach, Socratic, Debug, Practice or Review tutoring.
5. Take focused practice; scored evidence updates mastery, recall scheduling and roadmap atomically.
6. Reopen the app to resume local data. Run the fixed ARM benchmark suite and export actual measurements on the target phone.

The version-2 catalogue contains eight competencies, 64 curated questions and three career directions. Selection adapts to scored evidence, defers recently exposed items and shuffles displayed choices while preserving scoring indices. Fixed BKT-inspired parameters remain educational heuristics awaiting calibration. `submission/catalogue.v2.json` contains the exact compiled catalogue.

## Actual phone validation

```powershell
python scripts/device_probe.py --serial YOUR_ADB_SERIAL --output reports/device-probe.json
python scripts/offline_validation.py --serial YOUR_ADB_SERIAL
```

The second command temporarily enables airplane mode and disables Wi-Fi/Bluetooth, runs real device tests, and restores the original radio settings in `finally`. Its UI tests use `ai.adapt.app.validation`, with isolated data. They never clear the user's production package. Real-model tests skip explicitly when no model is provisioned; a skip is not successful inference.

After a trusted model has been provisioned, use Device → benchmark suite, export records, and run `scripts/summarize_benchmarks.py`. Use `scripts/sample_device_metrics.py` concurrently for sampled memory, process CPU and thermal/battery evidence. The existing core-UI baseline is labelled **no model**, and is not an inference benchmark.

## Project map

| Location | Responsibility | Team owner |
|---|---|---|
| `app/` | Compose UI, lifecycle, ViewModel, integration | Surya |
| `inference/`, `models/` | Released ExecuTorch API, model bundle import, exporter | Priyadharshan |
| `core/` | Competency graph, question bank, mastery, roadmap, revision | Anjana |
| `data/` | Room schema and transactional persistence | Integration |
| `schemas/` | Versioned JSON interfaces | All |

## Documentation

- [Architecture and risks](docs/ARCHITECTURE.md)
- [Prioritised engineering audit](docs/ENGINEERING_AUDIT.md)
- [PASS/PARTIAL/BLOCKED/FAIL matrix](docs/COMPLETION_MATRIX.md)
- [Implementation stages](docs/PROGRESS.md)
- [Database and contracts](docs/DATABASE_AND_CONTRACTS.md)
- [Model conversion and deployment](docs/MODEL_DEPLOYMENT.md)
- [Host build validation](reports/BUILD_VALIDATION.md)
- [Actual-device validation](reports/DEVICE_VALIDATION.md)
- [ARM benchmark report and protocol](docs/ARM_BENCHMARK_REPORT.md)
- [Offline validation](docs/OFFLINE_VALIDATION.md)
- [Technical competition report](docs/COMPETITION_REPORT.md)
- [Five-minute demo](docs/DEMO_WORKFLOW.md)

Do not commit model weights, access tokens, signing keys or learner data. Review Meta's model license before redistributing artifacts. Exact model export, generated-content quality and physical phone acceptance remain release gates.
