# 5. Document search with Spring AI and pgvector

Date: 2026-11-02

## Status

Accepted

## Context

Document search answers natural-language questions from the user's own documents
using retrieval-augmented generation (RAG). Documents are split into chunks, each chunk
is embedded as a vector, the chunks closest to a question are retrieved, and a local
chat model answers using only those chunks. Everything runs locally: Ollama serves the
models, and Postgres with pgvector stores the vectors, so documents never leave the
deployment.

Several of the choices here are expensive to change later:

- The embedding model fixes the vector dimension, and so the column type. Vectors from
  two different models cannot be compared, so changing the model means re-embedding
  every document.
- Flyway owns the database schema, and Hibernate only validates it
  (`ddl-auto: validate`). Nothing else should create or alter tables at startup.
- Auth is not built yet, but documents belong to a user. If ownership is not part of
  the data model and the retrieval query from the start, one user's question could
  surface another user's documents, and fixing that later touches the schema and
  every query.

The backend runs Spring Boot 4.1. Spring AI 2.0 is the first line that supports
Spring Boot 4, and its documentation lists support for both 4.0.x and 4.1.x.

## Decision

- **Spring AI 2.0.x, pinned to an exact GA version** through its BOM (2.0.2 at the time
  of writing). Milestones and release candidates are not used. Spring AI provides the
  Ollama embedding and chat clients, the pgvector `VectorStore`, document splitting,
  and the retrieval advisor that adds chunks to the chat prompt.
- **Embedding model `nomic-embed-text:v1.5`, 768 dimensions**, pinned to an explicit
  tag rather than `latest`. Each stored chunk records the embedding model that produced
  it, so a model change can be detected and the affected chunks re-embedded. The chat
  model stays configurable and can change freely, because nothing is stored against it.
- **Flyway creates the vector table.** A migration creates the `vector` extension and
  the table and HNSW index in the layout Spring AI's `PgVectorStore` expects, with
  `vector(768)`. Spring AI's schema initialization stays off (`initialize-schema:
  false`, its default).
- **An application-owned `documents` table** records each source document: owner,
  name, content hash, and the embedding model. Chunks live in the vector table and
  carry the document id, owner, and embedding model in their metadata. Deleting a
  document deletes its chunks through a metadata filter.
- **Every document has an owner, and every retrieval filters by owner.** Until auth
  exists, the single local user's owner is a configured value. The auth work replaces
  it with the JWT subject without changing the schema or the queries.
- **HNSW index with cosine distance.** HNSW needs no training step and handles
  incremental inserts well, unlike IVFFlat, which must be built after data is loaded
  and degrades as the data drifts. The cost is slower index builds and more memory,
  which does not matter at this scale.
- **Fixed-size token chunking with Spring AI's `TokenTextSplitter` defaults.** Chunk
  size is a setting to tune against retrieval results, not a design decision.

### Alternatives considered

- **Own chunk table queried with `JdbcClient`**, using Spring AI only for the model
  clients. This would make owner and document id real columns, with a foreign key and
  an ordinary index. It was rejected for now because it means writing the batching,
  similarity query, and filter handling that `VectorStore` already provides, and the
  retrieval advisor builds on `VectorStore`. It is the fallback if metadata filtering
  becomes a bottleneck or the missing foreign key causes integrity problems.
- **A separate vector database.** Rejected: Postgres already stores the relational
  data, and keeping the vectors there means one database to run and back up.

## Consequences

- Documents and chunks have no foreign key between them, because the link is in chunk
  metadata. The document service keeps them consistent and deletes chunks with their
  document.
- Owner filtering uses a JSON path expression on the metadata, which the HNSW index
  does not cover. pgvector applies the filter after the index returns candidates, so a
  filtered query can return fewer than the requested number of chunks once many users
  share the table. With one user every chunk matches. If it becomes a problem, the
  options are pgvector's iterative index scans or the own-table alternative above.
- `nomic-embed-text` expects task prefixes (`search_document:` for stored text and
  `search_query:` for questions). Spring AI does not add them, so the ingestion and
  query code must.
- Embedding happens inside `VectorStore.add`, and on the CPU under Docker on macOS it
  is slow. Ingestion should not run inside a long database transaction, so a
  connection is not held while the model works.
- Changing the embedding model requires a new migration for the vector dimension and
  re-embedding every document. Recording the model on each chunk makes that detectable,
  not automatic.
- Spring AI 2.0 uses Jackson 3, as Spring Boot 4 does, so there is no second Jackson
  line on the classpath.
