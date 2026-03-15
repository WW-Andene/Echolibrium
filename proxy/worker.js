// Deploy this at workers.cloudflare.com
// Add your DeepInfra key as an environment variable called DEEPINFRA_KEY
// (Settings → Variables → Add, so it's never in the code)
//
// Optional: set ALLOWED_ORIGIN env var to restrict to your app's package
// (e.g. "com.echolibrium.kyokan")

// KNOWN LIMITATION: In-memory rate limiter resets when worker instance restarts
// (Cloudflare Workers restart frequently) and doesn't share state across instances.
// For production use with real traffic, replace with one of:
//   1. Cloudflare Rate Limiting rules (dashboard → Security → WAF → Rate limiting rules)
//   2. Cloudflare Workers KV for durable per-IP counters
//   3. Cloudflare D1 (SQLite) for more sophisticated rate tracking
// The current in-memory approach is adequate for single-user/low-traffic deployment.
// TODO: Migrate to Cloudflare Rate Limiting rules before public release.
const RATE_LIMIT_WINDOW_MS = 60_000; // 1 minute
const RATE_LIMIT_MAX = 30; // max requests per IP per window
const MAX_INPUT_LENGTH = 2000; // max characters per request
const rateLimitMap = new Map();

function isRateLimited(ip) {
  const now = Date.now();
  const entry = rateLimitMap.get(ip);
  if (!entry || now - entry.windowStart > RATE_LIMIT_WINDOW_MS) {
    rateLimitMap.set(ip, { windowStart: now, count: 1 });
    return false;
  }
  entry.count++;
  if (entry.count > RATE_LIMIT_MAX) {
    return true;
  }
  return false;
}

// Periodically clean stale entries (keep map from growing unbounded)
function cleanRateLimitMap() {
  const now = Date.now();
  for (const [ip, entry] of rateLimitMap) {
    if (now - entry.windowStart > RATE_LIMIT_WINDOW_MS * 2) {
      rateLimitMap.delete(ip);
    }
  }
}

export default {
  async fetch(request, env) {
    // Only accept POST
    if (request.method !== "POST") {
      return new Response("Method not allowed", { status: 405 });
    }

    // Rate limiting by client IP
    const clientIp = request.headers.get("CF-Connecting-IP") || "unknown";
    if (isRateLimited(clientIp)) {
      return new Response("Rate limit exceeded", { status: 429 });
    }

    // Clean stale rate limit entries periodically
    if (rateLimitMap.size > 1000) {
      cleanRateLimitMap();
    }

    // Read the app's request (same shape as DeepInfra's API)
    let body;
    try {
      body = await request.json();
    } catch {
      return new Response("Invalid JSON", { status: 400 });
    }

    // Validate input
    if (!body.input || typeof body.input !== "string" || body.input.trim().length === 0) {
      return new Response("Missing or empty input", { status: 400 });
    }
    if (body.input.length > MAX_INPUT_LENGTH) {
      return new Response(`Input too long (max ${MAX_INPUT_LENGTH} chars)`, { status: 400 });
    }

    // Forward to DeepInfra with YOUR key (app never sees it)
    const response = await fetch("https://api.deepinfra.com/v1/openai/audio/speech", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${env.DEEPINFRA_KEY}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: body.model || "canopylabs/orpheus-3b-0.1-ft",
        input: body.input,
        voice: body.voice || "tara",
        response_format: "pcm",
      }),
    });

    if (!response.ok) {
      return new Response("TTS failed", { status: response.status });
    }

    // Stream the audio bytes back to the app
    return new Response(response.body, {
      headers: { "Content-Type": "application/octet-stream" },
    });
  },
};
