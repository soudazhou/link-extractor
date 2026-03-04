package linkextractor

import linkextractor.model.{ExtractedLinks, FetchResult, ItemQueue}
import java.util.concurrent.LinkedBlockingQueue

// ---------------------------------------------------------------------------
// End-to-end test — runs the full pipeline with REAL HTTP requests.
//
// Unlike IntegrationSuite (which stubs HTTP), this test hits actual public URLs
// to prove the system works in a realistic environment: real DNS resolution,
// real HTTP, real HTML parsing, real concurrent threads.
//
// Tagged with `munit.Slow` so it can be excluded from fast CI runs if needed:
//   sbt "testOnly -- --exclude-tags=Slow"
//
// Why this matters:
//   Unit tests prove each component works in isolation. The stubbed integration
//   test proves they wire together. But neither proves we can actually fetch a
//   real web page and extract real links — that's what this test does.
//
// See: docs/TESTING.md
// ---------------------------------------------------------------------------

class EndToEndSuite extends munit.FunSuite:

  // Use a generous timeout — real HTTP can be slow
  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  // --- Real HTTP: fetch a known stable page, extract links, verify output ---
  // We use https://example.com because it is:
  //   - Maintained by IANA as a stable documentation domain
  //   - Always returns a simple HTML page with at least one link
  //   - Fast and reliable (no rate limiting, no CAPTCHA)
  test("fetches real URLs and extracts links end-to-end".tag(munit.Slow)) {
    val queue = ItemQueue.fromBlockingQueue(
      LinkedBlockingQueue[Option[FetchResult]](10)
    )

    // Real fetcher — no stubs. This is the real deal.
    val fetcher = HttpFetcher()

    var results = List.empty[ExtractedLinks]
    val lock = new Object
    val captureOutput: ExtractedLinks => Unit = links =>
      lock.synchronized { results = results :+ links }

    val producer = Producer(
      List("https://example.com"),
      queue,
      fetcher
    )
    val consumer = Consumer(queue, HtmlParser(), captureOutput)

    val ct = Thread(() => consumer.run(), "e2e-consumer")
    val pt = Thread(() => producer.run(), "e2e-producer")
    ct.start()
    pt.start()
    pt.join(20_000)
    ct.join(20_000)

    // example.com always has at least one link (to IANA)
    assertEquals(results.size, 1, "Should process exactly one URL")
    assertEquals(results.head.url, "https://example.com")
    assert(results.head.links.nonEmpty, "example.com should have at least one link")
  }

  // --- Error isolation with real HTTP: bad URL doesn't crash good ones ---
  // Mix a real URL with a URL that will definitely fail (nonexistent domain).
  // The good URL should still be processed successfully.
  test("error isolation with real HTTP — bad URL doesn't affect good ones".tag(munit.Slow)) {
    val queue = ItemQueue.fromBlockingQueue(
      LinkedBlockingQueue[Option[FetchResult]](10)
    )

    val fetcher = HttpFetcher()

    var results = List.empty[ExtractedLinks]
    val lock = new Object
    val captureOutput: ExtractedLinks => Unit = links =>
      lock.synchronized { results = results :+ links }

    val producer = Producer(
      // First URL will fail (nonexistent domain), second should succeed
      List("https://this-domain-does-not-exist-12345.com", "https://example.com"),
      queue,
      fetcher
    )
    val consumer = Consumer(queue, HtmlParser(), captureOutput)

    val ct = Thread(() => consumer.run(), "e2e-consumer")
    val pt = Thread(() => producer.run(), "e2e-producer")
    ct.start()
    pt.start()
    pt.join(20_000)
    ct.join(20_000)

    // Only example.com should succeed — the bad domain is skipped
    assertEquals(results.size, 1, "Only the good URL should produce results")
    assertEquals(results.head.url, "https://example.com")
    assert(results.head.links.nonEmpty, "example.com should have links")
  }
