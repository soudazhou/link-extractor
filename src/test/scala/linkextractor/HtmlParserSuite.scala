package linkextractor

// ---------------------------------------------------------------------------
// Unit tests for HtmlParser.
//
// Tests the link extraction logic in isolation with known HTML strings.
// No network access, no stubs needed — HtmlParser is a pure function
// (HTML in → links out).
//
// See: docs/TESTING.md and test/SPEC.md
// ---------------------------------------------------------------------------

class HtmlParserSuite extends munit.FunSuite:
  val parser = HtmlParser()

  // --- Happy path: absolute links are extracted as-is ---
  test("extracts absolute links from HTML") {
    val html = """<html><body>
      <a href="https://example.com/page1">Link 1</a>
      <a href="https://example.com/page2">Link 2</a>
    </body></html>"""

    val result = parser.extractLinks("https://example.com", html)

    assertEquals(result.links.size, 2)
    assert(result.links.contains("https://example.com/page1"))
    assert(result.links.contains("https://example.com/page2"))
  }

  // --- Relative links should be resolved against the base URL ---
  // This is critical: many pages use relative paths like "/about" or "contact.html".
  // jsoup's abs:href resolves these using the base URL we pass to Jsoup.parse().
  test("resolves relative links against base URL") {
    val html = """<a href="/about">About</a><a href="contact.html">Contact</a>"""

    val result = parser.extractLinks("https://example.com", html)

    assertEquals(result.links.size, 2)
    assert(result.links.contains("https://example.com/about"))
    assert(result.links.contains("https://example.com/contact.html"))
  }

  // --- Pages with no links should return an empty list, not fail ---
  test("handles HTML with no links") {
    val html = "<html><body><p>No links here</p></body></html>"

    val result = parser.extractLinks("https://example.com", html)

    assertEquals(result.links, List.empty)
  }

  // --- jsoup is a lenient parser — malformed HTML should not crash ---
  // Real-world pages are often malformed. jsoup handles unclosed tags,
  // missing doctype, etc. gracefully. This test verifies that.
  test("handles malformed HTML gracefully") {
    val html = "<html><a href='https://example.com/link'>unclosed"

    val result = parser.extractLinks("https://example.com", html)

    assertEquals(result.links, List("https://example.com/link"))
  }

  // --- Empty href resolves to the base URL via abs:href ---
  // <a href=""> with base "https://example.com" resolves to "https://example.com"
  // because jsoup treats empty href as a self-reference (per HTML spec).
  // This is correct behaviour — we keep it because it IS a valid link.
  // Only truly empty strings (which don't occur with abs:href on a valid base) are filtered.
  test("resolves empty href to base URL") {
    val html = """
      <a href="">self-ref</a>
      <a href="https://real.com/page">real</a>
    """

    val result = parser.extractLinks("https://example.com", html)

    // Empty href resolves to base URL, so both links are present
    assert(result.links.contains("https://example.com"))
    assert(result.links.contains("https://real.com/page"))
    assertEquals(result.links.size, 2)
  }

  // --- The source URL should be preserved in the result ---
  // Consumer needs to know which page the links came from for output formatting.
  test("preserves source URL in result") {
    val html = "<html><a href='https://link.com'>L</a></html>"

    val result = parser.extractLinks("https://source.com", html)

    assertEquals(result.url, "https://source.com")
  }

  // --- javascript: and mailto: hrefs are technically valid href values ---
  // jsoup includes them via abs:href. We verify they come through as-is —
  // filtering them out would be a consumer/output concern, not a parser concern.
  // This test documents the behaviour rather than asserting a preference.
  test("includes javascript: and mailto: hrefs") {
    val html = """
      <a href="javascript:void(0)">JS</a>
      <a href="mailto:test@example.com">Email</a>
      <a href="https://real.com">Real</a>
    """

    val result = parser.extractLinks("https://example.com", html)

    // All three are present — jsoup treats them as valid href values
    assert(result.links.contains("https://real.com"))
    assert(result.links.exists(_.startsWith("javascript:")))
    assert(result.links.exists(_.startsWith("mailto:")))
  }
