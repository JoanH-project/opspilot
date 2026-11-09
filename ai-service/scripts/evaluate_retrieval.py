from __future__ import annotations

import asyncio
import hashlib
import json
import time
import uuid
from pathlib import Path

import httpx
from qdrant_client import QdrantClient

from app.chunking import MarkdownChunker
from app.embedding import E5Embeddings
from app.models import DocumentIndexRequest, SearchRequest
from app.service import KnowledgeService
from app.settings import get_settings
from app.vector_store import QdrantVectorStore

CORPUS = Path(__file__).parent.parent / "evaluation" / "corpus.json"


def weight_sha256(cache_dir: str, model_id: str, revision: str) -> str | None:
    model_directory = Path(cache_dir) / f"models--{model_id.replace('/', '--')}" / "snapshots"
    snapshots = list(model_directory.glob(f"{revision}*"))
    if not snapshots:
        return None
    weights = list(snapshots[0].glob("*.safetensors"))
    if not weights:
        return None
    digest = hashlib.sha256()
    with weights[0].open("rb") as model_file:
        for block in iter(lambda: model_file.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


async def evaluate() -> dict:
    settings = get_settings()
    data = json.loads(CORPUS.read_text())
    collection = f"opspilot_eval_{uuid.uuid4().hex[:12]}"
    embeddings = E5Embeddings(
        settings.embedding_model, settings.embedding_revision, settings.model_cache_dir
    )
    vectors = QdrantVectorStore(settings.qdrant_url, collection)
    service = KnowledgeService(settings, embeddings, vectors, MarkdownChunker(embeddings.tokenizer))
    qdrant = QdrantClient(url=settings.qdrant_url)
    cases = []
    try:
        for document in data["documents"]:
            await service.index(
                DocumentIndexRequest(
                    workspace_id=document.get("workspace_id", data["workspace_id"]),
                    document_id=document["document_id"],
                    revision=document["revision"],
                    title=document["title"],
                    content=document["content"],
                )
            )
        await service.archive(
            data["workspace_id"], data["archived_document_id"], data["archived_revision"]
        )

        for case in data["queries"]:
            started = time.perf_counter()
            result = await service.search(
                SearchRequest(workspace_id=case["workspace_id"], query=case["query"], limit=5)
            )
            elapsed_ms = round((time.perf_counter() - started) * 1000, 1)
            document_ids = [hit.document_id for hit in result.results]
            relevant = case["relevant_document_ids"]
            ranks = [
                index
                for index, document_id in enumerate(document_ids, 1)
                if document_id in relevant
            ]
            cases.append(
                {
                    "id": case["id"],
                    "workspace_id": case["workspace_id"],
                    "query": case["query"],
                    "relevant_document_ids": relevant,
                    "retrieved_document_ids": document_ids,
                    "retrieved_scores": [
                        {"document_id": hit.document_id, "score": round(hit.score, 4)}
                        for hit in result.results
                    ],
                    "hit_at_5": bool(ranks),
                    "reciprocal_rank_at_5": round(1 / min(ranks), 4) if ranks else 0.0,
                    "elapsed_ms": elapsed_ms,
                }
            )

        positive_cases = [case for case in cases if case["relevant_document_ids"]]
        relevant_total = sum(len(case["relevant_document_ids"]) for case in positive_cases)
        found_relevant = sum(
            len(set(case["retrieved_document_ids"]) & set(case["relevant_document_ids"]))
            for case in positive_cases
        )
        negative_cases = [case for case in cases if not case["relevant_document_ids"]]
        root = httpx.get(f"{settings.qdrant_url.rstrip('/')}/", timeout=5).json()
        return {
            "embedding_model": settings.embedding_model,
            "embedding_revision": settings.embedding_revision,
            "embedding_weights_sha256": weight_sha256(
                settings.model_cache_dir, settings.embedding_model, settings.embedding_revision
            ),
            "dimensions": 384,
            "qdrant_version": root.get("version"),
            "collection": collection,
            "top_k": 5,
            "recall_at_5": round(found_relevant / relevant_total, 4) if relevant_total else None,
            "hit_at_5": round(
                sum(case["hit_at_5"] for case in positive_cases) / len(positive_cases), 4
            )
            if positive_cases
            else None,
            "mrr_at_5": round(
                sum(case["reciprocal_rank_at_5"] for case in positive_cases) / len(positive_cases),
                4,
            )
            if positive_cases
            else None,
            "negative_queries_empty": sum(
                not case["retrieved_document_ids"] for case in negative_cases
            ),
            "negative_queries_total": len(negative_cases),
            "cases": cases,
        }
    finally:
        await service.http.aclose()
        if qdrant.collection_exists(collection):
            qdrant.delete_collection(collection)


if __name__ == "__main__":
    print(json.dumps(asyncio.run(evaluate()), ensure_ascii=False, indent=2))
