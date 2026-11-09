from __future__ import annotations

from functools import cached_property
from typing import Protocol

import numpy as np
from sentence_transformers import SentenceTransformer
from transformers import AutoTokenizer


class Embeddings(Protocol):
    def embed_passages(self, texts: list[str]) -> list[list[float]]: ...

    def embed_query(self, text: str) -> list[float]: ...


class E5Embeddings:
    def __init__(self, model_id: str, revision: str, cache_dir: str):
        self.model_id = model_id
        self.revision = revision or None
        self.cache_dir = cache_dir

    @cached_property
    def tokenizer(self):
        return AutoTokenizer.from_pretrained(
            self.model_id, revision=self.revision, cache_dir=self.cache_dir
        )

    @cached_property
    def model(self):
        model = SentenceTransformer(
            self.model_id,
            revision=self.revision,
            device="cpu",
            cache_folder=self.cache_dir,
        )
        model.max_seq_length = 512
        return model

    def embed_passages(self, texts: list[str]) -> list[list[float]]:
        values = self.model.encode(
            [f"passage: {text}" for text in texts],
            batch_size=8,
            normalize_embeddings=True,
            convert_to_numpy=True,
            show_progress_bar=False,
        )
        return self._vectors(values)

    def embed_query(self, text: str) -> list[float]:
        values = self.model.encode(
            [f"query: {text}"],
            batch_size=1,
            normalize_embeddings=True,
            convert_to_numpy=True,
            show_progress_bar=False,
        )
        return self._vectors(values)[0]

    @staticmethod
    def _vectors(values: np.ndarray) -> list[list[float]]:
        return [[float(value) for value in row] for row in values]
