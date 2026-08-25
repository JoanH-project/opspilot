# Engineering principles

- Do not preserve backward compatibility unless the current task explicitly requires it. Remove obsolete internal paths instead of adding unnecessary compatibility layers, fallbacks, or temporary migrations.
- Choose the simplest implementation that fully meets the requirements of the current phase. Avoid speculative abstractions, configuration, infrastructure, and indirection.
- Grow the system incrementally: build and verify the smallest end-to-end version first, then add capabilities without trading a working system for unfinished complexity.
- Keep module boundaries clear between domain, service/application, infrastructure, persistence, API, and UI concerns.
- Prefer established, actively maintained libraries when they materially reduce complexity or improve reliability; do not reimplement common functionality without a concrete reason.
- Before adding dependencies or custom infrastructure, inspect existing dependencies and capabilities. Do not assume they cannot solve the problem without checking their documentation, APIs, and types.
- Make decisions that can survive OpsPilot's intended evolution, but do not implement future architecture prematurely.
- Study established products and use proven conventions, adopting only what the current phase needs.

# Development workflow

- Understand related modules, tests, migrations, APIs, and frontend usage before making architectural changes.
- Work only on the requested phase or capability. Do not implement future RAG, agent, MCP, memory, workflow, messaging, caching, or infrastructure features unless explicitly requested.
- Preserve working end-to-end behavior while extending the system; prefer small, coherent changes over large rewrites.
- Do not introduce placeholders, fake implementations, dead code, speculative extension points, or temporary architecture expected to be replaced later.

# Completion standard

- Writing code alone does not complete a task.
- Before reporting completion, run relevant repository tests, type checks, builds, or other available verification.
- Add or update tests when behavior changes or a phase introduces domain/application behavior.
- If verification cannot be completed, explicitly state what could not be verified and why.
- At the end of every implementation task, report what changed, files added or modified, verification and results, and remaining limitations, risks, or intentionally deferred work.
