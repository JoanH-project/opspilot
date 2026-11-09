import os
import uuid

from qdrant_client import QdrantClient, models

from app.vector_store import QdrantVectorStore


def test_qdrant_tenant_filter_and_revision_tombstone():
    url = os.getenv("AI_TEST_QDRANT_URL")
    collection = f"opspilot_test_{uuid.uuid4().hex[:12]}"
    if url:
        client = QdrantClient(url=url)
    else:
        client = QdrantClient(":memory:")
    store = QdrantVectorStore.__new__(QdrantVectorStore)
    store.client = client
    store.collection = collection
    try:
        store.ensure_collection()
        vector = [1.0, *([0.0] * 383)]
        point = models.PointStruct(
            id="9ac67396-2394-40b7-b5dc-0ff01000bd4b",
            vector=vector,
            payload={
                "workspace_id": "7",
                "document_id": 3,
                "revision": 4,
                "title": "Runbook",
                "chunk_id": "chunk-a",
                "chunk_index": 0,
                "text": "Database maintenance window.",
                "source_start": 0,
                "source_end": 29,
                "is_tombstone": False,
            },
        )
        assert store.replace_document(7, 3, 4, [point])

        assert len(store.search(7, vector, 5)) == 1
        assert store.search(8, vector, 5) == []
        assert not store.replace_document(7, 3, 3, [point])
        assert store.replace_document(7, 3, 5, [])
        assert store.search(7, vector, 5) == []
    finally:
        if client.collection_exists(collection):
            client.delete_collection(collection)
        client.close()
