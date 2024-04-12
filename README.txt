# scala-zio-stm-workshop

* references
    * [Software Transactional Memory](https://www.youtube.com/watch?v=bLfxaHIvHfc)
    * [Haskell for Imperative Programmers #30 - Software Transactional Memory (STM)](https://www.youtube.com/watch?v=2lll2VbX8Vc)
    * [CppCon 2015: Brett Hall “Transactional Memory in Practice"](https://www.youtube.com/watch?v=k20nWb9fHj0)
    * [Transactional Memory for Concurrent Programming](https://www.youtube.com/watch?v=4caDLTfSa2Q)
    * [Software Transactional Memory](https://www.youtube.com/watch?v=CMMH46R9VSY)
    * [Introduction to Software Transactional Memory in Haskell](https://www.youtube.com/watch?v=F0tUPcffT6Y)
    * https://www.oreilly.com/library/view/parallel-and-concurrent/9781449335939/
    * https://web.mit.edu/ha22286/www/papers/MEng20_2.pdf
    * https://docs.rs/stm-core/latest/stm_core/
    * https://clojureverse.org/t/any-examples-of-clojures-stm-being-used-in-the-wild/9665
    * https://dl.acm.org/doi/pdf/10.1145/3386321
    * https://chat.openai.com/
    * https://www.linkedin.com/advice/0/how-do-you-optimize-performance-scalability-software
    * https://www.diva-portal.org/smash/get/diva2:828358/FULLTEXT01.pdf
    * https://gcc.gnu.org/wiki/TransactionalMemory
    * https://www.schoolofhaskell.com/school/advanced-haskell/beautiful-concurrency/3-software-transactional-memory
    * https://www.baeldung.com/java-multiverse-stm
    * https://objectcomputing.com/resources/publications/sett/september-2009-software-transactional-memory
    * https://link.springer.com/referenceworkentry/10.1007/978-0-387-39940-9_348
    * https://wiki.haskell.org/Software_transactional_memory
    * https://www.cs.carleton.edu/faculty/dmusican/cs348/stm.html
    * https://zio.dev/reference/stm/

# preface
* goals of this workshop
* workshop plan

# prerequisite
* problems with locking
    * taking too few locks
    * taking too many locks
    * taking the wrong lock
    * taking the right lock in the wrong order
    * error recovery
        * leaving the world in th right state
    * lost wake-ups
    * composition/encapsulation dies

# software transaction memory
* concept ported from the SQL database world
    * SQL transactions satisfy ACID (Atomicity, Consistency, Isolation, Durability) properties
    * STM: only Atomicity, Consistency and Isolation are satisfied because the mechanism runs in-memory
        * Atomicity
            * effects become visible to another thread all at once
                * example: ensures that no other thread can see a state in which money has been deposited in to but not yet withdrawn from
            * means that either all the changes in a transaction will be made successfully (commit) or none of them will be (rollback)
        * Isolation
            * transactions are isolated from each other such that each transaction sees a consistent view of memory
            * it is as if action takes a snapshot of the state of the world when it begins running, and then executes against that snapshot
        * Consistency
            * transactions happen successfully if there aren't any conflicting changes
* is a method of concurrency control in which shared-memory accesses are grouped into transactions which either succeed or fail to commit in their entirety
    * all or nothing semantics
    * within atomic blocks, you can reason about your code as if the program were sequential
* benefits
    * composability
        * easily composed with any other abstraction built using STM
        * mutexes don't compose
    * modularity
        * not expose the details of how your abstraction ensures safety
        * not the case with other forms of concurrent communication, such as locks or MVars
* good practices
    * never put any side effect in STM - it may be executed any arbitrary number of times
        * if a conflict arises on commit -> rerun
        * example: send email, fire missile


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

* two out of 4 ACID
  * consistent view
    * Consistency ensures that transactions always see a consistent view of the shared memory.
    * transactions happen succesfully if there aren't any conflicting changes
    * however, no data model like relations
* retry helps for conflicts (if there isnt a lot of contention)
* Software transactional memory (STM) is a technique for simplifying concurrent pro‐
  gramming by allowing multiple state-changing operations to be grouped together and
  performed as a single atomic operation.
* vs IO
    * An STM action is like an IO action, in that it can have side effects, but the range of side effects for STM actions is much smaller. The main thing you can do in an STM action is read or write a transactional variable, of type (TVar a), much as we could read or write IORefs in an IO action8.
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
* This optimism can enable non-conflicting transactions to execute and commit concurrently, increasing throughput. Such designs are motivated by
  Lamport’s argument that “contention for [shared objects] is rare in a well-designed
  system” [31], and thus such systems perform well when contention is low
* With locks the sequential composition of two two threadsafe actions is no longer threadsafe because other threads may interfer in between of these actions. Applying a third lock to protect both may lead to common sources of errors like deadlocks or race conditions.
* The text book use case for STM is keeping consistency when you want to coordinate changes between multiple refs in a concurrent system. I ask myself when would this be the case in a real life project. What kind of implementation would benefit from it? It seems that it only makes sense if you have state divided into multiple refs as opposed to concentrate state into few independent atoms.
    * It is an awesome concept, and barely gets used outside small examples, except I think in Haskell. I think it’s difficult to ensure good performance without a lazy language, and difficult to reason about without enforced immutability and monads.
* Furthermore, databases are already really good at doing what refs do, so assuming the data you’re mutating is being persisted, why bring that into memory just to do what the database is built to do for you?
* In reading the STM literature one problem I saw often was read-tracking, which I definitely wanted to avoid for performance reasons. In the database world, one technique for avoiding read tracking and read locks is multiversion concurrency control (MVCC)
    * It seemed to me that MVCC was a great fit for persistent data structures which supported
    efficient update with structural sharing, making versioning cheap.
    * So I designed and built an STM around MVCC. (Clojure)
        * The Clojure STM implementation is based on multi-version concurrency control (MVCC) [16] and snapshot isolation [17].
    * In basic MVCC only writes are tracked, and all reads are done with a consistent basis (as of the transaction start, and incorporating within-transaction writes)
    * However MVCC can be subject to write-skew, where the basis of a write to X is a read of Y, and Y is not otherwise written, and thus not ensured consistent at transaction end.
       * Clojure’s STM offers an ensure operation for opt-in tracking of such reads when needed.
    * In MVCC, each transaction operates on a snapshot of the database at a specific point in time, allowing readers to access consistent data without being blocked by writers.
* I did not need it, that I was better off organizing all my state into a single atom.
    * Designing with atoms results in more robust systems because of design and state is held in one place. However, there may be cases where it’s necessary to use refs but by then you’ll be dealing with other situations such as persistence because if you’re worried about distributed state, you’re probably going to be worried about what happens when the electricity goes out.
* To avoid unnecessary conflicts and aborts, you should design transactions to be short, small, and independent.
    * Finally, employ commutative and idempotent operations when you can as these do not cause any conflicts and can be safely executed in any order or multiple times.
* The
  traditional lock-based parallel programming techniques are error prone
  and suffer from various problems such as deadlocks, live-locks,
  priority inversion etc
  * Priority Inversion: Priority inversion takes place when a lower priority process is
    holding a resource which is required by a higher priority process, which makes the higher priority
    process wait until resource is released.
* One particularly attractive implementation is well established in the database world, namely optimistic execution. When (atomically act) is performed, a thread-local transaction log is allocated, initially empty. Then the action act is performed, without taking any locks at all. While performing act, each call to writeTVar writes the address of the TVar and its new value into the log; it does not write to the TVar itself. Each call to readTVar first searches the log (in case the TVar was written by an earlier call to writeTVar); if no such record is found, the value is read from the TVar itself, and the TVar and value read are recorded in the log. In the meantime, other threads might be running their own atomic blocks, reading and writing TVars like crazy.

  When the action act is finished, the implementation first validates the log and, if validation is successful, commits the log. The validation step examines each readTVar recorded in the log, and checks that the value in the log matches the value currently in the real TVar. If so, validation succeeds, and the commit step takes all the writes recorded in the log and writes them into the real TVars.
* What if validation fails? Then the transaction has had an inconsistent view of memory.
    * However, notice that it is crucial that act contains no effects other than reads and writes on TVars
        ```
        atomically (do x <- readTVar xv
                       y <- readTVar yv
                       if x>y then launchMissiles
                              else return () )
        ```
        * where launchMissiles :: IO () causes serious international side-effects
        * Fortunately, the type system prevents us running IO actions inside STM actions, so the above fragment would be rejected by the type checker
* The semantics of retry are simple: if a retry action is performed, the current transaction is abandoned and retried at some later time. It would be correct to retry the transaction immediately, but it would also be inefficient: the state of the account will probably be unchanged, so the transaction will again hit the retry. An efficient implementation would instead block the thread until some other thread writes to acc.
* We now turn our attention to choice. Suppose you want to withdraw money from account A if it has enough money, but if not then withdraw it from account B?
    * For that, we need the ability to choose an alternative action if the first one retries. To support choice, STM Haskell has one further primitive action, called orElse
* STM is harder in other languages. For instance in Clojure you have to manually implement rollback functionality.
* Issues with using lock-based concurrency include the following:
  It can be difficult to determine which lock(s) need to be obtained in order for a given block of code to execute safely.
  Variables for which a lock should be acquired can be accessed even when the wrong lock(s) are acquired and even when no locks are acquired.
  A thread is in a deadlock state when it is unable to acquire all the locks it needs to proceed because other threads own them and are themselves deadlocked. This can easily occur unless locks are acquired in a common order.
  Recovering from errors can be complicated because developers must remember to release locks that were acquired by the failing code.
  Correctly synchronized methods cannot be composed into compound methods without additional synchronization.
  The lock-based approach is pessimistic. It assumes that if multiple threads are each running sections of code that access the same memory then only one at a time can safely run. This is not always true. Making this assumption reduces the amount of concurrent processing that can occur.
* These characteristics make transactional memory atomic, consistent and isolated. Note that transactional memory is not durable
    *  If any memory that is written within transaction "A" is modified and committed by transaction "B" before "A" commits, the code in "A" is rerun
    * From the perspective of other threads, all the memory changes made within a transaction appear to happen at the same moment when a transaction is finished committing.
    * If the software crashes or there is a hardware malfunction, data in memory is typically lost.
*  this article distinguishes between a "transaction" and a "transaction try". A transaction includes one or more transaction tries. A transaction that completes without having to retry runs a single transaction try. Otherwise there are more than one.
* Benefits of using transactional memory include the following:

  It provides increased concurrency which means there are more opportunities for processing to be performed simultaneously instead of serially. This is especially true for transactions that only read data. Lock-based concurrency doesn't allow this kind of overlapping execution because it takes a pessimistic approach rather than an optimistic one.
  It is easier to write correct code using transactional memory than writing code that uses locks. The need to determine the locks to be acquired and the order in which to acquire them is removed. Instead, developers identify sections of code that require a consistent view of the set of variables it reads and writes.
  Implementations can guarantee that deadlock [5], livelock [6] and race conditions [7] will never occur.
* Issues with using transactional memory include the following:

  There is a potential for a large number of transaction retries resulting in wasted work.
  There is overhead imposed by transaction bookkeeping such as storing histories of committed values, storing in-transaction values and acquiring locks before committing changes.
  Tool support is currently lacking. For example, having tools that identify how often each transaction retries and why they retry (such as learning which variables had write conflicts) would make it easier to tune applications when necessary.
* write skew
    * For example, suppose a town places a restriction (constraint) on the total number of dogs and cats that a family can own. Let's say the limit is three. When a person obtains a new dog or cat, they are entered in a database. John and his wife Mary have one dog and one cat. John adopts another dog while at the same time Mary adopts a cat. These transactions occur concurrently. Remember that transactions only see changes made by other transactions that have committed. John's transaction attempts to modify the number of dogs they own. The constraint isn't violated because they now have a total of three. Mary's transaction attempts to modify the number of cats they own. Like in the other transaction, the constraint isn't violated because they now have a total of three. Both transactions are allowed to commit, resulting in a total of four dogs and cats which violates the constraint. This is permitted because neither transaction attempts to commit a change to data that is being modified by another concurrent transaction.

      Clojure provides a mechanism for avoiding write skew. See the ensure function discussed later.
* In the context of multithreaded software applications, these terms can be described as follows. A deadlock occurs when concurrently running threads cannot proceed because they are each waiting on a resource for which another has acquired exclusive access. A livelock occurs when concurrently running threads are performing work (as opposed to be being blocked, waiting on resources), but cannot complete due to something that other threads have done or not done. A race condition occurs when the outcome of a thread is affected by the timing of changes to shared state made by another concurrently running thread.
* The notion of composability is critical to building modular software.
    * If we take two pieces of code that individually work correctly, the composition of the two should also be correct.
* In transactional memory we get these aspects of ACID properties:

  Atomicity — On write operations, we want atomic update, which means the update operation either should run at once or not at all.

  Consistency — On read operations, we want consistent view of the state of the program that ensures us all reference to the state, gets the same value whenever they get the state.

  Isolated — If we have multiple updates, we need to perform these updates in isolated transactions. So each transaction doesn't affect other concurrent transactions. No matter how many fibers are running any number of transactions. None of them have to worry about what is happening in the other transactions.
* Advantage of Using STM
    Composable Transaction — Combining atomic operations using locking-oriented programming is almost impossible. ZIO provides the STM data type, which has lots of combinators to compose transactions.

    Declarative — ZIO STM is completely declarative. It doesn't require us to think about low-level primitives. It doesn't force us to think about the ordering of locks. Reasoning concurrent program in a declarative fashion is very simple. We can just focus on the logic of our program and run it in a concurrent environment deterministically. The user code is much simpler of course because it doesn't have to deal with the concurrency at all.

    Optimistic Concurrency — In most cases, we are allowed to be optimistic, unless there is tremendous contention. So if we haven't tremendous contention it really pays to be optimistic. It allows a higher volume of concurrent transactions.

    Lock-Free — All operations are non-blocking using lock-free algorithms.

    Fine-Grained Locking— Coarse-grained locking is very simple to implement, but it has a negative impact on performance, while fine-grained locking significantly has better performance, but it is very cumbersome, sophisticated, and error-prone even for experienced programmers. We would like to have the ease of use of coarse-grain locking, but at the same time, we would like to have the efficiency of fine-grain locking. ZIO provides several data types which are a very coarse way of using concurrency, but they are implemented as if every single word were lockable. So the granularity of concurrency is fine-grained. It increases the performance and concurrency. For example, if we have two fibers accessing the same TArray, one of them read and write on the first index of our array, and another one is read and write to the second index of that array, they will not conflict. It is just like as if we were locking the indices, not the whole array.
* problems
    Large Allocations — We should be very careful in choosing the best data structure using for using STM operations. For example, if we use a single data structure with TRef and that data structure occupies a big chunk of memory. Every time we are updating this data structure during the transaction, the runtime system needs a fresh copy of this chunk of memory.

    Running Expensive Operations— The beautiful feature of the retry combinator is when we decide to retry the transaction, the retry avoids the busy loop. It waits until any of the underlying transactional variables have changed. However, we should be careful about running expensive operations multiple times.