package linkextractor

import linkextractor.model.FetchResult
import java.util.concurrent.LinkedBlockingQueue

// ---------------------------------------------------------------------------
// Unit tests for Producer.
//
// Tests that the producer correctly populates the queue and handles errors.
// HttpFetcher is stubbed via method override — no network access needed.
//
// See: docs/TESTING.md and test/SPEC.md
// ---------------------------------------------------------------------------

class ProducerSuite extends munit.FunSuite:

  // --- Happy path: each URL produces a queue entry, followed by poison pill ---
  test("puts fetched results on queue and signals done with None") {
    val queue = LinkedBlockingQueue[Option[FetchResult]](10)

    // Stub fetcher: returns canned HTML containing the URL, no network needed
    val stubFetcher = new HttpFetcher():
      override def fetch(url: String): FetchResult =
        FetchResult(url, s"<html>$url</html>")

    val producer = Producer(List("http://a.com", "http://b.com"), queue, stubFetcher)
    producer.run()

    // Should have 2 results + 1 poison pill = 3 items total
    val item1 = queue.poll()
    val item2 = queue.poll()
    val item3 = queue.poll()

    assert(item1.isDefined, "First item should be Some(FetchResult)")
    assert(item2.isDefined, "Second item should be Some(FetchResult)")
    assertEquals(item3, None, "Third item should be None (poison pill)")
  }

  // --- Error isolation: all fetches fail, but producer still signals done ---
  // This is critical (R8): a bad URL must not crash the producer or leave
  // the consumer hanging forever waiting for None.
  test("signals done even when all fetches fail") {
    val queue = LinkedBlockingQueue[Option[FetchResult]](10)

    val failingFetcher = new HttpFetcher():
      override def fetch(url: String): FetchResult =
        throw RuntimeException(s"network error for $url")

    val producer = Producer(List("http://fail1.com", "http://fail2.com"), queue, failingFetcher)
    producer.run()

    // Only the poison pill should be on the queue — no results
    assertEquals(queue.poll(), None, "Should have poison pill even when all fetches fail")
  }

  // --- Edge case: empty URL list ---
  // Producer should immediately signal done without errors.
  test("signals done immediately for empty URL list") {
    val queue = LinkedBlockingQueue[Option[FetchResult]](10)

    val producer = Producer(List.empty, queue, HttpFetcher())
    producer.run()

    assertEquals(queue.poll(), None, "Empty URL list should produce only poison pill")
  }
