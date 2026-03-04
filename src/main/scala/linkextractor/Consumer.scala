package linkextractor

import linkextractor.model.{ExtractedLinks, FetchResult}
import java.util.concurrent.BlockingQueue
import scala.annotation.tailrec
import scala.util.{Try, Success, Failure}

// ---------------------------------------------------------------------------
// Consumer — reads fetched HTML from the queue, extracts links, and outputs.
//
// Responsibilities (from docs/SPEC.md):
//   - Read the queue until empty and producer is done (R4)
//   - Parse HTML and extract hyperlinks (R5)
//   - Output links listed against each parsed URL (R6)
//   - Error isolation — one bad parse doesn't affect others (R8)
//
// Why @tailrec:
//   The consumer loop is implemented as tail recursion. The @tailrec annotation
//   makes the Scala compiler verify at compile time that this compiles to a
//   simple loop (no stack frames accumulated). This guarantees no StackOverflow
//   regardless of how many thousands of URLs are processed.
//   See: docs/DECISIONS.md (D7)
//
// Why injectable output function:
//   The output parameter defaults to printing to stdout, but tests can pass
//   a capturing function (e.g., appending to a mutable list) to verify results
//   without parsing stdout. This is the simplest form of dependency injection.
//   See: docs/DECISIONS.md (D8)
//
// Blocking behaviour:
//   queue.take() blocks when the queue is empty, waiting for the producer to
//   put more items. No busy-waiting, no polling interval — the thread sleeps
//   until data is available. This is efficient and correct.
//
// See: src/main/scala/linkextractor/SPEC.md (Data Flow)
// ---------------------------------------------------------------------------

class Consumer(
    queue: BlockingQueue[Option[FetchResult]],
    parser: HtmlParser,
    output: ExtractedLinks => Unit = Consumer.printToStdout
):

  /**
   * Reads from the queue until the poison pill (None) is received.
   * Each HTML document is parsed independently — one bad parse
   * does not affect others. This method blocks until termination.
   */
  def run(): Unit =
    // Inner tail-recursive function. The @tailrec annotation is a compile-time
    // check that Scala optimises this to a loop. If we accidentally broke the
    // tail position (e.g., added code after the recursive call), the compiler
    // would reject it.
    @tailrec
    def consume(): Unit =
      queue.take() match
        case Some(FetchResult(url, html)) =>
          // Wrap parsing in Try for error isolation. If jsoup throws (rare but
          // possible with extremely malformed input), we log and skip this URL.
          // The consumer continues with the next item in the queue.
          Try(parser.extractLinks(url, html)) match
            case Success(links) => output(links)
            case Failure(e) =>
              System.err.println(s"[Consumer] Failed to parse $url: ${e.getMessage}")
          consume()  // tail position — compiler verifies this
        case None => ()  // poison pill received: producer is done, queue is drained

    consume()

// Companion object with the default output function.
// Separated from the class so it can be referenced as a default parameter.
object Consumer:

  /**
   * Default output: prints links to stdout grouped by source URL.
   *
   * Format (from docs/SPEC.md — Input/Output Format):
   *   URL: https://example.com
   *     - https://example.com/about
   *     - https://example.com/contact
   */
  def printToStdout(links: ExtractedLinks): Unit =
    println(s"URL: ${links.url}")
    if links.links.isEmpty then
      println("  (no links found)")
    else
      links.links.foreach(link => println(s"  - $link"))
    println()  // blank line between URLs for readability
