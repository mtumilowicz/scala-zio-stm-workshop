
* [Software Transactional Memory](https://www.youtube.com/watch?v=bLfxaHIvHfc)
* [Haskell for Imperative Programmers #30 - Software Transactional Memory (STM)](https://www.youtube.com/watch?v=2lll2VbX8Vc)
* [CppCon 2015: Brett Hall “Transactional Memory in Practice"](https://www.youtube.com/watch?v=k20nWb9fHj0)
* [Transactional Memory for Concurrent Programming](https://www.youtube.com/watch?v=4caDLTfSa2Q)
* [Software Transactional Memory](https://www.youtube.com/watch?v=CMMH46R9VSY)

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
* two out of 4 ACID
  * atomic
  * isolated