package linkextractor

import linkextractor.model.FetchResult
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.{Duration as JDuration}

// ---------------------------------------------------------------------------
// HTTP fetcher — thin wrapper around JDK's built-in HttpClient (since JDK 11).
//
// Why JDK HttpClient instead of sttp or http4s:
//   We only need synchronous GET requests. The JDK client handles timeouts,
//   redirects, and connection pooling out of the box. Adding a third-party
//   HTTP library would bring transitive dependencies for zero functional gain.
//   See: docs/DECISIONS.md (D1)
//
// Why this is a class (not an object):
//   Instance-level config (timeouts) and the ability to override fetch() in
//   tests. Producer tests stub this to return canned HTML without network access.
//   See: docs/TESTING.md (Stubbing Strategy)
//
// Thread safety:
//   java.net.http.HttpClient is thread-safe by specification. A single instance
//   is shared across all concurrent fetches in the Producer's thread pool.
//
// See: docs/SPEC.md (HttpFetcher contract)
// ---------------------------------------------------------------------------

class HttpFetcher(
    connectTimeoutMs: Long = 10_000,  // 10 seconds — generous for DNS + TCP handshake
    requestTimeoutMs: Long = 30_000   // 30 seconds — allows for slow servers
):

  // Build the client once and reuse across all fetch() calls.
  // HttpClient.newBuilder() creates an immutable, thread-safe client.
  // Redirect.NORMAL follows 301/302/307/308 automatically — we want the final page.
  private val client: HttpClient = HttpClient.newBuilder()
    .connectTimeout(JDuration.ofMillis(connectTimeoutMs))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

  /**
   * Fetches the HTML content of a URL.
   *
   * @param url The URL to fetch (must be a valid HTTP/HTTPS URL)
   * @return FetchResult containing the URL and its HTML body
   * @throws RuntimeException if the server returns HTTP 4xx/5xx
   * @throws java.io.IOException on network errors (DNS, timeout, connection refused)
   * @throws IllegalArgumentException if the URL is syntactically invalid
   *
   * Why we throw on 4xx/5xx instead of returning an error type:
   *   The Producer wraps each call in a Future. Throwing lets the Future's
   *   failure path handle it uniformly — one error handling mechanism, not two.
   */
  def fetch(url: String): FetchResult =
    val request = HttpRequest.newBuilder()
      .uri(URI.create(url))  // throws IllegalArgumentException on bad URL syntax
      .timeout(JDuration.ofMillis(requestTimeoutMs))
      .header("User-Agent", "LinkExtractor/1.0")  // polite: identify ourselves
      .GET()
      .build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString())

    // Fail fast on error status codes. The Producer's error handling will
    // catch this, log it, and move on to the next URL.
    if response.statusCode() >= 400 then
      throw RuntimeException(s"HTTP ${response.statusCode()} for $url")

    FetchResult(url, response.body())
