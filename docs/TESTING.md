# Test Strategy

## Approach

All tests use MUnit (`FunSuite` style). Tests are designed to run without network
access or external dependencies — HTTP fetching is stubbed via method override.

## Test Categories

### Unit Tests

Test each component in isolation with known inputs.

| Suite | Component | Tests | What It Proves |
|-------|-----------|-------|----------------|
| `HtmlParserSuite` | HtmlParser | 7 | Link extraction, relative URLs, malformed HTML, javascript:/mailto: hrefs |
| `ProducerSuite` | Producer | 4 | Queue population, all-fail, empty list, partial failure (mixed batch) |
| `ConsumerSuite` | Consumer | 2 | Full queue consumption, error isolation on bad parse |
| `BoundedDroppingQueueSuite` | BoundedDroppingQueue | 4 | Oldest-drop, multiple overflows, normal operation, concurrent stress |

### Integration Tests

Test the full pipeline with stubbed HTTP.

| Suite | Scope | Tests | What It Proves |
|-------|-------|-------|----------------|
| `IntegrationSuite` | Producer → Queue → Consumer | 1 | End-to-end data flow, concurrent execution, correct output |

### End-to-End Tests

Test the full pipeline with **real HTTP requests** to verify the product works in a
realistic environment — real DNS, real connections, real HTML.

| Suite | Scope | Tests | What It Proves |
|-------|-------|-------|----------------|
| `EndToEndSuite` | Full system with real HTTP | 2 | Real URL fetching (example.com), error isolation with real failures |

## Stubbing Strategy

`HttpFetcher.fetch()` is an instance method that can be overridden in tests:

```scala
val stubFetcher = new HttpFetcher():
  override def fetch(url: String): FetchResult =
    FetchResult(url, "<html><a href='http://example.com'>link</a></html>")
```

This avoids mocking frameworks and keeps tests simple and readable.

Similarly, `Consumer` accepts an output function parameter, so tests capture results
into a list instead of printing to stdout.

## How to Run

```bash
# All tests
sbt test

# Single suite
sbt "testOnly linkextractor.HtmlParserSuite"
```

## Test Evidence

All 20 tests pass on clean build (`sbt clean test`):

```
linkextractor.BoundedDroppingQueueSuite:
  + drops oldest entry when capacity is exceeded
  + works normally when under capacity
  + handles multiple overflows correctly
  + handles concurrent puts from multiple threads without errors
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
  + includes javascript: and mailto: hrefs
linkextractor.IntegrationSuite:
  + full pipeline: producer -> queue -> consumer
linkextractor.ProducerSuite:
  + puts fetched results on queue and signals done with None
  + signals done even when all fetches fail
  + signals done immediately for empty URL list
  + queues successful results and skips failures in mixed batch
linkextractor.EndToEndSuite:
  + fetches real URLs and extracts links end-to-end
  + error isolation with real HTTP — bad URL doesn't affect good ones

Passed: Total 20, Failed 0, Errors 0, Passed 20
```
