package linkextractor.model

// ---------------------------------------------------------------------------
// Domain types for the link extractor pipeline.
//
// These are immutable case classes — pure data carriers with no behaviour.
// Scala case classes give us equals, hashCode, toString, copy, and pattern
// matching for free, which is why we prefer them over plain classes.
//
// See: docs/SPEC.md (Component Contracts) and model/SPEC.md
// ---------------------------------------------------------------------------

/**
 * Result of fetching a single URL.
 *
 * Produced by HttpFetcher, placed on the queue by Producer, consumed by Consumer.
 * We carry the original URL alongside the HTML because the consumer needs it as
 * the base URI for resolving relative links (e.g., "/about" → "https://example.com/about").
 *
 * Why a case class instead of a (String, String) tuple:
 *   Named fields are self-documenting. `result.url` is clearer than `result._1`.
 */
case class FetchResult(url: String, html: String)

/**
 * Extracted hyperlinks from a single page.
 *
 * Produced by HtmlParser, passed to the output function by Consumer.
 * All links in the list are absolute URLs — relative links are resolved
 * during parsing using the source URL as base.
 *
 * Why List[String] instead of Set[String]:
 *   Preserves document order. Duplicate links (e.g., nav appearing in header
 *   and footer) are kept — deduplication is a consumer concern, not a parser concern.
 */
case class ExtractedLinks(url: String, links: List[String])
