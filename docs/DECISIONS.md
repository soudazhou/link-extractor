# Design Decisions

Extended rationale for each architectural choice. The README contains a summary table
linking here for details.

## D1: JDK HttpClient over sttp / Akka HTTP / http4s

**Decision**: Use `java.net.http.HttpClient` (built into JDK 11+) directly.

**Rationale**: The task only requires HTTP GET requests. The JDK client supports
timeouts, redirects, and async operation out of the box. Adding sttp or http4s would
introduce transitive dependencies for no functional gain. This also leverages familiar
Java API knowledge — the author has 10 years of Java experience.

**Alternative — sttp**: More Scala-idiomatic with backend abstraction. Rejected because
the abstraction adds complexity with zero benefit when we only need synchronous GET.

**Alternative — http4s**: Full functional HTTP stack built on Cats Effect. Rejected as
massive over-engineering for simple URL fetching.

## D2: jsoup Directly over scala-scraper

**Decision**: Use jsoup (Java library) directly via Scala/Java interop.

**Rationale**: scala-scraper is a thin Scala wrapper around jsoup. It adds `>>` and
`>?>` operators for CSS selectors, but our use case is a single `select("a[href]")` call.
The wrapper provides no material benefit here. Using jsoup directly demonstrates
comfortable Java interop — a practical skill for any Scala team working with JVM libraries.

**Alternative — scala-scraper**: Idiomatic Scala DSL. Rejected because it adds a
dependency for syntactic sugar we use once.

## D3: BlockingQueue with Option Poison Pill

**Decision**: `BlockingQueue[Option[FetchResult]]` where `None` signals producer completion.

**Rationale**: Idiomatic Scala use of `Option` as a type-safe sentinel. The consumer
pattern-matches on `Some(result)` vs `None`, which is clean and self-documenting.
No shared mutable flags needed.

**Alternative — AtomicBoolean flag**: Producer sets `done = true`, consumer polls with
timeout and checks flag. Rejected because it introduces polling latency and two
coordination mechanisms (queue + flag) instead of one.

**Alternative — null sentinel**: Works with Java queues but is not idiomatic Scala.
Rejected to avoid nullable values in Scala code.

## D4: CountDownLatch for Producer Completion

**Decision**: Use `java.util.concurrent.CountDownLatch` to wait for all Futures to
complete before sending the poison pill.

**Rationale**: Each Future's `onComplete` callback decrements the latch. After
`latch.await()`, we know all fetches (success or failure) have been processed, so it's
safe to send `None`. This is simpler than `Await.result(Future.sequence(...))` which
requires careful exception handling if any Future fails.

**Alternative — Future.sequence + Await**: Collects all Futures into one. If any fails,
the combined Future fails, requiring `.recover` at the sequence level. More complex,
harder to reason about. Rejected for simplicity.

## D5: Bounded Queue with Backpressure

**Decision**: `LinkedBlockingQueue(capacity = 50)` for the default queue.

**Rationale**: A bounded queue prevents unbounded memory growth if the producer is
faster than the consumer. When full, `queue.put()` blocks the producer — this is
natural backpressure. The capacity of 50 is generous enough to keep both sides busy
without holding excessive HTML in memory.

**Alternative — Unbounded queue**: Simpler but risks OOM if many large pages are
fetched faster than they can be parsed. Rejected for safety.

## D6: BoundedDroppingQueue (Bonus)

**Decision**: Provide an alternative queue that drops the oldest entry instead of
blocking when full.

**Rationale**: Addresses the bonus requirement "trimming oldest queue entries if queue
size balloons." In scenarios where the producer must never block (e.g., real-time
streaming), dropping stale data is preferable to backpressure.

**Trade-off**: Data loss vs. producer throughput. The default blocking queue preserves
all data; the dropping queue prioritises producer liveness.

**Alternative — CircularBuffer**: More complex data structure with the same effect.
Rejected because the LinkedBlockingQueue + poll-on-full approach is simpler and
well-understood.

## D7: @tailrec Consumer Loop

**Decision**: Implement the consumer as a `@tailrec` recursive function.

**Rationale**: The `@tailrec` annotation makes the Scala compiler verify at compile time
that the recursion is optimised to a loop. This guarantees no `StackOverflowError`
regardless of how many URLs are processed. It's also more idiomatic Scala than a
`while (true)` loop with a `break`.

**Alternative — while loop**: Functionally identical at runtime. Rejected because
`@tailrec` is a stronger correctness guarantee and more natural in Scala.

## D8: Injectable Output Function on Consumer

**Decision**: Consumer takes an `output: ExtractedLinks => Unit` parameter (default:
print to stdout).

**Rationale**: Makes the consumer testable in isolation. Tests pass a capturing function
instead of parsing stdout. This is dependency injection at its simplest — a function
parameter.

**Alternative — Hardcoded println**: Simpler but forces tests to capture stdout, which
is fragile and harder to assert on. Rejected for testability.

## D9: MUnit over ScalaTest

**Decision**: Use MUnit as the test framework.

**Rationale**: MUnit is lightweight, has excellent assertion diff output, and is designed
for Scala 3. Its `FunSuite` style is minimal — no DSL to learn, just `test("name") { ... }`.
For a project demonstrating Scala for the first time, a simple test framework reduces
cognitive load.

**Alternative — ScalaTest**: More popular and feature-rich (FlatSpec, WordSpec, etc.).
Rejected because the additional styles add complexity with no benefit for this project's
scope.

## D10: Scala 3.6.4

**Decision**: Use Scala 3.6.4 (latest stable 3.6.x at time of implementation).

**Rationale**: Well-supported, broadly compatible with JDK 8+, and has all the Scala 3
features we use (top-level definitions, `@main`, `given`/`using`, enum, etc.).

**Alternative — Scala 3.8.2 (latest)**: Available but requires JDK 17+ minimum and is
newer with less ecosystem testing. Rejected for stability — a senior engineer chooses
proven over bleeding-edge for a production-style submission.

**Alternative — Scala 2.13**: Still widely used but misses Scala 3 syntax improvements.
Rejected because the task is an opportunity to demonstrate modern Scala knowledge.
