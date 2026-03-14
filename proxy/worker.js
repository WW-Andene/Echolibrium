/**
 * Cloudflare Worker — TTS proxy for Kyōkan.
 *
 * Receives synthesis requests from the app (no auth required),
 * injects the DeepInfra API key, forwards to DeepInfra, and
 * streams the raw PCM response back to the app.
 *
 * Deploy:
 *   cd proxy
 *   npx wrangler secret put DEEPINFRA_API_KEY   # paste your key
 *   npx wrangler deploy
 *
 * Your PROXY_BASE_URL will be: https://kyokan-tts-proxy.<your-subdomain>.workers.dev/v1/tts
 */

const DEEPINFRA_URL = "https://api.deepinfra.com/v1/openai/audio/speech";
const ALLOWED_MODELS = new Set(["canopylabs/orpheus-3b-0.1-ft"]);
const ALLOWED_VOICES = new Set(["tara", "leah", "jess", "leo", "dan", "mia", "zac", "zoe"]);
const MAX_INPUT_LENGTH = 5000;

export default {
  async fetch(request, env) {
    // Only accept POST to /v1/tts
    const url = new URL(request.url);
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders() });
    }
    if (request.method !== "POST" || url.pathname !== "/v1/tts") {
      return jsonError(404, "Not found");
    }

    // Validate API key is configured
    const apiKey = env.DEEPINFRA_API_KEY;
    if (!apiKey) {
      return jsonError(500, "Server misconfigured: missing API key");
    }

    // Parse and validate request body
    let body;
    try {
      body = await request.json();
    } catch {
      return jsonError(400, "Invalid JSON");
    }

    if (!body.input || typeof body.input !== "string") {
      return jsonError(400, "Missing or invalid 'input' field");
    }
    if (body.input.length > MAX_INPUT_LENGTH) {
      return jsonError(400, `Input too long (max ${MAX_INPUT_LENGTH} chars)`);
    }
    if (body.model && !ALLOWED_MODELS.has(body.model)) {
      return jsonError(400, "Invalid model");
    }
    if (body.voice && !ALLOWED_VOICES.has(body.voice)) {
      return jsonError(400, "Invalid voice");
    }

    // Build the upstream request
    const payload = {
      model: body.model || "canopylabs/orpheus-3b-0.1-ft",
      input: body.input,
      response_format: "pcm",
      voice: body.voice || "tara",
    };

    // Forward to DeepInfra
    const upstream = await fetch(DEEPINFRA_URL, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });

    if (!upstream.ok) {
      const errBody = await upstream.text().catch(() => "");
      return jsonError(upstream.status, `Upstream error: ${errBody.slice(0, 200)}`);
    }

    // Stream PCM bytes back to the app
    return new Response(upstream.body, {
      status: 200,
      headers: {
        "Content-Type": "application/octet-stream",
        ...corsHeaders(),
      },
    });
  },
};

function jsonError(status, message) {
  return new Response(JSON.stringify({ error: message }), {
    status,
    headers: { "Content-Type": "application/json", ...corsHeaders() },
  });
}

function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
  };
}
