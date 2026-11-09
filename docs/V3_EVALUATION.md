# V3 Retrieval Evaluation

The repository includes a small synthetic bilingual corpus at `ai-service/evaluation/corpus.json`. It covers exact and paraphrased English queries, Chinese retrieval, a query needing two documents, a newer revision replacing an older one, an archived-only document, a no-answer query, and a cross-workspace-only query. All content is synthetic and contains no customer data.

## Metrics

`scripts/evaluate_retrieval.py` indexes the corpus into a uniquely named temporary Qdrant collection, applies the archive marker, runs all labeled queries at `k=5`, and deletes its collection in a `finally` block. It prints JSON with:

- **Recall@5:** relevant document IDs retrieved divided by relevant document IDs across positive queries.
- **Hit@5:** proportion of positive queries with at least one relevant result.
- **MRR@5:** mean reciprocal rank of the first relevant result across positive queries.
- **Negative queries empty:** count of no-answer, cross-workspace-only, and archived-only queries that returned no result after the configured score threshold.
- Per-query retrieved IDs and scores, latency, model revision, Qdrant version, and a SHA-256 of the loaded `.safetensors` weights when present.

These are retrieval metrics on a tiny synthetic corpus, not a claim of production answer quality. Do not tune the threshold on this corpus alone. Keep the corpus labels fixed for comparisons and add separate test cases before changing ranking behavior.

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

The first indexing run downloads the pinned E5 model into `opspilot_v3_ai_model_cache`; subsequent runs reuse it. The script reports actual metrics only after this command completes. Record the full JSON output, model revision/weight digest, Compose image versions, and date in the acceptance record when a real run is available.

## Automated tests

Python unit tests cover chunking bounds, private-token enforcement, prompt-injection framing, source-ID validation, refusal behavior, sanitized local-model failure, tenant filters, stale revision rejection, and archive tombstones. AI Service CI additionally runs the vector-store test against a Qdrant server container, runs Ruff, and builds the locked Docker image. CI does not download model weights or claim an E5 retrieval score.

## Answer-model verification

Retrieval evaluation does not call Ollama. Before accepting grounded answers, use the manual checklist in `docs/V3_ACCEPTANCE.md` to verify that Ollama is running the pinned local model, evidence is cited, unsupported questions are refused, injected instructions in a document are not followed, and an Ollama outage leaves search usable while answer requests fail with a sanitized response.
