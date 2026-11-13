# V3 Knowledge API

All public endpoints below are Spring Boot endpoints and require `Authorization: Bearer <accessToken>`. The authenticated user must be a member of the path workspace. The V1 contract in `docs/API_CONTRACT.md` remains unchanged; these V3 endpoints are additive.

## Search passages

`POST /api/workspaces/{workspaceId}/knowledge/search`

```json
{"query":"What is the database failover procedure?","limit":5}
```

`query` is required and limited to 2,000 characters. `limit` is optional and must be 1–10 (default 5). The response contains at most that many current, active, workspace-verified references:

```json
{
  "results": [
    {
      "documentId": 12,
      "workspaceId": 3,
      "revision": 0,
      "title": "Database recovery",
      "chunkId": "server-verified-chunk-id",
      "chunkIndex": 0,
      "excerpt": "Verify the replica is healthy before restoring service.",
      "score": 0.83,
      "sourceStart": 0,
      "sourceEnd": 62,
      "href": "/app/3/documents/12"
    }
  ]
}
```

`revision` is an opaque optimistic-lock version and can be zero for a newly created document. Clients should not infer business meaning from it.

## Ask a grounded question

`POST /api/workspaces/{workspaceId}/knowledge/ask`

```json
{"question":"What should I verify before restoring service?"}
```

The question is required and limited to 1,500 characters. A successful grounded response is:

```json
{
  "status": "ANSWERED",
  "answer": "Verify the replica is healthy before restoring service.",
  "sources": [ /* current Knowledge reference objects */ ]
}
```

When no verified evidence exists, the API returns HTTP 200 with `status: "INSUFFICIENT_EVIDENCE"`, a refusal message, and an empty `sources` array. If the local answer model is unavailable, the API returns a sanitized `503`; passage search does not depend on Ollama.

## Index status

`GET /api/workspaces/{workspaceId}/knowledge/index-status`

Returns counts from MySQL:

```json
{"total":12,"pending":2,"indexing":1,"ready":8,"failed":1}
```

## Reindex active documents

`POST /api/workspaces/{workspaceId}/knowledge/reindex`

OWNER and ADMIN are required. It returns HTTP `202` after queueing active documents:

```json
{"queuedDocuments":12,"status":"QUEUED"}
```

A workspace may have at most 500 active documents for this operation; a successful request starts a five-minute workspace cooldown. Reindex resets failed index attempts for active documents.

## Errors

Errors use the existing `ApiError` response shape (`timestamp`, `status`, `error`, `message`, `fieldErrors`). Relevant statuses:

- `400`: invalid body, query, or limit
- `401`: missing or invalid access token
- `403` / `404`: insufficient workspace role or unavailable workspace
- `409`: document changed while an answer was being generated
- `413`: reindex workspace exceeds 500 active documents
- `429`: workspace reindex cooldown; response includes `Retry-After`
- `503`: local AI/vector service unavailable

Database details, internal service URLs, model errors, stack traces, and internal credentials are not returned. The Python service's `/internal/v1/*` routes are private implementation details and must not be called by browsers.
