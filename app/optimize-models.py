#!/usr/bin/env python3
"""
Pre-optimizes bundled ONNX models to ORT format for faster loading on Android.

ORT format is a pre-optimized flatbuffer that eliminates graph optimization
at runtime, reducing model load time from ~10-30s to ~1-2s.

After successful conversion, the original .onnx file is deleted to keep
the APK under the 4GB ZIP32 limit.

Usage:
    python3 optimize-models.py

Requires: pip install onnxruntime
"""

import os
import sys
import glob

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
KOKORO_DIR = os.path.join(SCRIPT_DIR, "src", "main", "assets", "kokoro-model")
PIPER_DIR  = os.path.join(SCRIPT_DIR, "src", "main", "assets", "piper-models")

# Languages used by the app (espeak-ng language codes)
KEEP_LANGUAGES = {"en", "fr", "es"}


def convert_to_ort(onnx_path: str) -> bool:
    """Convert a .onnx model to .ort format, then delete the original."""
    ort_path = onnx_path.replace(".onnx", ".ort")
    if os.path.exists(ort_path):
        # ORT already exists — clean up .onnx if still present
        if os.path.exists(onnx_path):
            os.remove(onnx_path)
            print(f"  ✓ {os.path.basename(ort_path)} already exists, removed {os.path.basename(onnx_path)}")
        else:
            print(f"  ✓ {os.path.basename(ort_path)} already exists")
        return True

    try:
        import onnxruntime as ort

        print(f"  → Converting {os.path.basename(onnx_path)} to ORT format…")

        opts = ort.SessionOptions()
        opts.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        opts.optimized_model_filepath = ort_path

        ort.InferenceSession(onnx_path, opts, providers=["CPUExecutionProvider"])

        if os.path.exists(ort_path):
            orig_size = os.path.getsize(onnx_path) / (1024 * 1024)
            opt_size = os.path.getsize(ort_path) / (1024 * 1024)
            print(f"  ✓ {os.path.basename(ort_path)}: {orig_size:.1f}MB → {opt_size:.1f}MB")
            os.remove(onnx_path)
            print(f"    Removed {os.path.basename(onnx_path)} (ORT replaces it)")
            return True
        else:
            print(f"  ✗ Conversion produced no output for {os.path.basename(onnx_path)}")
            return False

    except ImportError:
        print("  ✗ onnxruntime not installed — skipping ORT conversion")
        print("    Install with: pip install onnxruntime")
        return False
    except Exception as e:
        print(f"  ✗ Failed to convert {os.path.basename(onnx_path)}: {e}")
        if os.path.exists(ort_path):
            os.remove(ort_path)
        return False


def strip_espeak_languages(espeak_dir: str):
    """Remove unused espeak-ng language data to reduce APK size."""
    if not os.path.isdir(espeak_dir):
        print(f"  ✗ espeak-ng-data not found at {espeak_dir}")
        return

    lang_dir = os.path.join(espeak_dir, "lang")
    if not os.path.isdir(lang_dir):
        print("  ✗ espeak-ng-data/lang not found")
        return

    removed = 0
    kept = 0

    for entry in os.listdir(lang_dir):
        entry_path = os.path.join(lang_dir, entry)

        if os.path.isdir(entry_path):
            if entry in KEEP_LANGUAGES:
                kept += 1
                continue
            import shutil
            shutil.rmtree(entry_path)
            removed += 1
        elif os.path.isfile(entry_path):
            lang_code = entry.split("-")[0].split("_")[0]
            if lang_code in KEEP_LANGUAGES:
                kept += 1
                continue
            os.remove(entry_path)
            removed += 1

    print(f"  ✓ espeak-ng: kept {kept} languages ({', '.join(sorted(KEEP_LANGUAGES))}), removed {removed} unused")


def main():
    print()
    print("═══ Model Optimization ═══")

    # 1. Convert Kokoro model to ORT format
    kokoro_model = os.path.join(KOKORO_DIR, "model.onnx")
    if os.path.exists(kokoro_model):
        print()
        print("── Kokoro model → ORT ──")
        convert_to_ort(kokoro_model)
    elif os.path.exists(kokoro_model.replace(".onnx", ".ort")):
        print()
        print("── Kokoro model → ORT ──")
        print(f"  ✓ model.ort already exists")
    else:
        print(f"  ✗ Kokoro model not found at {kokoro_model}")

    # 2. Strip unused espeak-ng languages
    espeak_dir = os.path.join(KOKORO_DIR, "espeak-ng-data")
    if os.path.isdir(espeak_dir):
        print()
        print("── Strip unused espeak-ng languages ──")
        strip_espeak_languages(espeak_dir)

    # 3. Convert bundled Piper voice models to ORT format
    piper_models = sorted(glob.glob(os.path.join(PIPER_DIR, "*.onnx")))
    # Exclude tokens.txt and other non-model files
    piper_models = [m for m in piper_models if not m.endswith("tokens.txt")]
    if piper_models:
        print()
        print(f"── Bundled Piper voices → ORT ({len(piper_models)} models) ──")
        converted = 0
        failed = 0
        for model_path in piper_models:
            if convert_to_ort(model_path):
                converted += 1
            else:
                failed += 1
        print(f"  Total: {converted} converted, {failed} failed")

    print()
    print("═══ Optimization complete ═══")
    print()


if __name__ == "__main__":
    main()
