# ARM benchmark report

**Llama inference status: BLOCKED / NOT MEASURED.** The user's Realme P4 Pro 5G is connected and core/JNI validation is recorded in `reports/DEVICE_VALIDATION.md`. No authorized weights/PTE are provisioned. Actual core-UI baseline samples exist, clearly separated from model results. No Llama latency, token rate, inference peak memory or model-power result is claimed.

## Hardware evidence

Actual ADB evidence: RMX5116, QTI SM7750, Android 16/API 36, `arm64-v8a`, 11,512,612 kB kernel-visible memory at initial audit and approximately 175 GiB available `/data` storage. Availability is time-dependent; retain the timestamped raw probe. CPU flags include `asimd`, `asimddp` and `i8mm`. These establish advertised instruction capabilities, not which kernels a model executes. Dynamic/swap RAM is not physical RAM. [Realme's P4 Pro specifications](https://www.realme.com/in/realme-p4-pro-5g/specs) identify the product's Snapdragon 7 Gen 4 chipset; use the measured properties for this test unit rather than the old Narzo assumptions.

Target: `arm64-v8a`, CPU XNNPACK. No NPU, GPU or NEON execution claim has been verified. Device CPU feature flags alone do not prove a model operator used a specific instruction path. For acceleration evidence, preserve export delegation logs plus runtime/backend profiling; report fallbacks.

## Reproducible protocol

1. Connect the P4 Pro and authorize ADB. Save `adb devices -l` and `python scripts/device_probe.py --serial SERIAL --output reports/device-probe.json`. Confirm actual model, SoC, ABI, OS, CPU features, kernel page size, available memory and storage.
2. Install debug APK; import the locally exported INT4 bundle. Record APK SHA-256, model/tokenizer hashes, exporter config/source/environment, build variant and device build fingerprint.
3. Airplane mode on; Wi-Fi and Bluetooth off explicitly. Keep brightness, battery range, charging state and ambient temperature fixed and documented. Let the device cool between model variants.
4. Device → Run fixed ARM benchmark suite. It releases the native module, performs 1 cold load/generation, 2 warmup calls and 5 measured calls with identical prompt and settings (total sequence limit 2048, native stop requested after 6000 UTF-16 code units, constructor temperature 0.3). The 1.0.0 config overload ignores maxNewTokens, so the app uses the native sequence-length overload and does not claim an exact new-token cap. Cold means native module reloaded, not guaranteed cold OS page cache. Record output quality separately; generation length may vary.
5. Export records to JSON. Repeat for INT8 after cooldown. Use `python scripts/summarize_benchmarks.py records.json --output reports/summary.json`. It groups measured calls by actual model, device, prompt hash and quantization; excludes tutor, cold and warmup calls. For a stronger competition result run at least 20 measured repetitions per variant and report thermal progression and variability, not just the best run.
6. Run `python scripts/sample_device_metrics.py --serial SERIAL --package ai.adapt.app --seconds 60 --interval 2 --label int4-inference --output reports/int4-device-samples.json` concurrently with the actual model suite. It samples PSS, `/proc/PID/stat` CPU deltas, thermal service and battery/charge counters. CPU 100% means one fully busy core; multiple cores can exceed 100%. The maximum observed sample is **not a guaranteed true peak**. Sampling overhead and actual intervals are retained. Model-only power needs an external monitor or calibrated method; battery percentage and USB-connected charge counter changes are not a consumption result.

## Metric definitions

| Metric | Measurement source | Caveat |
|---|---|---|
| Native load ms | elapsed monotonic time around module construction/load | Excludes model hash verification; load=0 for already loaded module |
| First text ms | First nonempty Java text callback minus generation start | Excludes model load/hash verification; may differ from native first-token time |
| Generation ms | elapsed time around native generation | Includes prompt prefill and decode |
| Decode tokens/s | native generated_tokens × 1000 / (inference_end_ms − prompt_eval_end_ms) | Same definition as release stats; no word/callback approximation |
| PSS KB | Debug.MemoryInfo after generation | Not peak; includes rest of app |
| Thermal status | Android PowerManager after generation | Device-dependent status; not CPU temperature |

Raw native stats are retained. Missing fields produce unavailable throughput, never an estimated token count. Export/import hashing is part of end-user setup latency; report separately from native load and generation.

## Results

| Variant | Phone generation | First text | Decode tokens/s | Peak memory | Quality |
|---|---|---|---|---|---|
| INT4 / 8da4w | Pending | Unmeasured | Unmeasured | Unmeasured | Unevaluated |
| INT8 | Pending | Unmeasured | Unmeasured | Unmeasured | Unevaluated |

The actual core-UI baseline (no model loaded) is in `reports/core-ui-device-samples.json` and summarised dynamically in `reports/DEVICE_VALIDATION.md`. It must not be copied into the INT4/INT8 table or described as inference memory/CPU. All published numerical model comparisons remain pending actual model execution and quality evaluation.

Optimization decisions must follow these measurements. Candidate experiments: context 1024 vs 2048 (requires matched app/manifest/export change), prompt reduction, group size, KV-cache dtype, CPU thread count via a verified custom runtime API, and sustained thermal behavior. None is claimed faster without paired results.
