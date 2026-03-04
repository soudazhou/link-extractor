package linkextractor

// ---------------------------------------------------------------------------
// Unit tests for BoundedDroppingQueue.
//
// Verifies the drop-oldest behaviour, normal operation under capacity,
// and thread safety under concurrent access.
//
// See: docs/TESTING.md and test/SPEC.md
// ---------------------------------------------------------------------------

class BoundedDroppingQueueSuite extends munit.FunSuite:

  // --- Core behaviour: oldest entries are dropped when capacity is exceeded ---
  // With capacity 3: put(1), put(2), put(3) fills the queue.
  // put(4) should drop 1 (oldest) and add 4. Queue is now [2, 3, 4].
  test("drops oldest entry when capacity is exceeded") {
    val queue = BoundedDroppingQueue[Int](3)
    queue.put(1)
    queue.put(2)
    queue.put(3)
    queue.put(4)  // should drop 1 (oldest)

    assertEquals(queue.take(), 2)
    assertEquals(queue.take(), 3)
    assertEquals(queue.take(), 4)
  }

  // --- Normal operation: works like a regular queue when under capacity ---
  test("works normally when under capacity") {
    val queue = BoundedDroppingQueue[String](10)
    queue.put("a")
    queue.put("b")
    queue.put("c")

    assertEquals(queue.take(), "a")
    assertEquals(queue.take(), "b")
    assertEquals(queue.take(), "c")
    assertEquals(queue.size, 0)
  }

  // --- Multiple overflows: keeps dropping oldest to fit new entries ---
  test("handles multiple overflows correctly") {
    val queue = BoundedDroppingQueue[Int](2)
    queue.put(1)
    queue.put(2)
    queue.put(3)  // drops 1
    queue.put(4)  // drops 2

    assertEquals(queue.take(), 3)
    assertEquals(queue.take(), 4)
  }

  // --- Concurrent stress test: multiple threads putting simultaneously ---
  // The synchronized block in put() should prevent race conditions where two
  // threads both see the queue as full, both poll, and both offer — causing
  // unnecessary double-drops. This test hammers the queue from multiple
  // threads and verifies no exceptions are thrown and the final state is sane.
  test("handles concurrent puts from multiple threads without errors") {
    val queue = BoundedDroppingQueue[Int](10)
    val numThreads = 4
    val putsPerThread = 100

    // Launch multiple threads that all put concurrently
    val threads = (0 until numThreads).map: threadId =>
      val t = Thread: () =>
        (0 until putsPerThread).foreach: i =>
          queue.put(threadId * putsPerThread + i)
      t.start()
      t

    // Wait for all threads to finish
    threads.foreach(_.join(5_000))

    // Drain the queue — should have at most `capacity` items (10), since
    // the queue drops oldest on overflow. Total puts = 400, capacity = 10.
    var count = 0
    while !queue.isEmpty do
      queue.take()
      count += 1

    // We can't predict exact count due to timing, but it must be <= capacity
    assert(count <= 10, s"Queue should have at most 10 items, got $count")
    assert(count > 0, "Queue should have at least some items")
  }
