from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Protocol


class Tokenizer(Protocol):
    def encode(self, text: str, *, add_special_tokens: bool = True) -> list[int]: ...

    def decode(self, token_ids: list[int], *, skip_special_tokens: bool = True) -> str: ...


@dataclass(frozen=True)
class Chunk:
    index: int
    text: str
    source_start: int
    source_end: int


_MARKDOWN_LINK = re.compile(r"!?(?:\[([^\]]+)\]\([^)]*\))")
_INLINE_CODE = re.compile(r"`([^`]*)`")
_HTML_TAG = re.compile(r"<[^>]+>")
_DECORATION = re.compile(r"(^\s{0,3}#{1,6}\s+|^\s*[-*+]\s+|^\s*\d+[.)]\s+|[*_~]{1,3})")


def _clean_markdown(text: str) -> str:
    text = _MARKDOWN_LINK.sub(r"\1", text)
    text = _INLINE_CODE.sub(r"\1", text)
    text = _HTML_TAG.sub(" ", text)
    lines = [_DECORATION.sub("", line) for line in text.splitlines()]
    return "\n".join(lines).strip()


class MarkdownChunker:
    """Split Markdown into bounded, overlapping, traceable passages."""

    def __init__(self, tokenizer: Tokenizer, max_tokens: int = 450, overlap_tokens: int = 48):
        if max_tokens > 510 or max_tokens < 32:
            raise ValueError("max_tokens must be between 32 and 510")
        if overlap_tokens < 0 or overlap_tokens >= max_tokens:
            raise ValueError("overlap_tokens must be smaller than max_tokens")
        self.tokenizer = tokenizer
        self.max_tokens = max_tokens
        self.overlap_tokens = overlap_tokens

    def chunk(self, markdown: str) -> list[Chunk]:
        if not markdown.strip():
            return []

        blocks = self._blocks(markdown)
        passages: list[tuple[str, int, int]] = []
        for text, start, end in blocks:
            passages.extend(self._bounded_parts(text, start, end))

        combined: list[tuple[str, int, int]] = []
        current_text = ""
        current_start = 0
        current_end = 0
        for text, start, end in passages:
            if not current_text:
                current_text = text
                current_start = start
                current_end = end
                continue
            candidate = f"{current_text}\n{text}".strip()
            if self._token_count(candidate) > self.max_tokens:
                combined.append((current_text, current_start, current_end))
                current_text = self._with_overlap(current_text, text)
                current_start = max(0, start - len(current_text))
                current_end = end
            else:
                current_text = candidate
                current_end = end
        if current_text:
            combined.append((current_text, current_start, current_end))

        return [
            Chunk(index=i, text=text, source_start=start, source_end=end)
            for i, (text, start, end) in enumerate(combined)
            if text.strip()
        ]

    def _blocks(self, markdown: str) -> list[tuple[str, int, int]]:
        headings: list[tuple[int, str]] = []
        blocks: list[tuple[str, int, int]] = []
        lines = markdown.splitlines(keepends=True)
        offset = 0
        current: list[str] = []
        block_start = 0

        def flush(end: int) -> None:
            nonlocal current
            body = _clean_markdown("".join(current))
            current = []
            if body:
                heading_path = " > ".join(title for _, title in headings)
                blocks.append(
                    (f"{heading_path}\n{body}" if heading_path else body, block_start, end)
                )

        for line in lines:
            heading = re.match(r"^\s{0,3}(#{1,6})\s+(.+?)\s*#*\s*$", line.rstrip("\r\n"))
            if heading:
                flush(offset)
                level = len(heading.group(1))
                headings = [(n, title) for n, title in headings if n < level]
                heading_title = _clean_markdown(heading.group(2))
                headings.append((level, heading_title))
            elif not line.strip():
                flush(offset)
            else:
                if not current:
                    block_start = offset
                current.append(line)
            offset += len(line)
        flush(offset)
        return blocks

    def _bounded_parts(
        self, text: str, source_start: int, source_end: int
    ) -> list[tuple[str, int, int]]:
        if self._token_count(text) <= self.max_tokens:
            return [(text, source_start, source_end)]

        parts: list[tuple[str, int, int]] = []
        start = 0
        while start < len(text):
            low, high = 1, len(text) - start
            best = 0
            while low <= high:
                middle = (low + high) // 2
                if self._token_count(text[start : start + middle]) <= self.max_tokens:
                    best = middle
                    low = middle + 1
                else:
                    high = middle - 1
            if best == 0:
                raise ValueError(
                    "Tokenizer could not fit a single character within the token limit"
                )

            cut = best
            if start + best < len(text):
                boundary = max(
                    text.rfind(mark, start, start + best)
                    for mark in (". ", ".\n", "! ", "? ", "。", "！", "？", "\n", " ")
                )
                if boundary > start + best // 2:
                    cut = boundary + 1 - start
            piece = text[start : start + cut].strip()
            relative_start = int((source_end - source_start) * start / max(1, len(text)))
            relative_end = int((source_end - source_start) * (start + cut) / max(1, len(text)))
            parts.append((piece, source_start + relative_start, source_start + relative_end))
            if start + cut >= len(text):
                break
            overlap = (
                self.tokenizer.decode(
                    self.tokenizer.encode(piece, add_special_tokens=False)[-self.overlap_tokens :],
                    skip_special_tokens=True,
                )
                if self.overlap_tokens
                else ""
            )
            start += max(1, cut - len(overlap))
        return parts

    def _with_overlap(self, previous: str, next_text: str) -> str:
        if self.overlap_tokens == 0:
            return next_text
        tail = self.tokenizer.decode(
            self.tokenizer.encode(previous, add_special_tokens=False)[-self.overlap_tokens :],
            skip_special_tokens=True,
        ).strip()
        candidate = f"{tail}\n{next_text}".strip()
        return candidate if self._token_count(candidate) <= self.max_tokens else next_text

    def _token_count(self, text: str) -> int:
        return len(self.tokenizer.encode(f"passage: {text}", add_special_tokens=True))
