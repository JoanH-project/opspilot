from __future__ import annotations

from dataclasses import dataclass


class CharacterTokenizer:
    def encode(self, text: str, *, add_special_tokens: bool = True) -> list[int]:
        prefix = 1 if add_special_tokens else 0
        return (
            [prefix, *[ord(character) for character in text]]
            if prefix
            else [ord(character) for character in text]
        )

    def decode(self, token_ids: list[int], *, skip_special_tokens: bool = True) -> str:
        values = (
            token_ids[1:] if skip_special_tokens and token_ids and token_ids[0] == 1 else token_ids
        )
        return "".join(chr(value) for value in values if value > 1)


class FakeEmbeddings:
    def __init__(self, vectors: list[list[float]] | None = None):
        self.vectors = vectors or [[1.0, *([0.0] * 383)]]
        self.passages: list[str] = []
        self.queries: list[str] = []

    def embed_passages(self, texts: list[str]) -> list[list[float]]:
        self.passages.extend(texts)
        return [self.vectors[0] for _ in texts]

    def embed_query(self, text: str) -> list[float]:
        self.queries.append(text)
        return self.vectors[0]


@dataclass
class FakePoint:
    payload: dict
    score: float = 0.9


class FakeVectorStore:
    def __init__(self):
        self.points: list[FakePoint] = []
        self.filters: list[int] = []
        self.replaced: list[tuple[int, int, int, int]] = []
        self.revisions: dict[tuple[int, int], int] = {}

    def replace_document(self, workspace_id, document_id, revision, points):
        self.replaced.append((workspace_id, document_id, revision, len(points)))
        key = (workspace_id, document_id)
        if revision < self.revisions.get(key, 0):
            return False
        self.revisions[key] = revision
        self.points = [
            point
            for point in self.points
            if not (
                int(point.payload.get("workspace_id", 0)) == workspace_id
                and int(point.payload.get("document_id", 0)) == document_id
            )
        ]
        self.points.extend(FakePoint(point.payload, 0.9) for point in points)
        return True

    def search(self, workspace_id, vector, limit):
        self.filters.append(workspace_id)
        return [
            point
            for point in self.points
            if int(point.payload.get("workspace_id", 0)) == workspace_id
            and not point.payload.get("is_tombstone")
        ][:limit]
