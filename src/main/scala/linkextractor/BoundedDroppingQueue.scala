package linkextractor

import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue}

// ---------------------------------------------------------------------------
// BoundedDroppingQueue — a bounded queue that drops the oldest entry when full.
//
// This addresses the bonus requirement: "Trimming oldest queue entries if queue
// size balloons." Instead of blocking the producer when the queue is full
// (like LinkedBlockingQueue.put()), this wrapper drops the oldest entry to
// make room for the new one.
//
// Trade-off (from docs/DECISIONS.md D6):
//   - LinkedBlockingQueue (default): Producer blocks when full. No data loss.
//     Consumer processes every fetched page. Good for correctness.
//   - BoundedDroppingQueue (this): Producer never blocks. Oldest entries dropped.
//     Good when producer throughput vastly exceeds consumer and freshness matters
//     more than completeness (e.g., real-time streaming scenarios).
//
// Thread safety:
//   The put() method is synchronized because it does a poll-then-offer sequence
//   that must be atomic. Without synchronization, two producers could both see
//   the queue as full, both poll, and then both offer — resulting in two
//   unnecessary drops. The synchronized block prevents this race.
//
//   take() delegates directly to the underlying LinkedBlockingQueue, which is
//   already thread-safe and handles its own blocking.
//
// Why not a CircularBuffer:
//   A circular buffer with overwrite-on-full would achieve the same effect but
//   requires more complex index management. Using LinkedBlockingQueue + poll-on-full
//   is simpler and reuses a well-tested JDK data structure.
//
// Integration:
//   Main.scala uses this queue when --drop-oldest is passed. Otherwise, the
//   default LinkedBlockingQueue is used for backpressure behaviour.
//
// Implements BlockingQueue[A] by delegation so it can be passed to Producer and
// Consumer without changing their signatures.
//
// See: docs/SPEC.md (BoundedDroppingQueue contract)
// ---------------------------------------------------------------------------

class BoundedDroppingQueue[A](capacity: Int):
  // The underlying thread-safe queue. Its capacity matches ours.
  private val underlying = LinkedBlockingQueue[A](capacity)

  /**
   * Puts an item on the queue. If the queue is full, drops the oldest entry
   * (head) to make room. The producer never blocks.
   *
   * Why poll() instead of remove():
   *   poll() returns null if the queue is unexpectedly empty (defensive).
   *   remove() would throw NoSuchElementException.
   */
  def put(item: A): Unit =
    synchronized:
      while !underlying.offer(item) do
        underlying.poll()  // drop oldest (head of queue)

  /**
   * Takes an item from the queue, blocking if empty.
   * Delegates directly to the underlying LinkedBlockingQueue.
   */
  def take(): A = underlying.take()

  /** Current number of items in the queue. */
  def size: Int = underlying.size

  /** Whether the queue is empty. */
  def isEmpty: Boolean = underlying.isEmpty
