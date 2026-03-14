"""
Qwen3-TTS Engine — natural language voice control, 10 languages.
Built on 1.7B Qwen architecture by Alibaba.
Connects to DeepInfra API.
"""

import aiohttp
import time
from .base import TtsEngine, TtsRequest, TtsResult, EngineType


class Qwen3TtsEngine(TtsEngine):

    VOICES = [
        "Vivian", "Serena", "Uncle_Fu", "Dylan", "Eric",
        "Ryan", "Aiden", "Ono_Anna", "Sohee"
    ]

    def __init__(self, api_key: str):
        super().__init__(
            api_key=api_key,
            base_url="https://api.deepinfra.com/v1/openai"
        )

    @property
    def engine_type(self) -> EngineType:
        return EngineType.QWEN3_TTS

    @property
    def name(self) -> str:
        return "Qwen3-TTS 1.7B"

    @property
    def available_voices(self) -> list[str]:
        return self.VOICES

    @property
    def default_voice(self) -> str:
        return "Vivian"

    @property
    def supports_emotion_tags(self) -> bool:
        return False  # uses instructions instead

    @property
    def supports_voice_instructions(self) -> bool:
        return True  # "speak calmly", "excited tone", etc.

    @property
    def cost_per_million_chars(self) -> float:
        return 1.00

    async def synthesize(self, request: TtsRequest) -> TtsResult:
        voice = request.voice if request.voice in self.VOICES else self.default_voice
        start = time.perf_counter()

        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }

        payload = {
            "model": "Qwen/Qwen3-TTS",
            "input": request.text,
            "voice": voice,
            "response_format": request.output_format,
        }

        # Qwen3-TTS supports instruction control via the voice_instruction field
        # DeepInfra may expose this as a 'style' or 'instruction' parameter
        if request.voice_instruction:
            payload["instruction"] = request.voice_instruction

        try:
            async with aiohttp.ClientSession() as session:
                async with session.post(
                    f"{self.base_url}/audio/speech",
                    headers=headers,
                    json=payload,
                ) as resp:
                    if resp.status != 200:
                        error_text = await resp.text()
                        return TtsResult(
                            audio_data=b"",
                            engine_used=self.name,
                            error=f"HTTP {resp.status}: {error_text[:200]}",
                        )

                    audio_data = await resp.read()
                    elapsed = (time.perf_counter() - start) * 1000

                    self._available = True
                    return TtsResult(
                        audio_data=audio_data,
                        sample_rate=24000,
                        format=request.output_format,
                        engine_used=self.name,
                        latency_ms=elapsed,
                        character_count=len(request.text),
                    )

        except Exception as e:
            return TtsResult(
                audio_data=b"",
                engine_used=self.name,
                error=str(e),
            )
