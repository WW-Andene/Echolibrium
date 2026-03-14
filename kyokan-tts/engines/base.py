"""
Base TTS Engine interface — every engine implements this contract.
"""

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from enum import Enum
from typing import Optional
import time


class EngineType(Enum):
    ORPHEUS = "orpheus"
    CHATTERBOX = "chatterbox"
    QWEN3_TTS = "qwen3_tts"


class Priority(Enum):
    LOW = "low"          # offline fallback, cheapest
    NORMAL = "normal"    # default
    HIGH = "high"        # maximum fidelity


@dataclass
class TtsRequest:
    """What the router sends to an engine."""
    text: str
    voice: Optional[str] = None              # engine-specific voice ID
    emotion_tags: list[str] = field(default_factory=list)
    voice_instruction: Optional[str] = None  # natural language (Qwen3 only)
    priority: Priority = Priority.NORMAL
    output_format: str = "wav"               # wav, mp3, pcm


@dataclass
class TtsResult:
    """What an engine returns."""
    audio_data: bytes
    sample_rate: int = 24000
    format: str = "wav"
    engine_used: str = ""
    latency_ms: float = 0.0
    character_count: int = 0
    error: Optional[str] = None

    @property
    def success(self) -> bool:
        return self.error is None and len(self.audio_data) > 0


class TtsEngine(ABC):
    """Base class for all TTS engines."""

    def __init__(self, api_key: str, base_url: str = ""):
        self.api_key = api_key
        self.base_url = base_url
        self._available = False

    @property
    @abstractmethod
    def engine_type(self) -> EngineType:
        ...

    @property
    @abstractmethod
    def name(self) -> str:
        ...

    @property
    @abstractmethod
    def available_voices(self) -> list[str]:
        ...

    @property
    @abstractmethod
    def default_voice(self) -> str:
        ...

    @property
    @abstractmethod
    def supports_emotion_tags(self) -> bool:
        ...

    @property
    @abstractmethod
    def supports_voice_instructions(self) -> bool:
        ...

    @property
    @abstractmethod
    def cost_per_million_chars(self) -> float:
        """Cost in USD per 1M characters."""
        ...

    @abstractmethod
    async def synthesize(self, request: TtsRequest) -> TtsResult:
        ...

    async def test_connection(self) -> bool:
        """Quick health check — try synthesizing a short phrase."""
        try:
            result = await self.synthesize(TtsRequest(text="Test."))
            self._available = result.success
            return self._available
        except Exception as e:
            print(f"[{self.name}] Connection test failed: {e}")
            self._available = False
            return False

    @property
    def is_available(self) -> bool:
        return self._available

    def estimate_cost(self, text: str) -> float:
        """Estimate cost in USD for synthesizing this text."""
        return len(text) / 1_000_000 * self.cost_per_million_chars

    def _measure_time(self):
        """Helper to measure latency."""
        return time.perf_counter()
