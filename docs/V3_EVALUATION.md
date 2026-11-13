# V3 Retrieval Evaluation

The repository includes a synthetic bilingual corpus at `ai-service/evaluation/corpus.json`. It covers exact and paraphrased English queries, Chinese retrieval, multi-document answers, revision replacement, archived-only and cross-workspace queries, and several unsupported topics. All content is synthetic and contains no user data. The current fixture has 13 document revisions and 23 labeled queries (15 positive and 8 negative).

## Metrics

`scripts/evaluate_retrieval.py` indexes the corpus into a uniquely named temporary Qdrant collection, applies the archive marker, runs all labeled queries at `k=5`, and deletes its collection in a `finally` block. It prints JSON with:

- **Recall@5:** relevant document IDs retrieved divided by relevant document IDs across positive queries.
- **Hit@5:** proportion of positive queries with at least one relevant result.
- **MRR@5:** mean reciprocal rank of the first relevant result across positive queries.
- **Negative queries empty:** count of no-answer, cross-workspace-only, and archived-only queries that returned no result after the configured score threshold.
- Per-query retrieved IDs and scores, latency, model revision, Qdrant version, and a SHA-256 of the loaded `.safetensors` weights when present.

These metrics are a regression fixture, not a claim of production answer quality. On the current corpus, the lowest retrieved relevant score is 0.8265 and the highest retrieved score for a negative query is 0.794. The local cutoff is therefore set to 0.81; it leaves a 0.0165 margin below the lowest positive and returns no results for all eight negative queries. The observed run recorded in `docs/evaluation/v3-local-2026-11-09.json` has Recall@5 1.0, Hit@5 1.0, MRR@5 0.9333, and negative queries empty 8/8. Keep corpus labels fixed for comparisons and add a separate, larger held-out query set before making broader relevance claims or retuning the cutoff.

## Run against the isolated local Qdrant and real E5 model

Start the isolated V3 stack as described in the root README, then pull the local Qwen model if answer generation will also be checked. The evaluation container mounts only the tracked evaluation script and corpus read-only; it uses the same locked AI-service image, E5 cache, and private Qdrant network as the application:

```bash
docker compose -f docker-compose.v3.yml run --rm --no-deps \
  --env PYTHONPATH=/app \
  --volume "$PWD/ai-service/scripts:/opt/eval/scripts:ro" \
  --volume "$PWD/ai-service/evaluation:/opt/eval/evaluation:ro" \
  --entrypoint /app/.venv/bin/python ai-service \
  /opt/eval/scripts/evaluate_retrieval.py
```

The first indexing run downloads the pinned E5 model into `opspilot_v3_ai_model_cache`; subsequent runs reuse it. The script reports actual metrics only after this command completes, including the configured `search_min_score`. Record the full JSON output, model revision/weight digest, Compose image versions, and date in the acceptance record after each real run.

## Automated tests

Python unit tests cover chunking bounds, private-token enforcement, prompt-injection framing, source-ID validation, refusal behavior, sanitized local-model failure, tenant filters, stale revision rejection, and archive tombstones. AI Service CI additionally runs the vector-store test against a Qdrant server container, runs Ruff, and builds the locked Docker image. CI does not download model weights or claim an E5 retrieval score.

## Answer-model verification

Retrieval evaluation does not call Ollama. Before accepting grounded answers, use the manual checklist in `docs/V3_ACCEPTANCE.md` to verify that Ollama is running the pinned local model, evidence is cited, unsupported questions are refused, injected instructions in a document are not followed, and an Ollama outage leaves search usable while answer requests fail with a sanitized response.
