from app.chunking import MarkdownChunker
from tests.fakes import CharacterTokenizer


def test_empty_markdown_has_no_chunks():
    assert MarkdownChunker(CharacterTokenizer()).chunk(" \n\t") == []


def test_english_markdown_keeps_heading_and_removes_link_markup():
    chunks = MarkdownChunker(CharacterTokenizer(), max_tokens=200).chunk(
        "# Incident response\n\nRead the [runbook](https://invalid.example/runbook) before paging."
    )

    assert len(chunks) == 1
    assert "Incident response" in chunks[0].text
    assert "runbook" in chunks[0].text
    assert "https://invalid.example" not in chunks[0].text
    assert chunks[0].source_start > 0
    assert chunks[0].source_end > chunks[0].source_start


def test_chinese_text_is_split_to_the_model_token_limit_with_overlap():
    markdown = "# 运维手册\n\n" + ("服务发生故障时先检查健康状态，然后联系值班工程师。" * 30)
    chunker = MarkdownChunker(CharacterTokenizer(), max_tokens=100, overlap_tokens=12)

    chunks = chunker.chunk(markdown)

    assert len(chunks) > 2
    assert all(
        len(CharacterTokenizer().encode(f"passage: {chunk.text}")) <= 100 for chunk in chunks
    )
    assert all(chunk.source_start < chunk.source_end for chunk in chunks)
    assert "运维手册" in chunks[0].text
    assert any(
        left.text[-8:] in right.text for left, right in zip(chunks, chunks[1:], strict=False)
    )
