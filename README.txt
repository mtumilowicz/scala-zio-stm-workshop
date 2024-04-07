
* [Software Transactional Memory](https://www.youtube.com/watch?v=bLfxaHIvHfc)
* [Haskell for Imperative Programmers #30 - Software Transactional Memory (STM)](https://www.youtube.com/watch?v=2lll2VbX8Vc)
* [CppCon 2015: Brett Hall “Transactional Memory in Practice"](https://www.youtube.com/watch?v=k20nWb9fHj0)
* [Transactional Memory for Concurrent Programming](https://www.youtube.com/watch?v=4caDLTfSa2Q)
* [Software Transactional Memory](https://www.youtube.com/watch?v=CMMH46R9VSY)
* [Introduction to Software Transactional Memory in Haskell](https://www.youtube.com/watch?v=F0tUPcffT6Y)
* https://www.oreilly.com/library/view/parallel-and-concurrent/9781449335939/

* never put any side effect in STM - it may be executed any arbitrary number of times
  * send email
* if a conflict arises on commit -> rerun
* composable memory transaction
* mutexes don't compose
* all of nothing semantics
* optimistic concurrency
  * no locks at all - you just execute code against possibly changing contents of the memory
  that other threads might be altering at the same time
  * every time you do a read from memory you log in a thread local transaction log
  * every time you do a write to memory you log in a thread local transaction log
  * at the end you've got saved up a log of all the reads and writes that have happened and now you need to do something that is truly atomic
    * programmer never sees this
    * system has to arrange atomically read from memory all the reads that you made and check that they're the same values
      * transaction t1 and t2 conflict when 
        * their write sets overlap
        * write set of t1 overlaps with read set of t2
        * write set of t2 overlaps with read set of t1
      * if they are => write back into memory all in one atomic step
      * if fails => commit fails and return transaction
        * nothing has happen in memory yet
* case study: producer-consumer stuff
  * one thread is producing, other thread is consuming
  * consumer needs to block if there isn't enough data
  * solution
    * if the queue youre getting something out of is empty then call retry
      * retry is the new primitive and it means abandon current transaction and re-execute
        * execution right away => buffer is likely to be still empty and then you retry again
          * processor would get very hot and not make much progress
        * u want system to block until one of the things that it read on the way to that retry has changed
          * transaction memory system is going to look at the transaction log see which locations you read and then block until one
          of those locations has been changed by another thread
          * no lost wakeup because system is dealing with wakeup
    * example: move message from one queue to another queue
      * condition: queue1.nonEmpty && queue.nonFull
    * example: move message from one queue an put in q2 if non empty or in q3 if non empty, if both empty - retry
      * composition
* problems with locking
  * taking too few locks
  * taking too many locks
  * taking the wrong lock
  * taking the right lock in the wrong order
  * error recovery - leaving the world in th right state
  * lost wake ups
  * composition/encapsulation dies
    * example with locking accounts (lower id first), account has lock
* two out of 4 ACID
  * atomic
  * isolated
    * no running transaction needs to be interested what happens in other transaction
  * consistent view
    * transactions happen succesfully if there aren't any conflicting changes
    * however, no data model like relations
* retry helps for conflicts (if there isnt a lot of contention)
* Software transactional memory (STM) is a technique for simplifying concurrent pro‐
  gramming by allowing multiple state-changing operations to be grouped together and
  performed as a single atomic operation.
* vs IO
    * A transaction can be rolled back only if we can track
      exactly what effects it has, and this would not be possible if arbitrary
      I/O were allowed inside a transaction—we might have performed some
      I/O that cannot be undone, like making a noise or launching some
      missiles.
* The meaning of retry is simply “abandon the current transaction and run it again.”
    * The STM implementation knows that there is no point
      rerunning the transaction unless something different is likely to happen, and
      that can be true only if one or more of the TVars that were read by the current
      transaction have changed.
* orElse :: STM a -> STM a -> STM a
  The operation orElse a b has the following behavior:
  • First, a is executed. If a returns a result, then the orElse call returns it and ends.
  • If a calls retry instead, a’s effects are discarded_ and b is executed instead.
  * The orElse operator lets us combine two blocking transactions such that one is per‐
    formed but not both.
* when multiple threads are
  blocked in STM transactions that depend on a particular TVar, and the TVar is modified
  by another thread, it is not enough to just wake up one of the blocked transactions—
  the runtime must wake them all
  * A transaction can block on an arbitrary condition, so the runtime doesn’t know whether
    any individual transaction will be able to make progress after the TVar is changed;
        do x <- takeTMVar m
        when (x /= 42) retry
    * it
      must run the transaction to find out
    * Hence, when there are multiple transactions that
      might be unblocked, we have to run them all; after all, they might all be able to continue
      now.
    * Because the runtime has to run all the blocked transactions, there is no guarantee
      that threads will be unblocked in FIFO order and no guarantee of fairness.
* By storing writeTVar operations in the log rather than applying them to main
  memory immediately, discarding the effects of a transaction is easy; we just throw
  away the log.
    * Hence, aborting a transaction has a fixed small cost.
* Each readTVar must traverse the log to check whether the TVar was written by an
  earlier writeTVar. Hence, readTVar is an O(n) operation in the length of the log.
* Because the log contains a record of all the readTVar operations, it can be used to
  discover the full set of TVars read during the transaction, which we need to know
  in order to implement retry.
* When a transaction reaches the end, the STM implementation compares the log against
  the contents of memory.
    * If the current contents of memory match the values read by
      readTVar, the effects of the transaction are committed to memory, and if not, the log is
      discarded and the transaction runs again from the beginning.
    * This process takes place
      atomically by locking all the TVars involved in the transaction for the duration.
      * transactions operating on disjoint sets of
        TVars can proceed without interference.
* There are two important rules of thumb:
  • Never read an unbounded number of TVars in a single transaction because the O(n)
  performance of readTVar then gives O(n
  2
  ) for the whole transaction.

  • Try to avoid expensive evaluation inside a transaction because this will cause the
  transaction to take a long time, increasing the chance that another transaction will

  modify one or more of the same TVars, causing the current transaction to be re-
  executed. In the worst case, a long-running transaction re-executes indefinitely

  because it is repeatedly aborted by shorter transactions.
* each TVar has a watch list of threads that should be woken up if the
  TVar is modified, and retry adds the current thread to the watch list of all the TVars
  read during the current transaction.
* STM provides several benefits for concurrent programming:
  Composable atomicity
  You can construct arbitrarily large atomic operations on shared state, which can
  simplify the implementation of concurrent data structures with fine-grained lock‐
  ing.
  Composable blocking
  You can build operations that choose between multiple blocking operations, which
  is very difficult with MVars and other low-level concurrency abstractions.
  Robustness in the presence of failure and cancellation
  A transaction in progress is aborted if an exception occurs, so STM makes it easy
  to maintain invariants on state in the presence of exceptions.