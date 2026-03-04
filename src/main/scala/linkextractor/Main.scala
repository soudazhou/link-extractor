package linkextractor

import linkextractor.model.{FetchResult, ItemQueue}
import java.util.concurrent.LinkedBlockingQueue
import scala.io.Source

// ---------------------------------------------------------------------------
// Main entry point — wires the producer and consumer together.
//
// Responsibilities (from docs/SPEC.md):
//   - Parse CLI arguments: --file <path> or positional URLs (R1)
//   - Create the bounded queue, fetcher, parser, producer, consumer
//   - Start producer and consumer on separate threads (R7)
//   - Wait for both to finish, then exit
//
// Threading:
//   We use plain Java Threads here, not Futures or ExecutionContexts. The
//   producer and consumer are long-running blocking operations (not short
//   async tasks), so dedicated threads are the right abstraction.
//
// Why consumer starts first:
//   The consumer blocks on queue.take() when the queue is empty, so starting
//   it before the producer is harmless. The alternative (starting producer
//   first) could fill the bounded queue before the consumer is ready, causing
//   unnecessary blocking on the producer side.
//
// Queue capacity:
//   50 is generous enough to keep both sides busy (producer can get ahead of
//   consumer) without holding excessive HTML in memory. Each FetchResult holds
//   the full HTML string, so 50 pages at ~100KB each ≈ 5MB — acceptable.
//   See: docs/DECISIONS.md (D5)
//
// Queue strategy (--drop-oldest):
//   Default: LinkedBlockingQueue — producer blocks when full (backpressure).
//   With --drop-oldest: BoundedDroppingQueue — drops oldest entry, producer
//   never blocks. See docs/DECISIONS.md (D5, D6).
//
// See: src/main/scala/linkextractor/SPEC.md (Data Flow, Threading Model)
// ---------------------------------------------------------------------------

@main def linkExtractor(args: String*): Unit =
  val argList = args.toList

  // --- Parse flags ---
  val dropOldest = argList.contains("--drop-oldest")
  val remainingArgs = argList.filterNot(_ == "--drop-oldest")

  // --- Parse CLI arguments ---
  // Two modes:
  //   1. --file <path>  : read URLs from a file (one per line, # comments, blank lines ignored)
  //   2. url1 url2 ...  : URLs as positional arguments
  val urls = if remainingArgs.nonEmpty && remainingArgs.head == "--file" && remainingArgs.length > 1 then
    val filePath = remainingArgs(1)
    val source = Source.fromFile(filePath)
    try
      source.getLines()
        .map(_.trim)
        .filter(_.nonEmpty)
        .filter(!_.startsWith("#"))  // skip comment lines
        .toList
    finally source.close()  // always close the file handle
  else if remainingArgs.nonEmpty then
    remainingArgs
  else
    System.err.println("Usage: link-extractor [--drop-oldest] [--file urls.txt | url1 url2 ...]")
    sys.exit(1)

  println(s"Processing ${urls.size} URL(s)...\n")

  // --- Wire components ---
  // Queue strategy depends on --drop-oldest flag.
  // Both are bounded to the same capacity; they differ in overflow behaviour.
  val queueCapacity = 50
  val queue: ItemQueue[Option[FetchResult]] =
    if dropOldest then
      println("[Using drop-oldest queue strategy]\n")
      ItemQueue.fromDroppingQueue(BoundedDroppingQueue[Option[FetchResult]](queueCapacity))
    else
      ItemQueue.fromBlockingQueue(LinkedBlockingQueue[Option[FetchResult]](queueCapacity))

  val fetcher = HttpFetcher()
  val parser = HtmlParser()
  val producer = Producer(urls, queue, fetcher, concurrency = 4)
  val consumer = Consumer(queue, parser)

  // --- Run concurrently ---
  // Thread names help with debugging if something hangs — they appear in
  // thread dumps (jstack) and logging frameworks.
  val consumerThread = Thread(() => consumer.run(), "consumer")
  val producerThread = Thread(() => producer.run(), "producer")

  consumerThread.start()  // start consumer first — it blocks on empty queue, ready to receive
  producerThread.start()

  // Wait for both threads to finish. Producer finishes when all URLs are
  // fetched (or failed). Consumer finishes when it receives the poison pill.
  producerThread.join()
  consumerThread.join()

  println("Done.")
