from pydantic import BaseModel, Field


class DocumentIndexRequest(BaseModel):
    workspace_id: int = Field(gt=0)
    document_id: int = Field(gt=0)
    revision: int = Field(ge=0)
    title: str = Field(min_length=1, max_length=200)
    content: str = Field(max_length=1_000_000)


class DocumentIndexResponse(BaseModel):
    revision: int
    chunks_indexed: int


class SearchRequest(BaseModel):
    workspace_id: int = Field(gt=0)
    query: str = Field(min_length=1, max_length=2000)
    limit: int = Field(default=5, ge=1, le=10)


class SearchResult(BaseModel):
    workspace_id: int
    document_id: int
    revision: int
    title: str
    chunk_id: str
    chunk_index: int
    text: str
    score: float
    source_start: int
    source_end: int


class SearchResponse(BaseModel):
    results: list[SearchResult]


class Evidence(BaseModel):
    workspace_id: int = Field(gt=0)
    document_id: int = Field(gt=0)
    revision: int = Field(ge=0)
    title: str = Field(min_length=1, max_length=200)
    chunk_id: str = Field(min_length=1, max_length=80)
    text: str = Field(min_length=1, max_length=3000)


class AnswerRequest(BaseModel):
    question: str = Field(min_length=1, max_length=1500)
    evidence: list[Evidence] = Field(max_length=8)


class AnswerResponse(BaseModel):
    status: str
    answer: str
    cited_chunk_ids: list[str]
