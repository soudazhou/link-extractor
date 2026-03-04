# Domain Types

## Overview

Immutable case classes that flow through the pipeline. Kept minimal — no behaviour,
just data carriers.

## Types

### FetchResult

```scala
case class FetchResult(url: String, html: String)
```

Produced by `HttpFetcher`, consumed by `HtmlParser`. Carries the original URL
(needed as base URI for resolving relative links) alongside the raw HTML.

### ExtractedLinks

```scala
case class ExtractedLinks(url: String, links: List[String])
```

Produced by `HtmlParser`, consumed by the output function. The `url` field
identifies which page the links came from. All links are absolute URLs.

## Why These Types

- **Explicit over Tuple**: `(String, String)` would work but is opaque. Named fields
  make the code self-documenting.
- **Immutable case classes**: Standard Scala practice. Provides `equals`, `hashCode`,
  `toString`, and pattern matching for free.
- **No Option fields**: Every field is always present. The queue uses `Option[FetchResult]`
  at the transport layer (for the poison pill), not in the domain types themselves.

### ItemQueue

```scala
trait ItemQueue[A]:
  def put(item: A): Unit
  def take(): A
```

Minimal abstraction over the queue so Producer and Consumer work with either
`LinkedBlockingQueue` (backpressure) or `BoundedDroppingQueue` (drop-oldest).
Companion object provides `fromBlockingQueue` and `fromDroppingQueue` factory methods.
