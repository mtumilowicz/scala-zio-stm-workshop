[![Build Status](https://app.travis-ci.com/mtumilowicz/scala-zio-stm-workshop.svg?token=PwyvjePQ7aiAX51hSYLE&branch=main)](https://app.travis-ci.com/mtumilowicz/scala-zio-stm-workshop)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
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
    * https://stackoverflow.com/questions/56384817/why-wait-with-predicate-solves-the-lost-wakeup-for-condition-variable

## preface
* goals of this workshop
* workshop plan
    1. implement data structure that index values and keep top n of them aside
    1. process messages in consecutive order specified in field
        * message n+1 cannot be processed if message n was not processed
    1. move actor from one supervisor into other supervisor
    1. implement bank account transfers

## prerequisite
* problems with locking
    * taking too few/many locks
        * variables for which a lock should be acquired can be accessed even when no locks are acquired
    * taking the wrong lock
        * it can be difficult to determine which lock(s) need to be obtained in order for a given block of code to execute safely
    * taking the right lock in the wrong order
        * deadlock can easily occur unless locks are acquired in a common order
    * error recovery
        * developers must remember to
            * release locks that were acquired by the failing code
            * leave the world in th right state
    * lost wake-ups
        * example
            * lost wakeup happens this way:
                1. acquire the lock that protects the data
                1. check to see whether we need to wait and we see that we do
                1. need to release the lock because otherwise no other thread can access the data
                1. wait for a wakeup
            * between steps 3 and 4, another thread could acquire the lock and send a wakeup
                * we have released the lock, so another thread can do this, but we aren't waiting yet, so we wouldn't get the signal
            * so long as step 2 is done under the protection of the lock and steps 3 and 4 are atomic, there is no risk of a lost wakeup
    * composition/encapsulation dies
        * synchronized methods cannot be composed into compound methods without additional synchronization
        * sequential composition of two thread-safe actions is no longer thread-safe
            * other threads may interfere in between of these actions
            * applying a third lock to protect both may lead to common sources of errors like deadlocks or race conditions
    * pessimistic approach
    * coarse-grained locking is very simple to implement, but it has a negative impact on performance
    * fine-grained locking has better performance, but it is very cumbersome, sophisticated, and error-prone
        * example: locking the indices, not the whole array
    * priority inversion
        * higher priority process waits until resource is released by a lower priority process
* optimistic concurrency
    * enable non-conflicting transactions to execute and commit concurrently, increasing throughput
    * motivated by Lamport’s argument "contention for [shared objects] is rare in a well-designed system"
    * if we haven't tremendous contention it really pays to be optimistic
        * allows a higher volume of concurrent transactions
* compare-and-swap
    * https://github.com/mtumilowicz/java12-fundamentals-nonblocking-stack-workshop
    * https://github.com/mtumilowicz/java-concurrency-compare-and-swap
    * example: fibonacci
        ```
        AtomicReference<Tuple> fib = new AtomicReference<>((0, 1));

        public long next() {
            return fib.updateAndGet(current -> new Tuple(current.snd, current.fst + current.snd)).snd;
        }
        ```

## software transaction memory
* is compare-and-swap stretched on multiple state (TVar)
    * example: move message from one queue to another queue
        * condition: `source.nonEmpty` && `target.nonFull`
* concept ported from the SQL database world
    * SQL transactions satisfy ACID (Atomicity, Consistency, Isolation, Durability) properties
    * STM: only Atomicity, Consistency and Isolation are satisfied because the mechanism runs in-memory
        * Atomicity
            * effects become visible to another thread all at once
                * example: ensures that no other thread can see a state in which money has been deposited in to but not yet withdrawn from
            * means that either all the changes in a transaction will be made successfully (commit) or none of them will be (rollback)
        * Isolation
            * transactions are isolated from each other such that each transaction sees a consistent view of memory
                * none of them have to worry about what is happening in the other transactions
            * it is as if action takes a snapshot of the state of the world when it begins running, and then executes against that snapshot
        * Consistency
            * transactions happen successfully if there aren't any conflicting changes
            * ensures us all reference to the state gets the same value whenever they get the state
        * note that transactional memory is not durable
            * if the software crashes or there is a hardware malfunction, data in memory is typically lost
* is a technique for allowing multiple state-changing operations to be grouped together and performed as a single atomic operation
    * all or nothing semantics
    * within atomic blocks, you can reason about your code as if the program were sequential
* steps
    1. when (atomically act) is performed, a thread-local transaction log is allocated, initially empty
    1. action act is performed, without taking any locks at all
    1. while performing act, each call to writeTVar writes the address of the TVar and its new value into the log
        * it does not write to the TVar itself
        * each call to readTVar first searches the log
            * readTVar is an O(n) operation in the length of the log
            * in case the TVar was written by an earlier call to writeTVar
            * if no such record is found, the value is read from the TVar itself, and the TVar and value read are recorded in the log
                * in the meantime, other threads might be running their own atomic blocks, reading and writing TVars like crazy
    1. when the action act is finished, the implementation first validates the log and, if validation is successful, commits the log
        * validation step examines each readTVar recorded in the log, and checks that the value in the log matches the value currently in the real TVar
            * if so => validation succeeds
            * if no => abort & retry
                * nothing has happen in main memory yet
    1. commit step takes all the writes recorded in the log and writes them into the real TVars
        * process takes place atomically by locking all the TVars involved in the transaction for the duration
            * transactions operating on disjoint sets of TVars can proceed without interference
* retry
    * if any memory that is written within transaction "A" is modified and committed by transaction "B" before "A" commits, the code in "A" is rerun
        * transaction t1 and t2 conflict when
            * their write sets overlap
            * write set of t1 overlaps with read set of t2
            * write set of t2 overlaps with read set of t1
    * meaning is simply "abandon the current transaction and run it again"
    * transaction can be rolled back only if we can track exactly what effects it has
        * not be possible if arbitrary I/O were allowed inside a transaction
            * some I/O cannot be undone, like making a noise
        * range of side effects for STM actions is much smaller
            * only read or write a transactional variable
    * log contains a record of all the readTVar operations => can be used to discover the full set of TVars read during the transaction
        * which we need to know in order to implement retry
    * if a retry action is performed, the current transaction is abandoned and retried at some later time
        * it would be correct to retry the transaction immediately, but it would also be inefficient
            * example: consumer needs to block if there isn't enough data
                * execution right away => buffer is likely to be still empty and then you retry again
        * an efficient implementation would instead wait until some other thread writes to TVar
            * each TVar has a watch list of threads that should be woken up if the TVar is modified
            * retry adds the current thread to the watch list of all the TVars read during the current transaction
    * discarding the effects of a transaction is easy; we just throw away the log
        * writeTVar operations are kept in the log rather than applied to main memory immediately
        * hence, aborting a transaction has a fixed small cost
* benefits
    * composability
        * easily composed with any other abstraction built using STM
        * combining atomic operations using locking-oriented programming is almost impossible
            * mutexes don't compose
        * is critical to building modular software
            * two pieces of code that individually work correctly => composition of the two should also be correct
        * You can construct arbitrarily large atomic operations on shared state, which can
            simplify the implementation of concurrent data structures with fine-grained lock‐
            ing.
    * modularity
        * not expose the details of how your abstraction ensures safety
        * not the case with other forms of concurrent communication, such as locks or MVars
    * declarative
        * doesn't require to think about low-level primitives
        * doesn't force to think about the ordering of locks
        * reasoning is simplified
            * just focus on the logic of our program and run it in a concurrent environment deterministically
        * user code is much simpler of course because it doesn't have to deal with the concurrency at all
    * easy to maintain invariants on state in the presence of exceptions
        * transaction in progress is aborted if an exception occurs
    * lock-free - all operations are non-blocking
    * implementations can guarantee that deadlock, livelock and race conditions will never occur
        * deadlock = threads cannot proceed because they are each waiting on a resource for which another thread has acquired exclusive access
        * livelock = threads are performing work, but cannot complete due to something that other threads have done or not done
        * race condition = outcome of a thread is affected by the timing of changes to shared state made by another thread
* drawbacks
    * potential for a large number of transaction retries resulting in wasted work
    * overhead imposed by transaction bookkeeping such as storing histories of committed values, storing in-transaction values and acquiring locks before committing changes
    * tool support is lacking
        * example
            * learning which variables had write conflicts
            * identify how often each transaction retries and why they retry would make it easier to tune applications when necessary
    * transaction can block on an arbitrary condition over particular TVar => runtime must wake them all
        * runtime doesn’t know whether any individual transaction will be able to make progress after the TVar is changed
            * after all, they might all be able to continue now
            * it must run the transaction to find out
                * example
                    ```
                    // transaction1
                    do x <- takeTMVar m
                    when (x /= 42) retry

                    // transaction2
                    do x <- takeTMVar m
                    when (x /= 43) retry
                    ```
        * there is no guarantee that threads will be unblocked in FIFO order and no guarantee of fairness
    * difficult to ensure good performance without a lazy language and difficult to reason about without enforced immutability and monads
        * barely gets used outside small examples except in Haskell
    * databases are already really good at doing what refs do
        * assuming the data you’re mutating is being persisted => why bring that into memory just to do what the database is built for?
    * sometimes it is better to organize state into a single atom
* good practices
    * never put any side effect in STM - it may be executed any arbitrary number of times
        * if a conflict arises on commit -> rerun
        * example: send email, fire missile
    * avoid large allocations
        * every time we are updating data during the transaction, the runtime system needs a fresh copy of this chunk of memory
    * design short, small, and independent transactions
        * to avoid unnecessary conflicts and aborts
        * transaction takes a long time => increases the chance that another transaction will modify one or more of the same TVars
            * causing the current transaction to be re-executed
        * in the worst case, a long-running transaction re-executes indefinitely
            * repeatedly aborted by shorter transactions
    * never read an unbounded number of TVars in a single transaction
        * O(n) performance of readTVar then gives O(n^2) for the whole transaction
    * employ commutative and idempotent operations
        * they do not cause any conflicts and can be safely executed in any order or multiple times
* haskell context
    * type system prevents us running IO actions inside STM actions
    * ability to choose an alternative action if the first one retries
        * example: withdraw money from account A if it has enough money, but if not then withdraw it from account B
        * orElse :: STM a -> STM a -> STM a
        * lets us combine two blocking transactions such that one is performed but not both
        * has the following behavior:
            * first action is executed
                * if returns a result => orElse call returns it and ends
                * if calls retry => first action’s effects are discarded_ and second action is executed instead
* clojure context
    * designed and built an STM around multi-version concurrency control (MVCC) and snapshot isolation
        * In MVCC, each transaction operates on a snapshot of the database at a specific point in time, allowing readers to access consistent data without being blocked by writers
    * in basic MVCC only writes are tracked
    * all reads are done with a consistent basis
        * as of the transaction start, and incorporating within-transaction writes
    * can be subject to write-skew
        * example: write to X depends on read of Y, and Y is not otherwise written, and thus not ensured consistent at transaction end
        * Clojure provides a mechanism for avoiding write skew
            * `ensure` operation for opt-in tracking of reads when needed
