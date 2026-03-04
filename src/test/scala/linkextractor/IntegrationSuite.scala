package linkextractor

import linkextractor.model.{ExtractedLinks, FetchResult, ItemQueue}
import java.util.concurrent.LinkedBlockingQueue

// ---------------------------------------------------------------------------
// Integration test — end-to-end pipeline with stubbed HTTP.
//
// Verifies that the full data flow works: Producer fetches URLs (stubbed),
// places results on queue, Consumer reads and parses them, output is captured.
//
// This test runs producer and consumer on separate threads, just like Main.scala,
// to verify concurrent behaviour. The stub fetcher returns canned HTML with
// known links, so we can assert on exact output.
//
// See: docs/TESTING.md (Integration Tests)
// ---------------------------------------------------------------------------

class IntegrationSuite extends munit.FunSuite:

  test("full pipeline: producer -> queue -> consumer") {
    val queue = ItemQueue.fromBlockingQueue(
      LinkedBlockingQueue[Option[FetchResult]](10)
    )

    // Stub fetcher: each URL gets HTML with two predictable links
    val stubFetcher = new HttpFetcher():
      override def fetch(url: String): FetchResult =
        FetchResult(
          url,
          s"""<html>
             |<a href="$url/link1">Link 1</a>
             |<a href="$url/link2">Link 2</a>
             |</html>""".stripMargin
        )

    // Capture output instead of printing to stdout.
    // synchronized because the consumer's output function is called from the
    // consumer thread, and we read `results` from the test (main) thread.
    var results = List.empty[ExtractedLinks]
    val lock = new Object
    val captureOutput: ExtractedLinks => Unit = links =>
      lock.synchronized { results = results :+ links }

    val producer = Producer(
      List("http://site1.com", "http://site2.com"),
      queue,
      stubFetcher
    )
    val consumer = Consumer(queue, HtmlParser(), captureOutput)

    // Run concurrently — same pattern as Main.scala
    val ct = Thread(() => consumer.run(), "test-consumer")
    val pt = Thread(() => producer.run(), "test-producer")
    ct.start()
    pt.start()
    pt.join(10_000)  // 10s timeout — should be near-instant with stubs
    ct.join(10_000)

    // Verify: 2 URLs processed, each with 2 links = 4 total links
    assertEquals(results.size, 2, "Should process both URLs")

    val allLinks = results.flatMap(_.links)
    assertEquals(allLinks.size, 4, "Each URL should produce 2 links")

    // Verify the links are correctly formed
    assert(allLinks.exists(_.contains("site1.com/link1")))
    assert(allLinks.exists(_.contains("site2.com/link2")))
  }
