# AI Assistant evaluation (AI-01..AI-11)

Script: `AiEval.java` (Java 17, no libraries). Run it from the repository root.

    set AI_API_KEY=<your key>
    java tests/ai-eval/AiEval.java --base-url https://openrouter.ai/api/v1 --model <model id> --letters L01,L17 --runs 2    (try first)
    java tests/ai-eval/AiEval.java --base-url https://openrouter.ai/api/v1 --model <model id>                              (full run: 20 letters x 5 runs)

Never commit an API key: the script reads it from the environment variable AI_API_KEY.

Output goes to `tests/ai-eval/raw/`: one JSON file per call (raw answer, model, temperature, prompt version, time),
`results-<prompt version>.csv` (score, in band, distance outside the band, item counts, response time) and
`summary-<prompt version>.txt` (per letter min, max, spread and average; AI-09 asks for a spread of at most 10).
The script uses the same prompt text and temperature (1.3) as the Portal (`PromptBuilder`, `AiClient`). A failed call is
kept as `..._failed-<time>.json` and retried on the next run.

The evaluation letters and their reference bands are in `ai-eval/` at the root of the repository.

Provider: OpenRouter
Model: nvidia/nemotron-3-ultra-550b-a55b:free
Run dates: <fill in>
