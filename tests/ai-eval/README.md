# AI Assistant evaluation (AI-01..AI-11)

Owner: Yousef. This folder holds the evaluation script, the fake provider used to reproduce Portal defects, and the
results of our baseline run.

| File | What it is |
| --- | --- |
| `AiEval.java` | Sends the 20 letters of `ai-eval/letters.json` to a provider 5 times each and checks the answers |
| `AiCapture.java` | A fake AI provider for the Portal: prints every request the Portal sends and can delay its answer |
| `results-baseline.csv` | One line per call: score, reference band, in band or not, distance outside the band, item counts, response time |
| `summary-baseline.txt` | Per letter: runs, runs in band, min, max, spread (AI-09 asks for at most 10), average; response times |
| `baseline_Lnn_runN.json` | The raw answer of every call, with model, temperature, prompt version and time |

In this repository the results of our run were uploaded to this folder. When you run the script yourself, it writes
to `tests/ai-eval/raw/` (change it with `--out`).

## Running the evaluation

Java 17 or later, no libraries. Run from the repository root.

    set AI_API_KEY=<your key>
    java tests/ai-eval/AiEval.java --base-url https://openrouter.ai/api/v1 --model <model id> --letters L01,L17 --runs 2    (try first)
    java tests/ai-eval/AiEval.java --base-url https://openrouter.ai/api/v1 --model <model id>                              (full run: 20 letters x 5 runs)

- Never commit an API key: the script reads it from the environment variable `AI_API_KEY`.
- The script uses the same prompt text and temperature (1.3) as the Portal (`PromptBuilder`, `AiClient`).
- A run whose raw file already exists is skipped, so an interrupted run can be continued.
- A failed call is kept as `..._failed-<time>.json` and retried on the next run. On HTTP 429 (daily limit of the free
  model) the script stops and says so.
- Other options: `--runs`, `--letters`, `--temperature`, `--prompt-version`, `--out`, `--pause-ms`, `--no-key`.

## Baseline run

| Item | Value |
| --- | --- |
| Provider | OpenRouter (free tier) |
| Model | `nvidia/nemotron-3-ultra-550b-a55b:free` |
| Prompt | The Portal's own prompt (version `baseline`), temperature 1.3 |
| Run dates | 24/11/2026 and 26/11/2026 (the free model allows about 50 requests per day) |
| Calls | 99 valid answers (L12 has 4 runs instead of 5 because the daily limit was reached); 2 failed attempts were retried |

Results (see `summary-baseline.txt` for every letter):

- **Reference bands:** 82 of 99 runs (83%) were inside the band. Medium letters were scored low most often (L07, L08, L09 and L12).
- **AI-09 (spread of at most 10):** six letters break it: L06 (12), L07 (15), L16 (13), L17 (30), L18 (20) and L20 (15).
- **AI-05 (10 second limit):** 97 of 99 calls took longer than 10 s (average 19 s, slowest 62 s). This is a property of the free model.
  The Portal itself has no time-out, which is a defect.
- **Not yet analysed here:** the checks for AI-03, AI-04 and AI-10 on the injection and personal-data letters (L17 to L20).

The reference bands are a reviewer's judgement, not exact truth. Where our result disagrees with a band, the report says so.
