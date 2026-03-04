# System Specification

## Overview

A producer/consumer web link extractor. The producer fetches HTML from a list of URLs
and places results onto a bounded queue. The consumer reads from the queue, parses
each page, and outputs the extracted hyperlinks.

## Requirements

Derived from the task description. Each maps to an acceptance criterion.

### Core Requirements

| ID | Requirement | Acceptance Criterion |
|----|-------------|---------------------|
| R1 | Producer receives a list of URLs (file, CLI, etc.) | URLs loaded from `--file` or positional CLI args |
| R2 | Producer extracts markup from each URL | HTML fetched via HTTP GET, placed on queue as `FetchResult` |
| R3 | Producer places output onto a queue | `BlockingQueue[Option[FetchResult]]` connects producer to consumer |
| R4 | Consumer reads queue until empty and producer done | Consumer stops on `None` poison pill after all items processed |
| R5 | Consumer parses HTML and extracts hyperlinks | jsoup extracts all `<a href="...">` as absolute URLs |
| R6 | Output listed against each parsed URL | Printed as `URL: <source>` followed by `  - <link>` per link |
| R7 | Producer and consumer run concurrently | Separate threads, started together, joined on completion |
| R8 | Error isolation — one bad fetch/parse doesn't affect others | Each fetch in its own Future with recovery; each parse in Try |
| R9 | Unit tests included | MUnit suites for parser, producer, consumer, integration |

### Bonus Requirements

| ID | Requirement | Acceptance Criterion |
|----|-------------|---------------------|
| B1 | URLs fetched concurrently | Fixed thread pool (default 4) via `Executors.newFixedThreadPool` |
| B2 | Trimming oldest queue entries if size balloons | `BoundedDroppingQueue` drops oldest on overflow instead of blocking |
| B3 | Comprehensive test coverage | 14+ tests across 5 suites |

## Architecture

```
CLI args / urls.txt
       │
       ▼
   ┌──────────┐    Option[FetchResult]    ┌──────────┐
   │ Producer  │ ──────────────────────▶   │  Queue   │
   │           │   Some(result) per URL    │ (bounded │
   │ Future per│   None = poison pill      │  50 cap) │
   │ URL fetch │                           └────┬─────┘
   └──────────┘                                 │
                                                ▼
                                          ┌──────────┐
                                          │ Consumer  │ ──▶ stdout / output fn
                                          │ @tailrec  │
                                          └──────────┘
```

### Threading Model

- **Main thread**: Parses CLI args, creates queue + components, starts producer & consumer
  threads, joins both, exits.
- **Producer thread**: Owns a fixed-size thread pool for concurrent fetching. Each URL
  dispatched as a `Future`. On completion (success or failure), result is put on the queue.
  After all URLs processed, puts `None` (poison pill). Shuts down the thread pool.
- **Consumer thread**: Blocks on `queue.take()`. Processes each `Some(FetchResult)` by
  parsing HTML and calling the output function. Stops on `None`.

### Data Flow

```
URL string ──▶ HttpFetcher.fetch(url) ──▶ FetchResult(url, html)
                                                  │
                                          queue.put(Some(...))
                                                  │
                                          queue.take() in Consumer
                                                  │
                                    HtmlParser.extractLinks(url, html)
                                                  │
                                          ExtractedLinks(url, links)
                                                  │
                                          output function (stdout)
```

## Component Contracts

### HttpFetcher

- **Input**: URL string
- **Output**: `FetchResult(url, html)` on success
- **Errors**: Throws `RuntimeException` on HTTP 4xx/5xx, `IOException` on network errors
- **Config**: Connect timeout (10s), request timeout (30s), follows redirects, User-Agent header
- **Thread safety**: Uses a shared `HttpClient` instance (thread-safe by JDK spec)

### HtmlParser

- **Input**: URL string (base URI) + HTML string
- **Output**: `ExtractedLinks(url, links)` where links are absolute URLs
- **Behaviour**: Uses jsoup's lenient parser. Resolves relative URLs via `abs:href`.
  Filters out empty hrefs. Never throws on malformed HTML (jsoup handles it).

### Producer

- **Input**: URL list, queue reference, fetcher, concurrency level
- **Output**: Populates queue with `Some(FetchResult)` per successful fetch, `None` at end
- **Error handling**: Failed fetches logged to stderr, counted down, skipped
- **Concurrency**: Bounded by fixed thread pool size (default 4)

### Consumer

- **Input**: Queue reference, parser, output function
- **Output**: Calls output function for each successfully parsed page
- **Error handling**: Failed parses logged to stderr, skipped
- **Termination**: Returns when `None` is taken from queue

### BoundedDroppingQueue (Bonus)

- **Input**: Capacity limit
- **Behaviour**: When full, `put()` drops the oldest entry instead of blocking
- **Thread safety**: `synchronized` on put to prevent race between poll and offer
- **Trade-off**: Producer never blocks, but oldest data may be lost

## Error Handling Strategy

| Error | Component | Handling | Isolation |
|-------|-----------|----------|-----------|
| DNS/timeout/connection failure | HttpFetcher → Producer | `Future.onComplete` catches, logs to stderr | Other URLs unaffected |
| HTTP 4xx/5xx | HttpFetcher → Producer | `RuntimeException` caught same way | Other URLs unaffected |
| Invalid URL syntax | `URI.create()` → Producer | `IllegalArgumentException` caught | Other URLs unaffected |
| Malformed HTML | HtmlParser | jsoup handles gracefully (lenient parser) | Partial links extracted |
| Parse exception | Consumer `Try` | Logged to stderr, URL skipped | Next URL processed normally |
| Input file not found | Main | `FileNotFoundException` propagates, clean exit | N/A — startup error |
| Empty URL list | Producer | Puts `None` immediately, consumer exits | Clean no-op |

## Input/Output Format

### Input

**CLI positional args:**

```bash
sbt "run https://example.com https://other.com"
```

**File input (`--file`):**

```bash
sbt "run --file urls.txt"
```

File format: one URL per line. Blank lines and lines starting with `#` are ignored.

### Output

```
Processing 3 URLs...

URL: https://example.com
  - https://example.com/about
  - https://example.com/contact

URL: https://other.com
  (no links found)

[Producer] Failed to fetch https://bad.com: HTTP 404 for https://bad.com

Done.
```

Errors are written to stderr. Results to stdout.
