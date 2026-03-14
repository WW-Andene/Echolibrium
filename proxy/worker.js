// Deploy this at workers.cloudflare.com
// Add your DeepInfra key as an environment variable called DEEPINFRA_KEY
// (Settings → Variables → Add, so it's never in the code)

export default {
  async fetch(request, env) {
    // Only accept POST
    if (request.method !== "POST") {
      return new Response("Method not allowed", { status: 405 });
    }

    // Read the app's request (same shape as DeepInfra's API)
    const body = await request.json();

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
