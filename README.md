# Local RAG in Java

RAG over a 220-page PDF running entirely on CPU inside a VM. Built this to
find out where retrieval-augmented generation breaks on constrained hardware,
not to demo that it works.

**Stack:** LangChain4j 1.19.0, Ollama (qwen2.5:1.5b), all-MiniLM-L6-v2
quantized embeddings running in-process, in-memory vector store.

**Environment:** Xubuntu VM, 3.7 GB RAM, 2 cores (i5-5200U), no GPU.

## Setup

Drop any PDF into `src/main/resources/docs/` and update `pdfPath` in the
source. The book I used isn't included for copyright reasons.

Two runnable classes:
- `RagApp` — no system prompt
- `RagAppContext` — same pipeline, system prompt constraining answers to
  retrieved context

## What broke

**Timeout.** LangChain4j's HTTP client defaults to 60 seconds. On CPU,
generating an answer from retrieved chunks took longer than that, so the
client gave up mid-generation. Raised it to 10 minutes and cut `maxResults`
from 3 to 2 to shrink the prompt. See `results/01_timeout_failure.txt`.

**The model answered from training memory instead of my documents.** Asked
about intermittent fasting, which isn't in the book, it invented a book
title ("The Easy Route") and produced confident health claims that appear
nowhere in the source. It did retrieve real nutrition chunks, then blended
them with its own training data. That mix is the dangerous part — the output
looks sourced, and you can't tell which half is real without checking.

A typo made this clearer. I fat-fingered `/exiit`, which is meaningless, and
got back a detailed answer about a person named Sarah and a spiritual medium.
It retrieved a random chunk and built an answer around it rather than
admitting it had nothing.

## What fixed it

A system prompt restricting answers to retrieved context only.

| Question | Without | With |
|---|---|---|
| Capital of Australia | "Canberra" | "Canberra" (still fails) |
| Intermittent fasting | Invented book title + fabricated health claims | Only real nutrition content, no fabrication |
| Two elements of self-love | Correct, padded | Correct, concise |
| Should I quit a draining job | Generic 8-point listicle | The book's actual position |
| Meaningless input | Full invented answer (`/exiit`) | "Not found in the provided documents." (`/bye`) |

Three of four fixed. Australia still fails because "the capital of Australia
is Canberra" is common enough in training data that a 1.5B model won't
suppress it, even when instructed. That's a model capacity limit, not a
config problem — a 7B model would likely refuse correctly.

The meaningless-input row is the clearest evidence the instruction works.
Same class of garbage input, opposite behaviour.

## Numbers

- Ingestion: ~41s for 253,030 characters
- Chunking: 500 chars, 100 overlap
- Peak RAM during a query: 927 MB available of 3.7 GB
- Vector store is in-memory, so every restart re-embeds. Production would
  persist to pgvector or similar.

## Raw output

`results/` has all four transcripts including the unedited full session log.