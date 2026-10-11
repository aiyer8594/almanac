# 1. Virtual threads for request handling

Date: 2026-10-10

## Status

Accepted

## Context

The backend is a Spring MVC (servlet) application. Its request path is I/O-bound:
JDBC calls to Postgres today, and calls to a local Ollama model once document search
lands. With platform threads, Tomcat serves each request on a thread from a bounded
pool (200 by default). Every request blocked on I/O holds one of those threads, so
the pool size, not CPU, limits concurrency.

The usual way past that limit is a reactive stack (WebFlux, R2DBC). That means
rewriting the code in a non-blocking style, giving up JPA, and accepting harder
debugging, since a stack trace no longer follows a request's logical flow. Java 21
added virtual threads, and Spring Boot turns them on with a single property. Java 24
removed the main drawback (JEP 491): a virtual thread blocking inside `synchronized`
no longer pins its carrier thread.

## Decision

Enable virtual threads with `spring.threads.virtual.enabled: true` and keep the
blocking Spring MVC + JPA programming model. Tomcat runs each request on its own
virtual thread, and Spring's auto-configured task executor and scheduler use virtual
threads too.

## Consequences

- Request concurrency is no longer limited by the size of a thread pool. Code stays
  plain and blocking, and stack traces stay readable.
- The JDBC connection pool becomes the real limit on database concurrency. HikariCP
  defaults to 10 connections. Requests beyond that wait for a connection, so pool
  size and connection timeout need to be sized deliberately once there's load data,
  not left at the defaults.
- More concurrent requests can reach Postgres and Ollama than with a 200-thread
  pool. Downstream limits (connection pool, timeouts, and eventually rate limiting)
  are what protect those services, not thread scarcity.
- `ThreadLocal` still works, but there is one virtual thread per task. Don't use
  thread-locals to cache expensive objects.
- Pinning can still happen during native (JNI) calls. Nothing in the current
  dependency set is known to cause this, but it is worth checking with JFR
  (`jdk.VirtualThreadPinned`) if latency problems appear.
- CPU-bound work gains nothing from virtual threads. If embedding or chunking ends up
  running in-process, it should use a bounded executor.
