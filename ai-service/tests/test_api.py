import json

from fastapi.testclient import TestClient

from app.chunking import MarkdownChunker
from app.main import create_app
from app.service import KnowledgeService
from app.settings import Settings
from tests.fakes import CharacterTokenizer, FakeEmbeddings, FakeVectorStore

TOKEN = "test-only-internal-token-0123456789abcd"


def client():
    settings = Settings(internal_token=TOKEN)
    service = KnowledgeService(
        settings,
        FakeEmbeddings(),
        FakeVectorStore(),
        MarkdownChunker(CharacterTokenizer()),
        http=FakeHttp(json.dumps({"answer": "A", "source_ids": ["S1"]})),
    )
    return TestClient(create_app(settings=settings, service=service))


def test_internal_routes_require_shared_secret():
    response = client().post(
        "/internal/v1/search",
        json={"workspace_id": 1, "query": "question"},
    )

    assert response.status_code == 401
    assert response.json() == {"detail": "Unauthorized"}


def test_search_accepts_only_bounded_workspace_queries():
    test_client = client()
    response = test_client.post(
        "/internal/v1/search",
        headers={"X-Opspilot-Service-Token": TOKEN},
        json={"workspace_id": 1, "query": "hello", "limit": 11},
    )

    assert response.status_code == 422
    assert response.json() == {"detail": "Invalid internal request"}


def test_index_route_accepts_zero_revision_for_new_jpa_documents():
    response = client().post(
        "/internal/v1/documents/index",
        headers={"X-Opspilot-Service-Token": TOKEN},
        json={
            "workspace_id": 1,
            "document_id": 2,
            "revision": 0,
            "title": "New document",
            "content": "A new document starts at JPA version zero.",
        },
    )

    assert response.status_code == 200
    assert response.json()["revision"] == 0


def test_model_answer_endpoint_maps_verified_source_ids():
    response = client().post(
        "/internal/v1/answer",
        headers={"X-Opspilot-Service-Token": TOKEN},
        json={
            "question": "Question",
            "evidence": [
                {
                    "workspace_id": 1,
                    "document_id": 2,
                    "revision": 1,
                    "title": "Runbook",
                    "chunk_id": "chunk-1",
                    "text": "Restart the service.",
                }
            ],
        },
    )

    assert response.status_code == 200
    assert response.json() == {
        "status": "ANSWERED",
        "answer": "A",
        "cited_chunk_ids": ["chunk-1"],
    }


class FakeHttp:
    def __init__(self, content):
        self.content = content

    async def post(self, url, json):
        return FakeHttpResponse(self.content)


class FakeHttpResponse:
    def __init__(self, content):
        self.content = content

    def raise_for_status(self):
        return None

    def json(self):
        return {"message": {"content": self.content}}
