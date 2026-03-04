# Test Directory: linkextractor

## Overview

All tests use MUnit `FunSuite`. No mocking frameworks — stubs are created via
method override or function injection.

## Suites

| Suite | What It Tests | Stubbing Approach |
|-------|--------------|-------------------|
| `HtmlParserSuite` | Link extraction from known HTML strings | None needed — pure function |
| `ProducerSuite` | Queue population, error handling, completion signal | `HttpFetcher.fetch()` overridden to return canned HTML or throw |
| `ConsumerSuite` | Queue consumption, error isolation | `HtmlParser.extractLinks()` overridden to throw on first call |
| `BoundedDroppingQueueSuite` | Drop-oldest behaviour, normal operation | None needed — tests the queue directly |
| `IntegrationSuite` | Full producer → queue → consumer pipeline | `HttpFetcher.fetch()` overridden with canned HTML |

## Design Principles

- **No network access**: All HTTP is stubbed. Tests run offline and fast.
- **No stdout capture**: Consumer output is captured via an injectable function.
- **Deterministic**: No timing dependencies. Queues are pre-populated for consumer
  tests. Producer tests use synchronous stubs.
- **Isolated**: Each test creates its own queue, producer, and consumer instances.
