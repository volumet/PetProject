# Playground

Practice module. Each topic is a stub to implement plus a test suite that is its specification.
It deliberately does **not** depend on `common-resilience` (which already contains production versions);
don't peek there until your version is reviewed.

Workflow per topic: Claude sets up the stub + spec -> you implement until green -> Claude reviews.

## Running

Unfinished exercises fail by design, so the normal build skips these tests. Run them explicitly (JDK 22):

```
./backend/mvnw -f pom.xml -pl playground test -Dplayground.skip=false
./backend/mvnw -f pom.xml -pl playground test -Dplayground.skip=false -Dtest=RequestCoalescerTest
```

Running a test class from the IDE ignores the skip switch.

## Modern Java

Every lesson deliberately uses post-Java-8 features and lists them in a **"Java features in this lesson"**
section. The full catalog with *Java 8 vs now* examples is `JAVA_FEATURES.md`.

## Roadmap

| #  | Topic                              | Package                         | Status      |
|----|------------------------------------|---------------------------------|-------------|
| 1  | Request coalescer (single-flight)  | `playground.coalescer`          | done (recap: `recaps/01-request-coalescer.md`) |
| W1 | Warm-up: Java records              | `playground.records`            | done (recap: `recaps/02-records.md`) |
| W2 | Warm-up: versioned keys            | `playground.keys`               | ready       |
| W3 | Warm-up: Redis config (needs Redis) | `playground.redis`             | next        |
| 2  | Redis distributed lock             | `playground.lock`               | —           |
| 3 | Stale-while-revalidate (SWR)       | `playground.swr`                | —           |
| 4 | Cache stampede guard               | `playground.stampede`           | —           |
| 5 | RabbitMQ (Spring AMQP)             | `playground.amqp`               | —           |
| 6 | Spring Cloud Stream over AMQP      | `playground.stream`             | —           |

## W1: Java records

Plain Java, no Redis. Do the four parts in order; each Javadoc explains the feature it practises.

| Part | File | Practises |
|---|---|---|
| 1 | `RoomSummary` | what the compiler generates, compact constructor (validate + normalize), delegating constructor, "wither" |
| 2 | `Tags` | immutability is shallow: defensive copy of a `List`, unmodifiable accessor |
| 3 | `DateRange` | cross-field validation, static factories, derived values (no extra fields allowed) |
| 4 | `LoadOutcome` | sealed interface + records + exhaustive `switch` with record patterns |

Spec: `RoomSummaryTest`, `TagsTest`, `DateRangeTest`, `LoadOutcomeTest` (26 tests; a few pass from the start
because records generate them: that's the point they make). Also checks that Jackson serializes a record by its
components and that **deserialization goes through your compact constructor**.

```
./backend/mvnw -f pom.xml -pl playground test -Dplayground.skip=false -Dtest='RoomSummaryTest,TagsTest,DateRangeTest,LoadOutcomeTest'
```

**Java features in this lesson** (details: `JAVA_FEATURES.md`): records (16), compact constructors, sealed
interfaces (17), switch expressions (14), pattern matching for `switch` and record patterns (21), `List.copyOf` (10),
`String.isBlank`/`strip` (11), text blocks (15, in `RoomSummaryTest`), `var` (10). Optional extra: try `_` (22)
for a component you don't need in a pattern.

Things to think about (Q&A after the review):
- What exactly does a record generate, and what can't a record do that a class can?
- When is a record *not* immutable, and what does `List.copyOf` do that `Collections.unmodifiableList` doesn't?
- Why would a record with a `byte[]` component surprise you in `equals`?
- What does `sealed` buy you in a `switch`, compared to a `default` branch?
- Why are records a good fit for DTOs and cache keys, and a bad fit for JPA entities?

## W2: versioned keys

Plain Java, uses records. A simplified version of production's `common-resilience` `KeySpace` / `VersionedKey`
(no stale window yet: that comes with SWR in topic 3). Don't peek at the production version until reviewed.

| File | Practises |
|---|---|
| `KeySpace` | a record with regex validation, nullable component (`ttl`), overloaded factories, `key(...)` |
| `VersionedKey` | record as a map key, normalizing parts (`42` == `42L`), defensive copy, two renderings, collision-free escaping |

Spec: `KeySpaceTest`, `VersionedKeyTest` (16 tests).

```
./backend/mvnw -f pom.xml -pl playground test -Dplayground.skip=false -Dtest='KeySpaceTest,VersionedKeyTest'
```

**Java features in this lesson**: records as map keys, `List.copyOf` (10), `Stream.toList()` (16) or
`Collectors.joining`, `String.isBlank` (11), varargs + records, `var` (10).

Things to think about:
- Why must `'%'` be escaped as well as `':'`, and in which order do the two replacements have to run?
- Two `KeySpace("rooms", 2, ...)` with different TTLs: are their keys equal? Should they be?
- Why is the schema version part of the key rather than part of the stored value?
- Where does each rendering (`cacheKey`, `qualified`) get used, and why do both exist?

## Topic 1: request coalescer

**Goal:** when N requests for the same key arrive while a load is in flight, run the loader once and
give all N the result. It is *not* a cache: once the load finishes, the key is free again.

Files:
- `src/main/java/volumet/project/playground/coalescer/RequestCoalescer.java`: implement `submit` and `execute`
- `src/test/java/volumet/project/playground/coalescer/RequestCoalescerTest.java`: the spec (13 tests)

What the spec checks:
1. One load for concurrent same-key callers (64 threads racing into `submit`).
2. `submit` never blocks and never runs the loader on the caller's thread.
3. Keys are independent.
4. Key released after success, after failure, and **after the executor rejects the task** (no stuck keys).
5. Failures reach every waiter; `execute` rethrows the original `RuntimeException`/`Error` instance.
6. Timeouts and cancellation are per caller: one caller giving up (even the first one) must not cancel the load for others.
7. An interrupted waiter keeps its interrupt flag.
8. `null` is a valid result.

### Topic 1b: the coalescer's task executor

The executor that runs the loads, in plain Java (production uses Spring's `SimpleAsyncTaskExecutor`).
Suggested order: get `RequestCoalescerTest` green first (it uses a JDK executor), then this one.

Files:
- `CoalescerExecutorSettings.java`: record of settings (validation is up to you)
- `CoalescerTaskExecutor.java`: implement the constructor, `execute` and `close`
- `CoalescerTaskExecutorTest.java`: the spec (15 tests)

What the spec checks:
1. One new virtual thread per task, named `<prefix><n>`, never the caller's thread.
2. Concurrency limit (`-1` = unlimited, `0` invalid): when full, the submitter blocks up to `acquireTimeout`
   and is then rejected with `RejectedExecutionException`. The coalescer turns this into a failed load and frees the key.
   (Production blocks forever instead; the review will ask which you'd prefer and why.)
3. A slot is freed even when the task throws; an interrupted submitter is rejected and keeps its interrupt flag.
4. MDC propagation: the task sees the submitter's correlation id; neither side's MDC leaks into the other.
5. `close()`: rejects new tasks, waits up to `awaitTermination`, then interrupts stragglers; idempotent.
6. With the coalescer: 64 followers join a load that holds the only slot. Followers must not need a slot.

Things to think about (the review will ask):
- Which `ConcurrentMap` operation makes "first caller becomes the leader" atomic? What race does
  `get` followed by `put` have?
- When is it safe to remove the entry, and why does `remove(key, value)` beat `remove(key)`?
- What does `CompletableFuture.cancel` on a shared future do to the other callers?
- The loader runs on another thread: what thread-local context (SecurityContext, MDC, transaction) does it lose?
- Virtual threads are cheap, so why limit concurrency at all? What does the limit actually protect?
- Where exactly must the MDC snapshot be taken, on the submitting thread or the worker? Why?
- What happens to a permit if starting the thread itself fails?
