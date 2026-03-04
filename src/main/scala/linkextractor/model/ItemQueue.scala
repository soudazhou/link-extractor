package linkextractor.model

import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue}
import linkextractor.BoundedDroppingQueue

// ---------------------------------------------------------------------------
// ItemQueue — a minimal abstraction over the queue used by Producer and Consumer.
//
// Why this trait exists:
//   Producer and Consumer need to call put() and take() on a queue. The standard
//   LinkedBlockingQueue implements java.util.concurrent.BlockingQueue, but our
//   BoundedDroppingQueue (bonus feature) is a custom wrapper that doesn't.
//   This trait provides the minimal interface both queue types can satisfy,
//   so Producer and Consumer work with either without knowing which one they have.
//
// Why not make BoundedDroppingQueue extend BlockingQueue:
//   BlockingQueue has 15+ methods we'd need to implement (offer, poll, peek,
//   drainTo, remainingCapacity, etc.). Most would be meaningless delegations.
//   A minimal trait with just put() and take() is simpler and more honest.
//
// See: docs/DECISIONS.md (D5, D6)
// ---------------------------------------------------------------------------

trait ItemQueue[A]:
  def put(item: A): Unit
  def take(): A

object ItemQueue:

  /**
   * Wraps a standard JDK BlockingQueue as an ItemQueue.
   * Used with LinkedBlockingQueue for the default backpressure behaviour.
   */
  def fromBlockingQueue[A](bq: BlockingQueue[A]): ItemQueue[A] =
    new ItemQueue[A]:
      def put(item: A): Unit = bq.put(item)
      def take(): A = bq.take()

  /**
   * Wraps a BoundedDroppingQueue as an ItemQueue.
   * Used when --drop-oldest is passed to Main.
   */
  def fromDroppingQueue[A](dq: BoundedDroppingQueue[A]): ItemQueue[A] =
    new ItemQueue[A]:
      def put(item: A): Unit = dq.put(item)
      def take(): A = dq.take()
