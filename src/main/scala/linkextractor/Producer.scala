package linkextractor

import linkextractor.model.{FetchResult, ItemQueue}
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

// ---------------------------------------------------------------------------
// Producer — fetches URLs concurrently and places results onto a shared queue.
//
// Responsibilities (from docs/SPEC.md):
//   - Receive a list of URLs (R1)
//   - Fetch HTML from each URL (R2)
//   - Place results on the queue (R3)
//   - Signal completion via poison pill (R4)
//   - Ensure error isolation — one failed fetch doesn't affect others (R8)
//   - Fetch URLs concurrently (B1)
//
// Threading model:
//   The Producer.run() method is designed to be called from a dedicated thread.
//   Internally, it creates a fixed-size thread pool for concurrent HTTP fetches.
//   Each URL is dispatched as a Future on this pool. As each Future completes
//   (success or failure), the result is put on the queue immediately — the
//   consumer can start processing before all URLs are fetched.
//
// Completion signaling (poison pill pattern):
//   When all Futures have completed, the producer puts None on the queue.
//   The consumer recognises None as the termination signal.
//   Why None instead of AtomicBoolean: see docs/DECISIONS.md (D3)
//
// Why CountDownLatch instead of Future.sequence + Await:
//   CountDownLatch is simpler. Each Future's onComplete decrements the latch,
//   regardless of success or failure. After latch.await(), we know it's safe
//   to send the poison pill. Future.sequence would fail the combined Future
//   if any individual fetch fails, requiring additional .recover handling.
//   See: docs/DECISIONS.md (D4)
//
// See: src/main/scala/linkextractor/SPEC.md (Data Flow, Threading Model)
// ---------------------------------------------------------------------------

class Producer(
    urls: List[String],
    queue: ItemQueue[Option[FetchResult]],
    fetcher: HttpFetcher,
    concurrency: Int = 4  // default 4 parallel fetches — conservative, avoids overwhelming servers
):

  /**
   * Fetches all URLs concurrently, places results on the queue, and signals
   * completion with None. This method blocks until all fetches are done.
   *
   * Error isolation: each failed fetch is logged to stderr and skipped.
   * The producer continues with remaining URLs and still sends the poison pill.
   */
  def run(): Unit =
    // Edge case: no URLs to fetch. Signal done immediately.
    if urls.isEmpty then
      queue.put(None)
      return

    // Create a bounded thread pool for concurrent fetching.
    // Why fixed pool: limits concurrent connections. If we used the global
    // ExecutionContext, all URLs would be dispatched at once, potentially
    // opening hundreds of connections and getting rate-limited or banned.
    val executor = Executors.newFixedThreadPool(concurrency)
    given ec: ExecutionContext = ExecutionContext.fromExecutor(executor)

    // One countdown per URL — latch reaches zero when all fetches complete.
    val latch = CountDownLatch(urls.size)

    urls.foreach: url =>
      Future(fetcher.fetch(url)).onComplete:
        case Success(result) =>
          // Put the result on the queue immediately. If the queue is full
          // (bounded capacity), this blocks until the consumer makes space.
          // That's the backpressure mechanism — see docs/DECISIONS.md (D5).
          queue.put(Some(result))
          latch.countDown()
        case Failure(e) =>
          // Error isolation: log and skip. Other URLs are unaffected.
          // We write to stderr so it doesn't interleave with stdout output.
          System.err.println(s"[Producer] Failed to fetch $url: ${e.getMessage}")
          latch.countDown()

    // Wait for all Futures to complete (success or failure), then send poison pill.
    // Timeout is generous — 5 minutes total for all URLs.
    latch.await(5, TimeUnit.MINUTES)
    queue.put(None)  // tells the consumer: "I'm done, no more items coming"

    // Clean shutdown of the thread pool. No new tasks will be submitted.
    executor.shutdown()
