package linkextractor

import linkextractor.model.{ExtractedLinks, FetchResult, ItemQueue}
import java.util.concurrent.LinkedBlockingQueue

// ---------------------------------------------------------------------------
// Unit tests for Consumer.
//
// Tests that the consumer reads from the queue correctly, handles errors,
// and stops on the poison pill. Output is captured via an injectable function
// instead of parsing stdout.
//
// See: docs/TESTING.md and test/SPEC.md
// ---------------------------------------------------------------------------

class ConsumerSuite extends munit.FunSuite:

  // Helper: create an ItemQueue pre-populated with items for consumer tests.
  // Returns the ItemQueue wrapper (for passing to Consumer).
  private def makeQueue(items: Option[FetchResult]*): ItemQueue[Option[FetchResult]] =
    val underlying = LinkedBlockingQueue[Option[FetchResult]](items.size + 1)
    items.foreach(underlying.put)
    ItemQueue.fromBlockingQueue(underlying)

  // --- Happy path: processes all items until poison pill ---
  test("processes all items and stops on None") {
    val queue = makeQueue(
      Some(FetchResult("http://a.com", "<a href='http://link1.com'>L</a>")),
      Some(FetchResult("http://b.com", "<a href='http://link2.com'>L</a>")),
      None  // poison pill
    )

    // Capture output instead of printing to stdout
    var results = List.empty[ExtractedLinks]
    val consumer = Consumer(queue, HtmlParser(), links => results = results :+ links)
    consumer.run()

    assertEquals(results.size, 2, "Should process both items before stopping")
    assertEquals(results.head.url, "http://a.com")
    assertEquals(results(1).url, "http://b.com")
    assert(results.head.links.contains("http://link1.com"))
    assert(results(1).links.contains("http://link2.com"))
  }

  // --- Error isolation: bad parse doesn't stop processing ---
  // The consumer wraps each parse in Try. If one page's HTML causes an error,
  // the consumer logs it and continues with the next page (R8).
  test("continues processing after a parse error") {
    val queue = makeQueue(
      Some(FetchResult("http://bad.com", "bad html")),
      Some(FetchResult("http://good.com", "<a href='http://ok.com'>OK</a>")),
      None
    )

    // Parser that throws on the first call, succeeds on subsequent calls
    var callCount = 0
    val failingParser = new HtmlParser():
      override def extractLinks(url: String, html: String): ExtractedLinks =
        callCount += 1
        if callCount == 1 then throw RuntimeException("simulated parse error")
        super.extractLinks(url, html)

    var results = List.empty[ExtractedLinks]
    val consumer = Consumer(queue, failingParser, links => results = results :+ links)
    consumer.run()

    // Only the second page should succeed
    assertEquals(results.size, 1, "Should have one successful result")
    assertEquals(results.head.url, "http://good.com")
    assert(results.head.links.contains("http://ok.com"))
  }
