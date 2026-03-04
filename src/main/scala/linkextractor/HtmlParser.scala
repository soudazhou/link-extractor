package linkextractor

import linkextractor.model.ExtractedLinks
import org.jsoup.Jsoup
import scala.jdk.CollectionConverters.*

// ---------------------------------------------------------------------------
// HTML parser — extracts all hyperlinks from an HTML document using jsoup.
//
// Why jsoup instead of scala-scraper:
//   scala-scraper is a thin Scala wrapper around jsoup. Our use case is a
//   single select("a[href]") call — the wrapper adds a dependency for
//   syntactic sugar we don't need. Using jsoup directly also demonstrates
//   comfortable Java interop, which is valuable on any Scala team.
//   See: docs/DECISIONS.md (D2)
//
// Why this is a class (not an object):
//   Allows overriding extractLinks() in tests if needed (e.g., to simulate
//   a parse failure). In practice, jsoup's lenient parser almost never throws,
//   but the Consumer wraps calls in Try as a safety net.
//
// See: docs/SPEC.md (HtmlParser contract)
// ---------------------------------------------------------------------------

class HtmlParser:

  /**
   * Extracts all hyperlinks from an HTML document.
   *
   * @param url  The source URL, used as base URI for resolving relative links.
   *             For example, if url = "https://example.com" and the HTML contains
   *             <a href="/about">, the resolved link is "https://example.com/about".
   * @param html The raw HTML string to parse.
   * @return ExtractedLinks containing the source URL and all absolute link URLs.
   *
   * Implementation notes:
   *   - Jsoup.parse(html, baseUrl) sets the base URI for relative link resolution.
   *   - select("a[href]") finds all <a> elements that have an href attribute.
   *   - attr("abs:href") is jsoup's built-in feature that returns the absolute URL,
   *     resolving relative paths against the base URI automatically.
   *   - We filter out empty strings because some pages have <a href=""> or <a href="#">
   *     which resolve to empty after abs:href processing.
   *   - .asScala converts jsoup's Java Elements collection to a Scala iterable.
   *     This is standard Scala/Java interop via scala.jdk.CollectionConverters.
   */
  def extractLinks(url: String, html: String): ExtractedLinks =
    val doc = Jsoup.parse(html, url)
    val links = doc
      .select("a[href]")
      .asScala
      .map(_.attr("abs:href"))
      .filter(_.nonEmpty)
      .toList
    ExtractedLinks(url, links)
