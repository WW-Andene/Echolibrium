"""
Kyōkan TTS Test Harness
============================
Tests all three engines with sample notifications, saves audio files,
and prints a comparison report.

Usage:
  export DEEPINFRA_API_KEY="your_key_here"
  python test_engines.py

Or pass the key directly:
  python test_engines.py --api-key "your_key_here"
"""

import asyncio
import argparse
import os
import sys

from engines import TtsRequest, EngineType, Priority
from router import TtsRouter


# ── Sample notifications to test ──
TEST_NOTIFICATIONS = [
    {
        "name": "simple_message",
        "text": "New message from Sarah. She says she's running ten minutes late but she's bringing coffee.",
        "priority": Priority.NORMAL,
    },
    {
        "name": "emotional_orpheus",
        "text": "Hey! <laugh> I just got the job offer! <gasp> I can't believe it actually happened.",
        "priority": Priority.HIGH,
        "note": "Uses Orpheus emotion tags",
    },
    {
        "name": "emotional_chatterbox",
        "text": "Hey! [laugh] I just got the job offer! I can't believe it actually happened.",
        "priority": Priority.NORMAL,
        "note": "Uses Chatterbox paralinguistic tags",
    },
    {
        "name": "calm_instruction",
        "text": "Reminder: Your meditation session starts in five minutes. Take a deep breath.",
        "priority": Priority.NORMAL,
        "voice_instruction": "Speak slowly and calmly, with a gentle soothing tone",
        "note": "Uses Qwen3 voice instruction",
    },
    {
        "name": "urgent_alert",
        "text": "Battery critically low at three percent. Connect charger immediately.",
        "priority": Priority.HIGH,
    },
    {
        "name": "casual_social",
        "text": "Alex liked your photo and left a comment: This is amazing, where was this taken?",
        "priority": Priority.LOW,
    },
]


async def test_single_engine(router: TtsRouter, engine_type: EngineType, text: str, filename: str, **kwargs):
    """Test a specific engine and save the output."""
    request = TtsRequest(
        text=text,
        voice=kwargs.get("voice"),
        voice_instruction=kwargs.get("voice_instruction"),
        priority=kwargs.get("priority", Priority.NORMAL),
        output_format="wav",
    )

    result = await router.synthesize_with(engine_type, request)

    if result.success:
        filepath = f"outputs/{filename}"
        with open(filepath, "wb") as f:
            f.write(result.audio_data)
        print(f"    ✓ {result.engine_used}: {result.latency_ms:.0f}ms, "
              f"{len(result.audio_data):,} bytes, "
              f"${router.get_engine(engine_type).estimate_cost(text):.5f}")
        return True
    else:
        print(f"    ✗ {result.engine_used}: {result.error}")
        return False


async def run_comparison_test(router: TtsRouter):
    """Run every notification through every compatible engine."""

    print("\n" + "=" * 60)
    print("  KYŌKAN TTS ENGINE COMPARISON TEST")
    print("=" * 60)

    # First, health check all engines
    print("\n── Engine Health Check ──")
    health = await router.test_all_engines()
    active = [name for name, ok in health.items() if ok]

    if not active:
        print("\n  ✗ No engines responding. Check your API key.")
        return

    print(f"\n  {len(active)}/3 engines online\n")

    # Run tests
    for i, notif in enumerate(TEST_NOTIFICATIONS, 1):
        print(f"\n── Test {i}: {notif['name']} ──")
        if "note" in notif:
            print(f"  ({notif['note']})")
        print(f'  "{notif["text"][:80]}..."' if len(notif["text"]) > 80 else f'  "{notif["text"]}"')
        print()

        # Test with Orpheus
        await test_single_engine(
            router, EngineType.ORPHEUS,
            text=notif["text"],
            filename=f"{notif['name']}_orpheus.wav",
            priority=notif.get("priority", Priority.NORMAL),
            voice="tara",
        )

        # Test with Chatterbox
        await test_single_engine(
            router, EngineType.CHATTERBOX,
            text=notif["text"],
            filename=f"{notif['name']}_chatterbox.wav",
            priority=notif.get("priority", Priority.NORMAL),
        )

        # Test with Qwen3
        await test_single_engine(
            router, EngineType.QWEN3_TTS,
            text=notif["text"],
            filename=f"{notif['name']}_qwen3.wav",
            priority=notif.get("priority", Priority.NORMAL),
            voice="Vivian",
            voice_instruction=notif.get("voice_instruction"),
        )

    # Test the smart router
    print(f"\n\n── Smart Router Test ──")
    print("  (Router picks the best engine automatically)\n")

    for notif in TEST_NOTIFICATIONS:
        request = TtsRequest(
            text=notif["text"],
            priority=notif.get("priority", Priority.NORMAL),
            voice_instruction=notif.get("voice_instruction"),
            output_format="wav",
        )
        engine = router.select_engine(request)
        result = await router.synthesize(request)

        filepath = f"outputs/{notif['name']}_routed.wav"
        if result.success:
            with open(filepath, "wb") as f:
                f.write(result.audio_data)
            print(f"  ✓ {notif['name']}: routed to {result.engine_used} "
                  f"({result.latency_ms:.0f}ms)")
        else:
            print(f"  ✗ {notif['name']}: {result.error}")

    # Print usage report
    print(f"\n{router.usage_report()}")
    print(f"\n  Audio files saved to: outputs/")
    print(f"  Router log saved to:  outputs/router_log.jsonl")


async def run_quick_test(router: TtsRouter):
    """Minimal test — one request per engine."""
    print("\n── Quick Test (one request per engine) ──\n")

    text = "Hello! This is a test of the Kyōkan text to speech system."

    for engine_type in EngineType:
        await test_single_engine(
            router, engine_type,
            text=text,
            filename=f"quick_{engine_type.value}.wav",
            voice="tara" if engine_type == EngineType.ORPHEUS else
                  "Vivian" if engine_type == EngineType.QWEN3_TTS else None,
        )

    print(f"\n{router.usage_report()}")


def main():
    parser = argparse.ArgumentParser(description="Kyōkan TTS Test Harness")
    parser.add_argument("--api-key", type=str, help="DeepInfra API key")
    parser.add_argument("--quick", action="store_true", help="Run minimal test only")
    args = parser.parse_args()

    # Get API key
    api_key = args.api_key or os.environ.get("DEEPINFRA_API_KEY")
    if not api_key:
        print("Error: No API key provided.")
        print("  Set DEEPINFRA_API_KEY environment variable or use --api-key")
        print("  Get a key at: https://deepinfra.com/auth/personal_details")
        sys.exit(1)

    # Ensure output directory exists
    os.makedirs("outputs", exist_ok=True)

    # Create router
    router = TtsRouter(api_key=api_key)

    # Run tests
    if args.quick:
        asyncio.run(run_quick_test(router))
    else:
        asyncio.run(run_comparison_test(router))


if __name__ == "__main__":
    main()
