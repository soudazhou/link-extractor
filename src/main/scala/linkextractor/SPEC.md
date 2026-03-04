# Source Directory: linkextractor

## Overview

This directory contains all production source code for the link extractor. Each file
has a single responsibility in the producer/consumer pipeline.

## Files and Responsibilities

| File | Responsibility | Spec Reference |
|------|---------------|----------------|
| `Main.scala` | Entry point. Parses CLI args, wires components, starts threads. | R1, R7 |
| `model/Types.scala` | Domain types: `FetchResult`, `ExtractedLinks`. | R2, R5 |
| `HttpFetcher.scala` | HTTP GET wrapper around JDK `HttpClient`. | R2, R8 |
| `HtmlParser.scala` | Extracts `<a href>` links from HTML using jsoup. | R5 |
| `Producer.scala` | Concurrent URL fetching, queue population, completion signal. | R2, R3, R7, R8, B1 |
| `Consumer.scala` | Queue reading, HTML parsing delegation, output. | R4, R5, R6, R8 |
| `BoundedDroppingQueue.scala` | Queue variant that drops oldest on overflow. | B2 |

## Data Flow

```
Main.scala
  │
  ├──▶ Producer (own thread)
  │      │
  │      ├──▶ HttpFetcher.fetch(url) [via Future pool]
  │      │      └──▶ FetchResult(url, html)
  │      │
  │      └──▶ queue.put(Some(result)) ... queue.put(None)
  │
  └──▶ Consumer (own thread)
         │
         ├──▶ queue.take()
         │      └──▶ Some(FetchResult) or None
         │
         ├──▶ HtmlParser.extractLinks(url, html)
         │      └──▶ ExtractedLinks(url, links)
         │
         └──▶ output(links) → stdout
```

## Threading Model

- `Main` runs on the main thread. It starts two child threads and joins both.
- `Producer` thread owns a `FixedThreadPool(4)` for parallel HTTP fetches.
  Each URL is dispatched as a `Future` on this pool. Results are put on the
  queue via `onComplete` callbacks (which run on the pool threads).
- `Consumer` thread blocks on `queue.take()` — no busy-waiting.
