from __future__ import annotations

import hmac
from functools import lru_cache

from fastapi import Depends, FastAPI, Header, HTTPException, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.chunking import MarkdownChunker
from app.embedding import E5Embeddings
from app.models import (
    AnswerRequest,
    AnswerResponse,
    DocumentIndexRequest,
    DocumentIndexResponse,
    SearchRequest,
    SearchResponse,
)
from app.service import AiServiceUnavailable, KnowledgeService
from app.settings import Settings, get_settings
from app.vector_store import QdrantVectorStore


@lru_cache
def create_knowledge_service() -> KnowledgeService:
    settings = get_settings()
    embeddings = E5Embeddings(
        model_id=settings.embedding_model,
        revision=settings.embedding_revision,
        cache_dir=settings.model_cache_dir,
    )
    vectors = QdrantVectorStore(settings.qdrant_url, settings.qdrant_collection)
    return KnowledgeService(settings, embeddings, vectors, MarkdownChunker(embeddings.tokenizer))


def create_app(
    settings: Settings | None = None, service: KnowledgeService | None = None
) -> FastAPI:
    configured = settings or get_settings()
    app = FastAPI(title="OpsPilot private knowledge service", docs_url=None, redoc_url=None)

    @app.exception_handler(RequestValidationError)
    async def validation_error_handler(
        _request: Request, _exception: RequestValidationError
    ) -> JSONResponse:
        return JSONResponse(status_code=422, content={"detail": "Invalid internal request"})

    def require_internal_token(x_opspilot_service_token: str | None = Header(default=None)) -> None:
        if not x_opspilot_service_token or not hmac.compare_digest(
            x_opspilot_service_token, configured.internal_token
        ):
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Unauthorized")

    def knowledge() -> KnowledgeService:
        return service or create_knowledge_service()

    @app.get("/healthz")
    async def health() -> dict[str, str]:
        return {"status": "UP"}

    @app.post(
        "/internal/v1/documents/index",
        response_model=DocumentIndexResponse,
        dependencies=[Depends(require_internal_token)],
    )
    async def index_document(request: DocumentIndexRequest) -> DocumentIndexResponse:
        try:
            count = await knowledge().index(request)
            return DocumentIndexResponse(revision=request.revision, chunks_indexed=count)
        except ValueError as exc:
            raise HTTPException(status_code=400, detail="Document cannot be indexed") from exc
        except Exception as exc:
            raise HTTPException(status_code=503, detail="Knowledge service unavailable") from exc

    @app.post(
        "/internal/v1/documents/{workspace_id}/{document_id}/{revision}/archive",
        dependencies=[Depends(require_internal_token)],
    )
    async def archive_document(
        workspace_id: int,
        document_id: int,
        revision: int,
    ) -> dict[str, int]:
        service_instance = knowledge()
        try:
            await service_instance.archive(workspace_id, document_id, revision)
        except Exception as exc:
            raise HTTPException(status_code=503, detail="Knowledge service unavailable") from exc
        return {"revision": revision}

    @app.post(
        "/internal/v1/search",
        response_model=SearchResponse,
        dependencies=[Depends(require_internal_token)],
    )
    async def search(request: SearchRequest) -> SearchResponse:
        try:
            return await knowledge().search(request)
        except ValueError as exc:
            raise HTTPException(status_code=400, detail="Invalid search request") from exc
        except Exception as exc:
            raise HTTPException(status_code=503, detail="Knowledge service unavailable") from exc

    @app.post(
        "/internal/v1/answer",
        response_model=AnswerResponse,
        dependencies=[Depends(require_internal_token)],
    )
    async def answer(request: AnswerRequest) -> AnswerResponse:
        try:
            return await knowledge().answer(request)
        except AiServiceUnavailable as exc:
            raise HTTPException(status_code=503, detail="Local answer model unavailable") from exc
        except ValueError as exc:
            raise HTTPException(status_code=400, detail="Invalid answer request") from exc

    return app


app = create_app()
