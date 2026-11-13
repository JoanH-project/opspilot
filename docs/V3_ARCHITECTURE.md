# V3 Knowledge / Retrieval-Augmented Generation Architecture

V3 adds workspace-scoped retrieval and grounded answers while keeping the V1 business API contract frozen. MySQL remains authoritative. Qdrant is a disposable search index derived from active MySQL documents.

## Request and trust boundaries

```text
Browser
  │ JWT, public API only
  ▼
Spring Boot (authentication, workspace membership/RBAC, source-of-truth checks)
  ├── MySQL: documents, revisions, index outbox/status, reindex cooldown
  │
  └── private Docker network + shared internal token
        ├── FastAPI: Markdown chunking, E5 embeddings, retrieval, local answer request
        ├── Qdrant: tenant-filtered derived vectors
        └── Ollama: local Qwen2.5 answer generation
```

Only Spring Boot is published to the host by the V3 Compose file. The FastAPI, Qdrant, Redis, and Ollama ports are not published. Browser requests never call those services directly. Spring checks workspace membership before and after AI calls. It validates every search result against an ACTIVE document in the same workspace and its current revision; answer citations are checked again after generation. AI and Qdrant failures cannot grant access or make stale/archived content authoritative.

The internal token authenticates service-to-service calls, not end users. It must be at least 32 characters and must not be committed. Internal FastAPI routes are not part of the public API.

## Document write and index lifecycle

Create, edit, and archive operations write the document revision and its `document_index_states` row in the same MySQL transaction. A scheduled Spring worker claims one leased row at a time and sends the current active document for indexing or an archive tombstone. Transient failures use bounded exponential retry; after eight attempts the row remains `FAILED` until a permitted reindex queues it again. A worker lease allows a crashed job to be retried. Index status is derived from MySQL, never from Qdrant.

V8 adds the document revision, transactional index state, and a per-workspace reindex cooldown. It seeds active V1/V2 documents as pending. V1–V7 migrations and `docs/API_CONTRACT.md` are unchanged. Reindex is OWNER/ADMIN only, has a five-minute workspace cooldown, and refuses workspaces with more than 500 active documents.

## Retrieval and grounding

- Chunk Markdown into bounded overlapping passages (maximum 450 model tokens), retaining document revision and source offsets.
- Embed passages and queries using `intfloat/multilingual-e5-small`, with `passage:` and `query:` prefixes, normalized 384-dimensional vectors, CPU inference, and a pinned model revision.
- Filter Qdrant by the exact workspace tenant and exclude tombstone markers. Replacing a document first removes its previous revision; a revision marker prevents delayed stale jobs from resurrecting old chunks.
- Spring then checks the workspace, ACTIVE status, and current MySQL revision before exposing excerpts or giving evidence to the answer model. It checks membership and revisions again before returning a generated answer.
- Qwen receives only verified excerpts and a system instruction that treats both the question and source text as untrusted data. The service rejects fabricated source IDs, returns no answer without evidence, strips model-generated citation markers and URLs, and maps citations to server-verified chunks.
- Ollama is optional for retrieval. If it is unavailable, search remains usable and answer requests return a sanitized `503`.

This design reduces prompt-injection and stale-data risk; it does not make a small local language model infallible. Answers must remain visibly cited and can be refused.

## Local stack and models

`docker-compose.v3.yml` uses unique `opspilot-v3-*` containers and `opspilot_v3_*` volumes. Its MySQL host port defaults to 3310 and backend port to 8084; it does not reuse or stop the original MySQL service or V2 stack. Qdrant and Ollama remain on the private Compose network. The E5 weights are downloaded on first indexing into a persistent model-cache volume; the Qwen model is pulled explicitly with `ollama pull`. No paid inference API or cloud runtime is configured.

The pinned E5 model revision is [`614241f622f53c4eeff9890bdc4f31cfecc418b3`](https://huggingface.co/intfloat/multilingual-e5-small/tree/614241f622f53c4eeff9890bdc4f31cfecc418b3); the model card lists MIT licensing. The local answer model is [`qwen2.5:1.5b-instruct`](https://ollama.com/library/qwen2.5%3A1.5b-instruct), a 1.54B Q4_K_M model listed under Apache 2.0. Review upstream licenses and model terms before redistribution or deployment.

## Deliberate boundaries

V3 adds no Agent, task-changing tools, MCP, memory, external hosted AI API, new public authentication system, or microservice decomposition. Spring remains the authorization and business-data boundary. RAG retrieval and answers are the only AI capabilities in scope.
