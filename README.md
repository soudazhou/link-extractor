# Web Link Extractor

A concurrent producer/consumer system that fetches web pages and extracts all
hyperlinks. Built in Scala 3 as a take-home technical assessment.

---

## Submission Note

*By Wenxuan Zhou*

Fair warning: I have roughly 10 years of Java experience but this is my first
Scala project. If you ask me to explain the finer points of implicits or type-level
programming, I'll probably pivot the conversation to `BlockingQueue` internals
instead. That said, the design principles — concurrency, error isolation, clean
separation of concerns, testability — are the same ones I'd apply in Java. The
language is new; the engineering thinking is not.

I chose Scala intentionally for this task to demonstrate that picking up a new
JVM language is a matter of syntax, not fundamentals. You'll find the code is
heavily commented — partly to show my reasoning, partly because that's how I
learn: by writing down *why* something works the way it does.

The project was built using **spec-driven development with Claude Code**: all
specification documents were written first (see `docs/`), then implementation
followed to satisfy them. Think of it as TDD, but for architecture — the specs
are the "tests" that the code must pass.

### Git History

Each logical step is on its own feature branch merged to `main` via `--no-ff`:

1. `chore: initial project setup with sbt and dependencies`
2. `docs: add system specification, design decisions, and test strategy`
3. `feat: add core models, HTTP fetcher, and HTML parser with tests`
4. `feat: add producer and consumer with concurrent fetching and error isolation`
5. `feat: add main entry point and integration test`
6. `feat: add bounded dropping queue for oldest-entry trimming (bonus)`
7. `docs: add README with acceptance criteria, design decisions, and test evidence`

---

## Acceptance Criteria

Derived from the task requirements. All checked items are verified by tests and/or
live run evidence below.

### Core Requirements

- [x] Producer receives a list of URLs (file or CLI args)
- [x] Producer extracts markup from each URL and places it onto a queue
- [x] Consumer reads the queue until it is empty and the producer is done
- [x] Consumer parses HTML and extracts all hyperlinks into a list
- [x] Output is listed against each parsed URL
- [x] Producer and consumer run concurrently (separate threads)
- [x] Error isolation — one bad fetch or parse does not affect others
- [x] Unit tests included

### Bonus Requirements

- [x] URLs fetched concurrently (bounded thread pool, default 4)
- [x] Trimming oldest queue entries if queue size balloons (`--drop-oldest`)
- [x] Comprehensive test coverage (17 tests across 6 suites, including E2E with real HTTP)

---

## Quick Start

### Prerequisites

- JDK 17+ (project was built with JDK 21)
- sbt (install via [Coursier](https://get-coursier.io/docs/cli-installation): `brew install coursier/formulas/coursier && cs setup`)

### Build & Test

```bash
sbt compile    # compile
sbt test       # run all 15 tests
```

### Run

```bash
# From file (one URL per line, # comments and blank lines ignored)
sbt "run --file urls.txt"

# Positional URLs
sbt "run https://www.scala-lang.org https://news.ycombinator.com"

# With drop-oldest queue strategy (bonus)
sbt "run --drop-oldest --file urls.txt"
```

---

## Key Design Decisions

| Decision | Why | Alternative Considered | Source |
|----------|-----|------------------------|--------|
| JDK `HttpClient` over sttp | Zero deps for simple GET; leverages Java background | sttp — more Scala-idiomatic but adds dep for no gain | [HttpFetcher.scala](src/main/scala/linkextractor/HttpFetcher.scala) |
| jsoup directly over scala-scraper | Direct Java interop; no unnecessary wrapper | scala-scraper — thin Scala wrapper, no real benefit here | [HtmlParser.scala](src/main/scala/linkextractor/HtmlParser.scala) |
| `Option[FetchResult]` poison pill | Idiomatic Scala; no shared mutable state | `AtomicBoolean` flag — requires polling with timeout | [Producer.scala](src/main/scala/linkextractor/Producer.scala) |
| `CountDownLatch` for completion | Simple, familiar from Java concurrency | `Future.sequence` + `Await` — more complex error handling | [Producer.scala](src/main/scala/linkextractor/Producer.scala) |
| Bounded queue (cap=50) | Natural backpressure prevents OOM | Unbounded — simpler but risky at scale | [Main.scala](src/main/scala/linkextractor/Main.scala) |
| `BoundedDroppingQueue` (bonus) | Drops oldest instead of blocking producer | `CircularBuffer` — more complex, same effect | [BoundedDroppingQueue.scala](src/main/scala/linkextractor/BoundedDroppingQueue.scala) |
| `@tailrec` consumer loop | Compiler-verified stack safety | While loop — equivalent but less idiomatic Scala | [Consumer.scala](src/main/scala/linkextractor/Consumer.scala) |
| Injectable output fn | Testable without stdout capture | Hardcoded println — simpler but untestable | [Consumer.scala](src/main/scala/linkextractor/Consumer.scala) |
| `ItemQueue` trait | Lets Producer/Consumer work with either queue type | Direct `BlockingQueue` — locks out BoundedDroppingQueue | [ItemQueue.scala](src/main/scala/linkextractor/model/ItemQueue.scala) |
| MUnit over ScalaTest | Lighter, better diffs, Scala 3 native | ScalaTest — heavier, more boilerplate | [build.sbt](build.sbt) |

For extended rationale, see [docs/DECISIONS.md](docs/DECISIONS.md).

---

## Documentation Map

| Document | What It Covers |
|----------|---------------|
| [docs/SPEC.md](docs/SPEC.md) | Full system spec: requirements, architecture, component contracts, error handling |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Extended design decision log with alternatives and trade-offs |
| [docs/TESTING.md](docs/TESTING.md) | Test strategy, stubbing approach, how to reproduce |
| [src/main/scala/linkextractor/SPEC.md](src/main/scala/linkextractor/SPEC.md) | Source directory: file responsibilities, data flow, threading model |
| [src/main/scala/linkextractor/model/SPEC.md](src/main/scala/linkextractor/model/SPEC.md) | Domain types rationale |
| [src/test/scala/linkextractor/SPEC.md](src/test/scala/linkextractor/SPEC.md) | Test directory: what each suite covers |

---

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
                                          │ Consumer  │ ──▶ stdout
                                          │ @tailrec  │
                                          └──────────┘
```

- **Producer thread** owns a `FixedThreadPool(4)` for concurrent fetching
- **Consumer thread** blocks on `queue.take()` — no busy-waiting
- **Poison pill** (`None`) signals consumer to stop
- **Error isolation**: each fetch in its own `Future` with recovery; each parse in `Try`

---

## Test Evidence

### All 17 tests pass (clean build)

```
sbt clean test

linkextractor.BoundedDroppingQueueSuite:
  + drops oldest entry when capacity is exceeded
  + works normally when under capacity
  + handles multiple overflows correctly
linkextractor.ConsumerSuite:
  + processes all items and stops on None
  + continues processing after a parse error
linkextractor.HtmlParserSuite:
  + extracts absolute links from HTML
  + resolves relative links against base URL
  + handles HTML with no links
  + handles malformed HTML gracefully
  + resolves empty href to base URL
  + preserves source URL in result
linkextractor.IntegrationSuite:
  + full pipeline: producer -> queue -> consumer
linkextractor.ProducerSuite:
  + puts fetched results on queue and signals done with None
  + signals done even when all fetches fail
  + signals done immediately for empty URL list
linkextractor.EndToEndSuite:
  + fetches real URLs and extracts links end-to-end
  + error isolation with real HTTP — bad URL doesn't affect good ones

Passed: Total 17, Failed 0, Errors 0, Passed 17
```

### Test breakdown by suite

| Suite | Tests | Category | What It Proves |
|-------|-------|----------|----------------|
| HtmlParserSuite | 6 | Unit | Link extraction, relative URL resolution, malformed HTML |
| ProducerSuite | 3 | Unit | Queue population, error isolation, empty list |
| ConsumerSuite | 2 | Unit | Full consumption, error isolation on bad parse |
| BoundedDroppingQueueSuite | 3 | Unit | Drop-oldest overflow, under-capacity, multiple overflows |
| IntegrationSuite | 1 | Integration | Full pipeline with stubbed HTTP and concurrent threads |
| EndToEndSuite | 2 | E2E | Real HTTP requests to example.com, real error isolation |

### Live run — error isolation demo

```
sbt "run https://www.scala-lang.org https://httpstat.us/404"

Processing 2 URL(s)...

[Producer] Failed to fetch https://httpstat.us/404: null

URL: https://www.scala-lang.org
  - https://www.scala-lang.org/blog/2026/03/02/sbt2-compat.html
  - https://docs.scala-lang.org
  - https://www.scala-lang.org/download/
  - https://scastie.scala-lang.org
  - https://www.scala-lang.org/community/
  ... (90+ links extracted)

Done.
```

The 404 URL fails gracefully (logged to stderr). The good URL is fetched and
parsed normally — error isolation confirmed.

---

## Potential Enhancements

Documented but deliberately not implemented to stay within scope:

- **Rate limiting**: Token bucket or `Semaphore` in HttpFetcher to respect robots.txt
- **Retry with backoff**: Retry failed fetches 2-3x with exponential backoff before skipping
- **Output formats**: `--format json` or `--output results.txt`
- **Deduplication**: Skip duplicate URLs in input; deduplicate extracted links
- **Depth crawling**: Recursively fetch extracted links up to configurable depth
- **Multiple consumers**: N consumer threads taking from the same queue (needs N poison pills)

---

## Assumptions

- URLs in the input are well-formed HTTP/HTTPS URLs
- The system has network access to fetch the URLs
- HTML parsing is best-effort — malformed pages produce partial results via jsoup's lenient parser
- The `#` fragment in `<a href="#">` resolves to the base URL (jsoup's behaviour, kept as-is)
- Queue capacity of 50 is sufficient for typical use cases
- Default concurrency of 4 parallel fetches balances throughput and server load
