from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="AI_", case_sensitive=False)

    internal_token: str = Field(min_length=32)
    qdrant_url: str = "http://qdrant:6333"
    qdrant_collection: str = "opspilot_knowledge"
    ollama_base_url: str = "http://ollama:11434"
    ollama_model: str = "qwen2.5:1.5b-instruct"
    embedding_model: str = "intfloat/multilingual-e5-small"
    embedding_revision: str = "614241f622f53c4eeff9890bdc4f31cfecc418b3"
    model_cache_dir: str = "/var/cache/huggingface"
    max_concurrent_embeddings: int = Field(default=1, ge=1, le=2)
    max_chunks_per_document: int = Field(default=400, ge=1, le=1000)
    max_search_results: int = Field(default=10, ge=1, le=20)
    search_min_score: float = Field(default=0.81, ge=-1, le=1)
    max_context_characters: int = Field(default=12000, ge=1000, le=20000)
    ollama_timeout_seconds: float = Field(default=300, ge=1, le=300)


@lru_cache
def get_settings() -> Settings:
    return Settings()
