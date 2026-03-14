"""
Chatterbox TTS Engine — fast, emotion exaggeration, 23 languages.
Built on 0.5B Llama backbone by Resemble AI.
Connects to DeepInfra API (OpenAI-compatible endpoint).
"""

import aiohttp
import time
from .base import TtsEngine, TtsRequest, TtsResult, EngineType


class ChatterboxEngine(TtsEngine):

    # Chatterbox Turbo paralinguistic tags
    EMOTION_TAGS = ["[laugh]", "[cough]", "[chuckle]", "[sniffle]"]

    def __init__(self, api_key: str):
        super().__init__(
            api_key=api_key,
            base_url="https://api.deepinfra.com/v1/openai"
        )

    @property
    def engine_type(self) -> EngineType:
        return EngineType.CHATTERBOX

    @property
    def name(self) -> str:
        return "Chatterbox Turbo"

    @property
    def available_voices(self) -> list[str]:
        # Chatterbox uses reference audio for voice cloning,
        # but Turbo on DeepInfra has default voices
        return ["default"]

    @property
    def default_voice(self) -> str:
        return "default"

    @property
    def supports_emotion_tags(self) -> bool:
        return True  # Turbo supports [laugh], [cough], etc.

    @property
    def supports_voice_instructions(self) -> bool:
        return False

    @property
    def cost_per_million_chars(self) -> float:
        return 1.00

    async def synthesize(self, request: TtsRequest) -> TtsResult:
        start = time.perf_counter()

        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }

        payload = {
            "model": "ResembleAI/chatterbox-turbo",
            "input": request.text,
            "response_format": request.output_format,
        }

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
