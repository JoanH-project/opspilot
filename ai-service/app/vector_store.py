from __future__ import annotations

import uuid
from typing import Protocol

from qdrant_client import QdrantClient, models


class VectorStore(Protocol):
    def replace_document(
        self,
        workspace_id: int,
        document_id: int,
        revision: int,
        points: list[models.PointStruct],
    ) -> bool: ...

    def search(
        self, workspace_id: int, vector: list[float], limit: int
    ) -> list[models.ScoredPoint]: ...


class QdrantVectorStore:
    def __init__(self, url: str, collection: str):
        self.client = QdrantClient(url=url, timeout=5)
        self.collection = collection

    def ensure_collection(self) -> None:
        if self.client.collection_exists(self.collection):
            return
        self.client.create_collection(
            collection_name=self.collection,
            vectors_config=models.VectorParams(size=384, distance=models.Distance.COSINE),
            hnsw_config=models.HnswConfigDiff(m=0, payload_m=16),
        )
        self.client.create_payload_index(
            collection_name=self.collection,
            field_name="workspace_id",
            field_schema=models.KeywordIndexParams(
                type=models.KeywordIndexType.KEYWORD,
                is_tenant=True,
            ),
            wait=True,
        )
        self.client.create_payload_index(
            collection_name=self.collection,
            field_name="is_tombstone",
            field_schema=models.PayloadSchemaType.BOOL,
            wait=True,
        )
        self.client.create_payload_index(
            collection_name=self.collection,
            field_name="document_id",
            field_schema=models.PayloadSchemaType.INTEGER,
            wait=True,
        )

    def replace_document(
        self,
        workspace_id: int,
        document_id: int,
        revision: int,
        points: list[models.PointStruct],
    ) -> bool:
        """Replace one document only when the incoming revision is not stale."""
        self.ensure_collection()
        existing, _ = self.client.scroll(
            collection_name=self.collection,
            scroll_filter=self._document_filter(workspace_id, document_id),
            limit=1000,
            with_payload=["revision"],
            with_vectors=False,
        )
        latest_revision = max(
            (int(point.payload.get("revision", 0)) for point in existing if point.payload),
            default=0,
        )
        if revision < latest_revision:
            return False

        self.client.delete(
            collection_name=self.collection,
            points_selector=models.FilterSelector(
                filter=self._document_filter(workspace_id, document_id)
            ),
            wait=True,
        )
        marker_id = str(uuid.uuid5(uuid.NAMESPACE_URL, f"opspilot-document-revision:{document_id}"))
        marker = models.PointStruct(
            id=marker_id,
            vector=[1.0, *([0.0] * 383)],
            payload={
                "workspace_id": str(workspace_id),
                "document_id": document_id,
                "revision": revision,
                "is_tombstone": True,
            },
        )
        self.client.upsert(
            collection_name=self.collection,
            points=[marker, *points],
            wait=True,
        )
        return True

    def search(
        self, workspace_id: int, vector: list[float], limit: int
    ) -> list[models.ScoredPoint]:
        self.ensure_collection()
        result = self.client.query_points(
            collection_name=self.collection,
            query=vector,
            query_filter=models.Filter(
                must=[
                    models.FieldCondition(
                        key="workspace_id",
                        match=models.MatchValue(value=str(workspace_id)),
                    )
                ],
                must_not=[
                    models.FieldCondition(
                        key="is_tombstone",
                        match=models.MatchValue(value=True),
                    )
                ],
            ),
            limit=limit,
            with_payload=True,
        )
        return result.points

    @staticmethod
    def _document_filter(workspace_id: int, document_id: int) -> models.Filter:
        return models.Filter(
            must=[
                models.FieldCondition(
                    key="workspace_id",
                    match=models.MatchValue(value=str(workspace_id)),
                ),
                models.FieldCondition(
                    key="document_id",
                    match=models.MatchValue(value=document_id),
                ),
            ]
        )
