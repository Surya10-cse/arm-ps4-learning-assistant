# Actual-device validation — 2026-11-11T11:44:35+05:30

User's Realme P4 Pro 5G: RMX5116; SoC property SM7750; Android API 36; ABI arm64-v8a; kernel page size 4096 bytes. Raw properties are in device-probe.json. Measured properties take precedence over manufacturer specifications.

Core UI/scoring/adaptation, persistence, migration and missing-model behavior passed physically with airplane mode enabled and Wi-Fi/Bluetooth disabled. Original radios were restored. Official native ExecuTorch class/JNI initialization: PASS. This verifies library linkage, not model operators, tokenizer pairing, generated answers or per-operator acceleration.

The production debug update used `adb install -r`, preserved learner row counts and retained the database filename. Private snapshots remain in ignored .tools; no learner values were copied into submission reports. UI tests used an isolated validation package. Screenshots show its explicitly named test learner and genuinely scored test responses, not simulated learner statistics or Llama responses.

## Actual non-inference baseline

Label `core-ui-baseline-no-model`; package `ai.adapt.app.validation`. 12 samples over 30.219 seconds. Maximum sampled PSS: 99237 KiB. Maximum observed interval CPU: 1.8497965223965598% of one core. These are core-UI measurements with no model, not Llama performance or true unsampled peak memory. Raw samples include thermal output, battery temperature and charge counters. USB charging prevents a defensible battery-consumption result.

## Blocked acceptance

No authorized original checkpoint/tokenizer or compatible trusted bundle is provisioned. PTE execution, real five-mode output, loaded-model cancellation, offline inference, quality and INT4/INT8 comparisons remain BLOCKED. The real-model test is explicitly skipped. No model token rate, first-token latency, peak memory, CPU or battery result is fabricated.
