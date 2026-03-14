"""
Kyōkan TTS Proxy — FastAPI server.

Receives synthesis requests from the app (no auth required),
injects the DeepInfra API key, forwards to DeepInfra, and
streams the raw PCM response back to the app.

Run:
    DEEPINFRA_API_KEY=your_key_here uvicorn server:app --host 0.0.0.0 --port 8000

Or set the key in a .env file alongside this script.
"""

import os
import httpx
from fastapi import FastAPI, Request
from fastapi.responses import Response, JSONResponse

DEEPINFRA_URL = "https://api.deepinfra.com/v1/openai/audio/speech"
ALLOWED_MODELS = {"canopylabs/orpheus-3b-0.1-ft"}
ALLOWED_VOICES = {"tara", "leah", "jess", "leo", "dan", "mia", "zac", "zoe"}
MAX_INPUT_LENGTH = 5000

app = FastAPI()
client = httpx.Client(timeout=40.0)


@app.post("/v1/tts")
async def tts_proxy(request: Request):
    api_key = os.environ.get("DEEPINFRA_API_KEY", "")
    if not api_key:
        return JSONResponse(status_code=500, content={"error": "Server misconfigured: missing API key"})

    try:
        body = await request.json()
    except Exception:
        return JSONResponse(status_code=400, content={"error": "Invalid JSON"})

    text = body.get("input")
    if not text or not isinstance(text, str):
        return JSONResponse(status_code=400, content={"error": "Missing or invalid 'input' field"})
    if len(text) > MAX_INPUT_LENGTH:
        return JSONResponse(status_code=400, content={"error": f"Input too long (max {MAX_INPUT_LENGTH} chars)"})

    model = body.get("model", "canopylabs/orpheus-3b-0.1-ft")
    if model not in ALLOWED_MODELS:
        return JSONResponse(status_code=400, content={"error": "Invalid model"})

    voice = body.get("voice", "tara")
    if voice not in ALLOWED_VOICES:
        return JSONResponse(status_code=400, content={"error": "Invalid voice"})

    payload = {
        "model": model,
        "input": text,
        "response_format": "pcm",
        "voice": voice,
    }

    try:
        upstream = client.post(
            DEEPINFRA_URL,
            json=payload,
            headers={
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json",
            },
        )
    except httpx.RequestError as e:
        return JSONResponse(status_code=502, content={"error": f"Upstream connection failed: {e}"})

    if upstream.status_code != 200:
        return JSONResponse(
            status_code=upstream.status_code,
            content={"error": f"Upstream error: {upstream.text[:200]}"},
        )

    return Response(content=upstream.content, media_type="application/octet-stream")
