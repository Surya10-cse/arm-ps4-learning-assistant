# Real local Llama deployment

Status: runtime adapter uses the official released AAR; physical JNI initialization is a separate device test gate. No authorized weights or bundle are provisioned, so actual export, tokenizer/model pairing, PTE generation and answer quality remain **BLOCKED**. The actual phone is now the Realme P4 Pro 5G, Android 16, ARM64.

Official references inspected:
- [ExecuTorch Android 1.0](https://docs.pytorch.org/executorch/1.0/using-executorch-android.html)
- [LLM export 1.0](https://docs.pytorch.org/executorch/1.0/llm/export-llm.html)
- [v1.0.0 exporter configuration source](https://github.com/pytorch/executorch/blob/v1.0.0/extension/llm/export/config/llm_config.py)
- [v1.0.0 Llama instructions](https://github.com/pytorch/executorch/blob/v1.0.0/examples/models/llama/README.md)

The actual `org.pytorch:executorch-android:1.0.0` classes were inspected with `javap` and `javap -c`: `extension.llm.LlmModule`, `LlmModuleConfig`, `LlmGenerationConfig` and `LlmCallback`. This release has `onResult`/`onStats`, `load`, `stop`, `resetContext` and `resetNative`. Although the generation config exposes maxNewTokens and temperature setters, bytecode shows that overload forwards only seqLen and echo. The app therefore calls the native `generate(prompt, seqLen, callback, echo)` overload directly, uses constructor temperature, and claims only a 2048-token total-sequence bound. An additional text cap requests stop after 6000 emitted UTF-16 units. Official JNI is sufficient; adding custom C++ would increase maintenance without a current need.

## Export on Linux/WSL2

1. Obtain Meta-authorized **Llama 3.2 1B Instruct original** checkpoint files: `consolidated.00.pth`, `params.json`, `tokenizer.model`. Accept the model license/access terms yourself. Do not provide credentials to this repository. Safetensors or GGUF cannot be renamed into these formats.
2. Install source-compatible exporter in a separate directory (several GB disk and sufficient host RAM required):

```bash
bash models/setup-exporter.sh /absolute/path/executorch-v1
source /absolute/path/executorch-v1/.venv/bin/activate
python models/export_model.py --source /absolute/path/executorch-v1 \
  --weights /absolute/path/authorized-llama-instruct \
  --output /absolute/path/int4-export --quantization 8da4w
python models/export_model.py --source /absolute/path/executorch-v1 \
  --weights /absolute/path/authorized-llama-instruct \
  --output /absolute/path/int8-export --quantization int8
```

The script checks release tag, tracked/staged changes, submodule commits and model dimensions. It validates the original tiktoken tokenizer's 128000 unique contiguous merge ranks before exporting. This is format verification, not proof of semantic compatibility. It exports with KV cache and XNNPACK, records config/source/checkpoint/tokenizer hashes, captures logs and pip freeze, then builds an uncompressed bundle. Installation follows official release scripts; the resolved environment is captured, not claimed to be a prevalidated immutable lock. Archive it with the submission. Source reference for original tokenizer format: [Meta tokenizer](https://github.com/meta-llama/llama3/blob/main/llama/tokenizer.py).

3. Inspect export logs for delegated operators and fallbacks. Compare both models against the same reference prompts and independently scored quality rubric. INT4 file-size reduction alone is not a latency/quality result. These recipes use ordinary XNNPACK quantization, not experimental TorchAO Arm low-bit kernels; those cannot be mixed with XNNPACK settings.
4. Copy the resulting ZIP to the phone's Downloads folder (USB transfer is sufficient). Open ADAPT-AI → Device → Import. Import runs on IO, validates the paired files and atomically activates the bundle.
5. Run each tutor mode in airplane mode. Check stop, backgrounding, reload and bad-bundle handling. Structured JSON can fail with a small model; this is reported, never silently accepted.

## Lifecycle and limits

One native generation at a time. Native load/generation run off the UI thread; callbacks enter a bounded flow. A short ownership monitor excludes stop/reset races, and cleanup only stops its own generation. Backgrounding requests native stop; an application-owned scope releases memory after the active job ends. Context resets each turn. A failed native load releases resources, and import only activates after successful native loading. There is a text cap and a two-minute generation watchdog; native load and an unresponsive native stop cannot be forcibly preempted safely in-process. No model/network download or cloud fallback exists. CPU/XNNPACK does not imply Qualcomm NPU support.

Remaining device risks: large-memory native load can fail or be killed by Android; thermal throttling, storage and incompatible exports must be tested physically. The app reports runtime errors, but cannot guarantee recovery from an OS process kill or a native crash. The prior active bundle remains when a new import fails validation.
