package linkextractor

// ---------------------------------------------------------------------------
// Unit tests for BoundedDroppingQueue.
//
// Verifies the drop-oldest behaviour and normal operation under capacity.
// No threading needed — these are synchronous put/take sequences.
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
