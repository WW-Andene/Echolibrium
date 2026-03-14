"""
Orpheus 3B TTS Engine — highest fidelity, emotion tags, Llama-based.
Connects to DeepInfra API (OpenAI-compatible endpoint).
"""

import aiohttp
import time
from .base import TtsEngine, TtsRequest, TtsResult, EngineType


class OrpheusEngine(TtsEngine):

    VOICES = ["tara", "leah", "jess", "leo", "dan", "mia", "zac", "zoe"]
    EMOTION_TAGS = [
        "<laugh>", "<chuckle>", "<sigh>", "<cough>",
        "<sniffle>", "<groan>", "<yawn>", "<gasp>"
    ]

    def __init__(self, api_key: str):
        super().__init__(
            api_key=api_key,
            base_url="https://api.deepinfra.com/v1/openai"
        )

    @property
    def engine_type(self) -> EngineType:
        return EngineType.ORPHEUS

    @property
    def name(self) -> str:
        return "Orpheus 3B"

    @property
    def available_voices(self) -> list[str]:
        return self.VOICES

    @property
    def default_voice(self) -> str:
        return "tara"

    @property
    def supports_emotion_tags(self) -> bool:
        return True

    @property
    def supports_voice_instructions(self) -> bool:
        return False

    @property
    def cost_per_million_chars(self) -> float:
        return 7.00

    def _format_prompt(self, text: str, voice: str) -> str:
        """Orpheus expects format: 'voice_name: text here'"""
        return f"{voice}: {text}"

    async def synthesize(self, request: TtsRequest) -> TtsResult:
        voice = request.voice if request.voice in self.VOICES else self.default_voice
        start = time.perf_counter()

        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }

        payload = {
            "model": "canopylabs/orpheus-3b-0.1-ft",
            "input": request.text,
            "voice": voice,
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
