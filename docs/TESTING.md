# Test Strategy

## Approach

All tests use MUnit (`FunSuite` style). Tests are designed to run without network
access or external dependencies — HTTP fetching is stubbed via method override.

## Test Categories

### Unit Tests

Test each component in isolation with known inputs.

| Suite | Component | Tests | What It Proves |
|-------|-----------|-------|----------------|
| `HtmlParserSuite` | HtmlParser | 6 | Correct link extraction, relative URL resolution, malformed HTML handling |
| `ProducerSuite` | Producer | 3 | Queue population, error isolation, empty list handling |
| `ConsumerSuite` | Consumer | 2 | Full queue consumption, error isolation on bad parse |
| `BoundedDroppingQueueSuite` | BoundedDroppingQueue | 2 | Oldest-drop behaviour, normal operation under capacity |

### Integration Tests

Test the full pipeline with stubbed HTTP.

| Suite | Scope | Tests | What It Proves |
|-------|-------|-------|----------------|
| `IntegrationSuite` | Producer → Queue → Consumer | 1 | End-to-end data flow, concurrent execution, correct output |

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

*To be filled after implementation. Will include `sbt test` output and sample run.*
