# Topic 1 recap: Request coalescer + its task executor

Code: `playground/src/main/java/volumet/project/playground/coalescer/`
Spec: `RequestCoalescerTest` (13) + `CoalescerTaskExecutorTest` (15), all green.
Production version: `common-resilience` → `components/RequestCoalescer`, `configs/RequestCoalescerExecutorConfig`.

---

## 1. What it is

**Single-flight**: concurrent calls with the same key share **one** execution of the loader; all of them get
the same result. Once the load finishes (success *or* failure) the key is released: the next call loads again.

- It is **not a cache**. It only deduplicates requests that are *in flight at the same moment, in the same JVM*.
- Best for **hot keys** (many users reading the same thing at once). Useless for many distinct keys.
- Only for **idempotent reads**.

```
caller ─► putIfAbsent(key, F) ─┬─ null      → LEADER:   start load on executor → complete F → remove(key, F)
                               └─ existing  → FOLLOWER: wait on existing (a copy of it)
```

## 2. The coalescer: rules and why

| Rule | Why | Bug if broken |
|---|---|---|
| Elect the leader with **`putIfAbsent`** | check + insert in one atomic step | `containsKey` then `put` is a **check-then-act race**: two threads both see "absent", both load, the second `put` overwrites the first |
| Release with **`remove(key, leader)`** | **compare-and-remove**: delete only if the entry is still *mine* | `remove(key)` can delete a *successor's* entry if anything else ever touches the map (invalidate, timeout eviction, refactor) → duplicate loads |
| Release on **every** path, in `finally` | a key that's never removed is a permanent cache entry | forgetting it turns the coalescer into an unbounded cache |
| Release also when **`supplyAsync` throws synchronously** (executor rejects) | the leader future would never complete | all followers of that key hang until timeout, forever |
| Give every caller a **`copy()`** | cancel/complete on a copy never flows back to the source | one caller's timeout `cancel()` fails the shared future with `CancellationException` for **everyone**; the loader keeps running anyway (CF cancel never interrupts) |
| `execute` **rethrows the original** `RuntimeException`/`Error` | the central exception handler maps it as if the loader were called directly | swallowing + `return null` hides failures: caller can't tell "value is null" from "DB down" |
| Unwrap `ExecutionException` **and** `CompletionException` | `whenComplete` hands you a `CompletionException` wrapper | `instanceof RuntimeException` matches the wrapper, not the real cause |
| Timeout → `CoalescerTimeoutException`, **don't cancel** the shared load | the other callers still want the result | |
| Interrupted → **restore the flag**, then throw | see §5 | |

## 3. The executor: rules and why

Plain Java building blocks (what Spring's `SimpleAsyncTaskExecutor` does internally):

| Need | Building block |
|---|---|
| virtual thread per task, named | `Thread.ofVirtual().name(prefix, 1).factory()` + `Executors.newThreadPerTaskExecutor(factory)` |
| concurrency limit | `Semaphore` (`null` for unlimited; `new Semaphore(-1)` blocks forever) |
| MDC propagation | wrap the `Runnable`: **capture on the submitter**, restore + `clear()` in the worker's `finally` |
| graceful shutdown | `shutdown()` → `awaitTermination(t)` → `shutdownNow()` (and `shutdownNow()` if interrupted while waiting) |

### Where the semaphore goes: on the submitter

```
execute(task)                                   worker thread
  requireNonNull(task)
  isShutdown()?   → reject (no permit taken)
  tryAcquire(t)   → fail: reject (no permit taken)
  ─── from here this thread OWNS a permit ───
  try { hand off ─────────────────────────────►  try { run with MDC }
  } catch (RuntimeException) {                   finally { release }   ← normal path
      release; rethrow                     ← hand-off failed: no worker will release it
  }
```

- **Throttle on the submitter**, not in the worker. In the worker it limits *running* tasks but not *waiting*
  ones: every submission still creates a thread that parks → an **unbounded queue of parked threads**, no
  backpressure, `execute` never rejects, tasks still run after their callers gave up.
- Worker-side throttling is legitimate elsewhere (JEP 444: semaphore around one scarce resource inside a task that
  is already running), just not for an executor that promises bounded admission.
- **The permit is held as long as the thing it limits lasts**: the task's run → release in the worker's `finally`.
  Releasing in the submitter's `finally` right after hand-off = no limit at all.

### `Semaphore` has no owner

`release()` is always +1: no check that you acquired, from which thread, or that you exceed the initial count.
That's what allows acquire-on-submitter / release-on-worker, and also why balancing is **entirely your job**:

- release **only after a successful acquire**, **exactly once**, on **every** path;
- what decides "release or not" is **where** the exception is thrown (before / after the acquire), never its type.
  The `try` must start *after* the acquire.
- Contrast: `ReentrantLock.unlock()` by a non-owner → `IllegalMonitorStateException`.

Bugs found on the way (all permit-balance or thread-placement mistakes):

| Bug | Effect |
|---|---|
| `decorator.run(task)` returned the wrapped Runnable and dropped it | tasks never ran |
| `acquire()` inside the worker | no rejection, `acquireTimeout` unused, unbounded parked threads |
| `finally { release() }` around a failed acquire | limit **grows** with every failure |
| exception thrown on the worker | lost (nobody listens to that thread); caller sees success |
| no release when `executor.execute` throws after acquiring | limit **shrinks**, eventually 0 → everything rejected |
| `acquired = !tryAcquire(...)` | every normal call rejected *and* leaking; full executor admitting tasks |
| `close()` without `shutdown()` | `awaitTermination` just sleeps the full timeout; new tasks still accepted |

## 4. Exceptions and contracts

- `Executor.execute` has exactly one way to say "not accepted": **`RejectedExecutionException`**.
  Callers rely on it (`supplyAsync` passes it through, the coalescer fails the leader and frees the key).
- Convert `InterruptedException` from `tryAcquire` **inside** the acquire helper → `RejectedExecutionException`,
  so `execute` sees one outcome: returns = permit held, throws = no permit.
- `close()` (narrowed `AutoCloseable.close()`) **doesn't throw**: callers run it in `finally`/shutdown, where an
  exception would mask the original one and skip the remaining cleanup.

## 5. Interrupts

An interrupt is a **request to stop**, stored as a flag. Blocking methods throw `InterruptedException` **and clear
the flag**. After catching it you hold the only trace of the request. So:

1. propagate it (`throws InterruptedException`), or
2. **restore it**: `Thread.currentThread().interrupt()`, then throw/return.

Never swallow it: you don't own the thread. `shutdownNow()`, `Future.cancel(true)`, request timeouts and Spring
shutdown all work *only* through this flag.

> "Catching `InterruptedException` clears the flag; if I don't rethrow it, I restore it so the thread's owner can
> still see the stop request."

## 6. Time units

`Duration` → `(long, TimeUnit)` APIs that control behaviour: use **`toNanos()` + `NANOSECONDS`** (or
`TimeUnit.NANOSECONDS.convert(duration)`, saturating). `toMillis()` truncates: 0.5 ms → 0 → "don't wait".
The JDK converts to nanos internally anyway. Display-only values (log/exception messages) can use anything.

## 7. Context propagation and security

The loader runs on a worker thread and loses every `ThreadLocal`:

| Lost | Effect |
|---|---|
| `MDC` | logs lose the correlation id → **propagate it** (log-only, harmless) |
| `SecurityContextHolder` | no authentication → **do NOT propagate** |
| JPA auditing user | `createdBy = SYSTEM` |
| `@Transactional` / Hibernate session | no tx, `LazyInitializationException` |
| `RequestContextHolder`, `LocaleContextHolder` | `null` |

**The data-leak scenario**: key `"my-rooms"`, Alice leads, Bob joins → Bob receives Alice's rooms.
The coalescer assumes *same key = same answer*.

> "A coalescer gives every caller with the same key the same result, so if the result depends on who is asking
> and the key doesn't, one user's data leaks to another. The key must include every input the result depends on
> (user id, tenant, roles, locale), and the loader gets those as explicit parameters captured on the request
> thread, never from the SecurityContext. Authorization still runs per caller."

Why production propagates MDC but **not** the SecurityContext: copying it would make the single load run *as the
leader* and hand that result to all followers. Leaving it empty is **fail-fast**: a loader that wrongly reads the
current user fails loudly instead of passing single-user tests and leaking under concurrency.

## 8. Idempotent reads only

Coalescing two `POST /payments`: **only one write happens**, the other caller still gets `201` with someone
else's result (a lost write reported as success); one failure fails everyone.
For duplicate writes (double-click) use **idempotency keys** (client-sent key, *persisted* with the result):
a coalescer only dedups concurrent requests on one pod, not retries or other pods.

## 9. Why limit virtual threads at all

Virtual threads are JVM-managed, multiplexed on a few carrier threads; a blocked one **unmounts** and uses no
core. The limit protects **downstream**, not the CPU: DB pool (Hikari default 10), remote rate limits, memory
per in-flight load. 10,000 unthrottled loads on 10 connections → pool timeouts → *everything* fails.

**When does the limit fill?** Each leader holds one slot for its load's duration; followers are free.
`concurrent loads ≈ distinct-key loads/s × avg load time` (Little's law). It fills with
**many distinct keys** or a **slow downstream** (same traffic, 10× load time = 10× slots), never with one hot key.
Leaders of *other keys* fill the slots, never a key's own followers.

Two gates, in this order:

| Gate | Scope | Passed by |
|---|---|---|
| `putIfAbsent` | per key | one leader per key; followers stop here |
| executor slot | global | leaders only |

Taking a slot *before* the election would make followers occupy slots doing nothing.

## 10. Block forever vs reject after timeout

| Block until a slot frees (Spring default) | Reject after `acquireTimeout` (playground) |
|---|---|
| all work must eventually run: batch, background, consumers | request path with a latency SLA |
| slowing the producer *is* the backpressure | fail fast → 503, let retry/backoff/circuit breaker react |
| simple, never drops work | caps how long a request thread is held; frees the coalescer key promptly |

With a coalescer, a blocked leader keeps its key occupied and followers pile up behind it.

## 11. Playground vs production

| | Playground | Production (`common-resilience`) |
|---|---|---|
| Key | `String` | `VersionedKey` from a `KeySpace` |
| Executor | own `CoalescerTaskExecutor` | Spring `SimpleAsyncTaskExecutor` (virtual threads, limit, MDC decorator, termination timeout) |
| Full executor | wait ≤ `acquireTimeout`, then reject | **blocks the leader forever** |

Typed keys prevent: cross-feature collisions (`rooms:v2:42` ≠ `persons:v1:42`), separator collisions (parts are
escaped: `key("a:b")` ≠ `key("a","b")`), inconsistent formatting (one way to build keys), and arbitrary strings
(compile-time). The schema version matters for Redis cache/lock across pods during rolling deploys, not for the
in-memory coalescer; it uses `VersionedKey` so there's one key type everywhere.

**Production finding**: when the executor is full, `supplyAsync` blocks the leader's request thread *inside*
`submit` with no limit (its own timeout hasn't started), while its followers time out with errors.
`setTaskTerminationTimeout` doesn't help: it only bounds `close()`.

| `SimpleAsyncTaskExecutor` setting | Applies to |
|---|---|
| `setConcurrencyLimit` | `execute`: max running |
| (default) | `execute` when full: wait on a monitor, no limit |
| `setRejectTasksWhenLimitReached(true)` (Spring 6.2+) | `execute` when full: reject immediately (`TaskRejectedException`) |
| `setTaskTerminationTimeout` | `close()`: wait for running tasks |
| `setCancelRemainingTasksOnClose` | `close()`: interrupt the rest |

Fix options: `setRejectTasksWhenLimitReached(true)` (one line, fail fast), or a bounded-wait wrapper like the
playground executor.

## 12. Bulkhead vs executor limit: keep both

They limit different things. In backend-app today:

| Limit | Where | Value | Counts |
|---|---|---|---|
| executor `concurrency-limit` | `requestCoalescerTaskExecutor` | 200 | loads in flight, every kind, every downstream |
| `@Bulkhead(roomRead)` | `RoomRepositoryImpl.findVersionedById`, called **inside the loader** | 8, wait 500ms | concurrent room queries (kept below Hikari's 10) |

**Placement decides what a bulkhead counts:**
- **Inside the loader** (on the worker thread): counts only leaders, i.e. real DB work. Correct.
- **Around the endpoint**, in front of the coalescer: counts every request thread, followers included. With a hot
  key, followers use up the slots doing nothing, and the bulkhead rejects requests that cost zero extra DB work.
  It's the two-gates ordering problem again: a limit placed before the per-key election penalizes followers.

The in-loader bulkhead is a *worker-side* throttle, and that's fine here: the wait is bounded (500ms), the failure
reaches the caller (leader future fails, `GeneralExceptionHandler` → 503), and it guards one specific resource
(the JEP 444 pattern).

**Why the executor limit is still needed:**
1. Loaders without a bulkhead (other key spaces, remote calls, next month's loader) are protected only by it.
2. Threads waiting at the bulkhead still exist: 5,000 distinct keys/s × 0.5s wait ≈ 2,500 parked threads, uncapped.
3. Different downstreams need different limits (DB 8, partner API 3, Redis 50). A coarse global cap plus precise
   per-resource limits; neither one alone can do both.

**How they interact:** a leader holds its executor slot *while it waits for the bulkhead*:
`slot occupancy ≈ distinct-key loads/s × (bulkhead wait + query time)`. Under overload the bulkhead sheds load
correctly, but every doomed leader still holds a slot for 500ms. Above about 400 distinct-key loads/s the 200 slots
fill up, and `SimpleAsyncTaskExecutor` then blocks new leaders forever (the production finding in §11). The bulkhead
makes the executor reach its limit *sooner*, which is one more argument for `setRejectTasksWhenLimitReached(true)`.

**Rule of thumb:**
- bulkhead: inside the loader, per downstream, sized to the resource (below the connection pool). The precise limit.
- executor limit: well above the bulkheads it feeds (200 vs 8), as a safety net and memory bound.
- both fail fast. The bulkhead does already; the executor doesn't yet.
- never put a bulkhead in front of the coalescer.
- caller timeout > lock wait + bulkhead wait + slowest load (with retry), or followers time out while their leader
  is still legitimately waiting (`default-timeout: 10s` in backend-app follows this).

## 13. Java features in this lesson

Details and more examples: `playground/JAVA_FEATURES.md`.

| Feature | Version | Where in topic 1 |
|---|---|---|
| Virtual threads, `Thread.ofVirtual().name(prefix, 1).factory()` | 21 | the executor's thread factory |
| `Executors.newThreadPerTaskExecutor(factory)`, `newVirtualThreadPerTaskExecutor()` | 21 | executor delegate; JDK executor in tests |
| `ExecutorService` as `AutoCloseable` | 19 | `try (ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor())` in tests: closing waits for all submits |
| `Thread.ofVirtual().start(...)` / `.unstarted(...)` | 21 | the interrupt tests' waiter/submitter threads |
| `CompletableFuture.copy()` | 9 | one future per caller |
| `CompletableFuture.orTimeout(...)` | 9 | `runOn(...)` helper in the executor tests |
| Pattern matching for `instanceof` | 16 | `if (error instanceof RuntimeException runtime) throw runtime;` |
| `List.of`, `Set.of` | 9 | tests |
| `record` | 16 | `CoalescerExecutorSettings` with compact-constructor validation |
| `Objects.requireNonNull` | 7 | (not new, but used everywhere) |

```java
// Java 8: no virtual threads, a pooled, named executor
ThreadFactory f = r -> { Thread t = new Thread(r); t.setName("request-coalescer-" + n.incrementAndGet()); return t; };
ExecutorService pool = Executors.newFixedThreadPool(200, f);

// Java 21: one cheap virtual thread per task, named, no pool; limit with a Semaphore instead
ExecutorService ex = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("request-coalescer-", 1).factory());
```

```java
// Java 8
if (error instanceof RuntimeException) { throw (RuntimeException) error; }
// Java 16
if (error instanceof RuntimeException runtime) { throw runtime; }
```

## 14. Interview one-liners

- **Check-then-act**: two separate operations, another thread runs in between; use the atomic compound op.
- **Compare-and-remove**: clean up only what is still yours.
- **Copy per caller**: isolate cancellation; the shared future is never handed out.
- **Semaphore**: a counter without owner; every successful acquire, exactly one release, on every path.
- **Placement decides who waits**: in the worker, unbounded queue, hidden overload; at submission, bounded, backpressure.
- **Interrupts**: restore the flag unless you rethrow.
- **Key = every input the result depends on**; loader takes them explicitly, never from thread-local context.
- **Coalesce only idempotent reads**; duplicate writes need persisted idempotency keys.
- **The limit protects downstream, not the CPU.**
- **Bulkhead inside the loader, executor limit around everything**: precise per resource + coarse global cap.

## 15. Open items

- `RequestCoalescer` lines 109/119: pass the cause, fix the `ExecutionException` message.
- Optional production fix: `setRejectTasksWhenLimitReached(true)` in `RequestCoalescerExecutorConfig` + a test.
