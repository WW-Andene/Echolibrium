"""
TTS Router — intelligently dispatches requests to the best engine.

Routing logic:
1. Voice instruction present? → Qwen3-TTS (only engine that supports it)
2. Priority HIGH? → Orpheus (maximum fidelity)
3. Priority LOW? → Chatterbox Turbo (cheapest, fastest)
4. Default? → Chatterbox Turbo (best cost/quality ratio)

Future: Custom Core will learn user preferences and override defaults.
"""

import json
import os
from datetime import datetime
from engines import (
    TtsEngine, TtsRequest, TtsResult, EngineType, Priority,
    OrpheusEngine, ChatterboxEngine, Qwen3TtsEngine,
)


class TtsRouter:
    def __init__(self, api_key: str, log_path: str = "outputs/router_log.jsonl"):
        self.engines: dict[EngineType, TtsEngine] = {
            EngineType.ORPHEUS: OrpheusEngine(api_key),
            EngineType.CHATTERBOX: ChatterboxEngine(api_key),
            EngineType.QWEN3_TTS: Qwen3TtsEngine(api_key),
        }
        self.log_path = log_path
        self._total_chars = 0
        self._total_cost = 0.0
        self._request_count = 0

    def get_engine(self, engine_type: EngineType) -> TtsEngine:
        return self.engines[engine_type]

    def select_engine(self, request: TtsRequest) -> TtsEngine:
        """Pick the best engine for this request."""

        # Rule 1: Voice instructions → only Qwen3 handles this
        if request.voice_instruction:
            return self.engines[EngineType.QWEN3_TTS]

        # Rule 2: High priority → maximum fidelity
        if request.priority == Priority.HIGH:
            return self.engines[EngineType.ORPHEUS]

        # Rule 3: Low priority → cheapest/fastest
        if request.priority == Priority.LOW:
            return self.engines[EngineType.CHATTERBOX]

        # Rule 4: Check for Orpheus-specific emotion tags
        orpheus_tags = ["<laugh>", "<sigh>", "<gasp>", "<cough>",
                        "<chuckle>", "<groan>", "<yawn>", "<sniffle>"]
        if any(tag in request.text for tag in orpheus_tags):
            return self.engines[EngineType.ORPHEUS]

        # Rule 5: Check for Chatterbox-specific tags
        chatterbox_tags = ["[laugh]", "[cough]", "[chuckle]", "[sniffle]"]
        if any(tag in request.text for tag in chatterbox_tags):
            return self.engines[EngineType.CHATTERBOX]

        # Default: Chatterbox Turbo (best cost/quality for notifications)
        return self.engines[EngineType.CHATTERBOX]

    async def synthesize(self, request: TtsRequest) -> TtsResult:
        """Route the request and synthesize."""
        engine = self.select_engine(request)
        result = await engine.synthesize(request)

        # Track usage
        if result.success:
            self._total_chars += result.character_count
            self._total_cost += engine.estimate_cost(request.text)
            self._request_count += 1

        # Log for Custom Core (future learning)
        self._log(engine, request, result)

        return result

    async def synthesize_with(
        self, engine_type: EngineType, request: TtsRequest
    ) -> TtsResult:
        """Force a specific engine — used for testing/comparison."""
        engine = self.engines[engine_type]
        result = await engine.synthesize(request)

        if result.success:
            self._total_chars += result.character_count
            self._total_cost += engine.estimate_cost(request.text)
            self._request_count += 1

        self._log(engine, request, result)
        return result

    async def test_all_engines(self) -> dict[str, bool]:
        """Health check all engines."""
        results = {}
        for engine_type, engine in self.engines.items():
            ok = await engine.test_connection()
            results[engine.name] = ok
            status = "✓" if ok else "✗"
            print(f"  {status} {engine.name}")
        return results

    def usage_report(self) -> str:
        """Print current session usage stats."""
        lines = [
            "── Usage Report ──",
            f"  Requests:   {self._request_count}",
            f"  Characters: {self._total_chars:,}",
            f"  Est. cost:  ${self._total_cost:.4f}",
            "──────────────────",
        ]
        return "\n".join(lines)

    def _log(self, engine: TtsEngine, request: TtsRequest, result: TtsResult):
        """Append to JSONL log — future Custom Core reads this."""
        os.makedirs(os.path.dirname(self.log_path), exist_ok=True)
        entry = {
            "timestamp": datetime.utcnow().isoformat(),
            "engine": engine.name,
            "engine_type": engine.engine_type.value,
            "text_length": len(request.text),
            "voice": request.voice,
            "priority": request.priority.value,
            "voice_instruction": request.voice_instruction,
            "success": result.success,
            "latency_ms": round(result.latency_ms, 1),
            "cost_usd": round(engine.estimate_cost(request.text), 6),
            "error": result.error,
        }
        with open(self.log_path, "a") as f:
            f.write(json.dumps(entry) + "\n")
