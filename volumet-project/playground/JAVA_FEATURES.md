# Java 9 → 22: what changed since Java 8

The project runs on **Java 22**. This catalog covers the **finalized** features worth knowing for daily work and
interviews, each with a *Java 8 vs now* example, the gotchas, and where the playground practises it.
Preview features (need `--enable-preview`) are listed at the end and not used.

Legend for **Practised in**: `T1` = topic 1 (coalescer), `W1` records, `W2` keys, `W3` Redis config,
`T2` lock, `T3` SWR, `T4` stampede, `T5` RabbitMQ, `T6` Cloud Stream. *Planned* = not built yet.

LTS releases (what companies run): **11, 17, 21** (and 25). Most interview questions about "modern Java" are about
features up to 21.

---

## Quick map

| Feature | Version | Practised in |
|---|---|---|
| `var` local variables | 10 (lambdas 11) | T1, everywhere |
| Collection factories `List.of`, `Map.of`, `List.copyOf` | 9 / 10 | T1, W1, W2 |
| `CompletableFuture.copy`, `orTimeout`, `completeOnTimeout`, `delayedExecutor` | 9 | T1 (copy, orTimeout), T3 planned |
| try-with-resources on an existing variable | 9 | T1 |
| Private interface methods | 9 | T5 planned |
| `Optional` additions (`ifPresentOrElse`, `or`, `stream`, `orElseThrow()`, `isEmpty`) | 9 / 10 / 11 | W3 planned |
| `String` additions (`isBlank`, `strip`, `lines`, `repeat`, `formatted`) | 11 / 15 | W1, W2 |
| `Predicate.not`, `Files.readString`, `HttpClient` | 11 | W3 / T4 planned |
| Switch expressions (`->`, `yield`) | 14 | W1 |
| Helpful `NullPointerException` messages | 14 | free, everywhere |
| Text blocks `"""` | 15 | W1 tests, T5 planned |
| **Records** | 16 | W1, W2, all later topics |
| Pattern matching for `instanceof` | 16 | T1 |
| `Stream.toList()`, `mapMulti` | 16 | W2, T5 planned |
| **Sealed classes / interfaces** | 17 | W1, T2/T3/T5 planned |
| `RandomGenerator`, `HexFormat` | 17 | T2 / T4 planned |
| `ExecutorService` is `AutoCloseable`; `Thread.join(Duration)`, `Thread.sleep(Duration)` | 19 | T1 |
| **Virtual threads**, `Thread.ofVirtual()`, `newThreadPerTaskExecutor` | 21 | T1 |
| **Pattern matching for `switch`** | 21 | W1, T3/T5 planned |
| **Record patterns** (deconstruction) | 21 | W1, T3/T5 planned |
| Sequenced collections (`getFirst`, `getLast`, `reversed`) | 21 | T6 planned |
| `Math.clamp` | 21 | T4 planned |
| **Unnamed variables and patterns `_`** | 22 | T5 planned |

---

## 1. Language

### `var` (10; in lambda parameters 11)

```java
// Java 8
Map<VersionedKey, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();
for (Map.Entry<VersionedKey, CompletableFuture<Object>> e : inFlight.entrySet()) { ... }

// now
var inFlight = new ConcurrentHashMap<VersionedKey, CompletableFuture<Object>>();
for (var e : inFlight.entrySet()) { ... }
```

- Local variables only (not fields, parameters, return types). Still **statically typed**: the type is inferred
  once, at compile time.
- Use it when the type is obvious from the right-hand side; avoid it when it hides the type (`var x = service.load();`).
- `var list = new ArrayList<>();` infers `ArrayList<Object>`: give the type argument on the right.

### Switch expressions (14)

```java
// Java 8: statement, fall-through, needs break, can't return a value
String label;
switch (status) {
    case ACTIVE:  label = "on";  break;
    case DELETED: label = "off"; break;
    default: throw new IllegalStateException();
}

// now: an expression, no fall-through, exhaustive for enums
String label = switch (status) {
    case ACTIVE -> "on";
    case DELETED, ARCHIVED -> "off";          // several labels
    case PENDING -> {
        log.debug("pending");
        yield "wait";                         // yield = the value of a block branch
    }
};
```

- For an **enum** (or sealed type) covering all cases, no `default` is needed; adding a constant later makes the
  switch fail to compile, which is what you want.

### Text blocks (15)

```java
// Java 8
String json = "{\n" +
              "  \"id\": 1,\n" +
              "  \"name\": \"Lab\"\n" +
              "}";

// now
String json = """
        {
          "id": 1,
          "name": "Lab"
        }
        """;
```

- Indentation is removed up to the least-indented line (the closing `"""` counts). No escaping of `"`.
- `\` at the end of a line joins lines; `\s` keeps a trailing space.
- Great for JSON fixtures in tests, SQL, Lua. In this project, Lua scripts still go in `.lua` files (CLAUDE.md).

### Records (16)

```java
// Java 8: ~40 lines: fields, constructor, getters, equals, hashCode, toString
public final class RoomSummary {
    private final long id; private final String name;
    public RoomSummary(long id, String name) { ... }
    public long getId() { return id; }
    @Override public boolean equals(Object o) { ... }
    @Override public int hashCode() { ... }
    @Override public String toString() { ... }
}

// now
public record RoomSummary(long id, String name) {
    public RoomSummary {                                  // compact canonical constructor
        Objects.requireNonNull(name, "name");
        name = name.trim();                               // you may reassign the parameter
    }
}
```

- Generated: `private final` fields, canonical constructor, accessors **`id()`** (no `get`), `equals`/`hashCode`/
  `toString` over **all** components.
- Can have: static fields and methods, instance methods, extra constructors (must call `this(...)`), nested types,
  implement interfaces. Can't: extend a class (implicitly `final`, extends `java.lang.Record`), declare instance
  fields beyond the components.
- **Shallow immutability**: a `List` component is still mutable unless you `List.copyOf` it.
- **Arrays** compare by reference in the generated `equals`: `new Raw(new byte[]{1}).equals(new Raw(new byte[]{1}))`
  is `false`. Avoid array components, or override `equals`/`hashCode`.
- Fits: DTOs, value objects, cache keys, messages, results. Doesn't fit: **JPA entities** (need a no-arg
  constructor, mutable state, proxies).
- Jackson (2.12+) serializes by components and deserializes through the canonical constructor, so your validation runs.

### Pattern matching for `instanceof` (16)

```java
// Java 8
if (cause instanceof RuntimeException) {
    throw (RuntimeException) cause;
}

// now: test + cast + bind in one step
if (cause instanceof RuntimeException runtime) {
    throw runtime;
}
if (!(obj instanceof String s) || s.isBlank()) return;   // s is in scope where the match is certain
```

### Sealed classes and interfaces (17)

```java
public sealed interface LoadOutcome<T> permits Hit, Miss, Failed { }
record Hit<T>(T value) implements LoadOutcome<T> { }
record Miss<T>() implements LoadOutcome<T> { }
record Failed<T>(Throwable cause) implements LoadOutcome<T> { }
```

- The compiler knows **all** subtypes, so a `switch` over them is **exhaustive without `default`**.
- Every permitted subtype must be `final` (records are), `sealed`, or `non-sealed` (reopens the hierarchy).
- Permitted subtypes must be in the same module (or the same package, without modules).
- Use for closed sets of cases: results (`Hit/Miss/Failed`), events, commands, states. It's Java's take on
  algebraic data types.
- Why not `default`? A `default` silently swallows a case added later; a sealed switch fails to compile instead.

### Pattern matching for `switch` + record patterns (21)

```java
// Java 8: instanceof chain
if (o instanceof Hit) { return "hit: " + ((Hit<?>) o).value(); }
else if (o instanceof Miss) { return "miss"; }
else if (o instanceof Failed) { return "failed: " + ((Failed<?>) o).cause().getMessage(); }
else throw new IllegalStateException();

// now
return switch (outcome) {
    case Hit<?>(var value)                  -> "hit: " + value;          // record pattern: deconstructs
    case Miss<?>()                          -> "miss";
    case Failed<?>(IOException e)           -> "io: " + e.getMessage();  // nested type pattern
    case Failed<?>(var cause)               -> "failed: " + cause.getMessage();
};

// guards
case Hit<?>(String s) when s.isBlank() -> "blank hit";
```

- Order matters: more specific cases first (the compiler rejects a case that can never match: *dominance*).
- `case null ->` is allowed; without it, a `null` selector throws `NullPointerException` (as before).
- Works for any type, not only sealed; for non-sealed types you need a `default`.

### Unnamed variables and patterns `_` (22)

```java
case Failed<?>(_) -> "failed";                         // don't care about the component
try { ... } catch (NumberFormatException _) { ... }    // unused exception
map.forEach((_, value) -> process(value));             // unused lambda parameter
for (var _ : items) count++;
```

- Makes "this is intentionally unused" explicit and stops IDE warnings. Final in 22 (preview in 21).

### Private interface methods (9)

```java
interface MessageHandler {
    default void handle(Message m) { log(m); process(m); }
    private void log(Message m) { ... }        // shared helper for default methods, not part of the API
    void process(Message m);
}
```

### Helpful `NullPointerException` messages (14)

```
// Java 8:  java.lang.NullPointerException
// now:     Cannot invoke "String.trim()" because the return value of "RoomSummary.name()" is null
```

Free, no code change. Interview point: it tells you *which* part of `a.b().c()` was null.

---

## 2. Concurrency

### Virtual threads (21)

```java
// Java 8: platform threads, expensive (≈1 MB stack, OS thread), so you pool them
ExecutorService pool = Executors.newFixedThreadPool(200);

// now: cheap JVM-managed threads, one per task, no pooling
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {        // AutoCloseable since 19
    executor.submit(() -> repository.load(id));
}

Thread t = Thread.ofVirtual().name("request-coalescer-", 1).start(task);   // builder API
ThreadFactory factory = Thread.ofVirtual().name("worker-", 1).factory();
ExecutorService ex = Executors.newThreadPerTaskExecutor(factory);
```

- Many virtual threads run on a few **carrier** (platform) threads. Blocking I/O **unmounts** the virtual thread
  and frees the carrier, so 100k blocked virtual threads cost memory, not OS threads.
- **Don't pool them**: create one per task. Limit *access to resources* with a `Semaphore` instead (T1).
- **Pinning** (Java 21–23): blocking inside `synchronized` keeps the carrier busy; prefer `ReentrantLock` around
  blocking calls. (Fixed in Java 24.)
- `ThreadLocal`s work but each of a million threads gets its own copy: keep them small.
- Help I/O-bound work, not CPU-bound work.

### `ExecutorService` is `AutoCloseable` (19)

`close()` = `shutdown()` + wait for all tasks; on interrupt, `shutdownNow()`. So `try (var ex = ...) { }` waits for
everything submitted inside the block (T1 tests use this).

### `CompletableFuture` additions (9)

| Method | Does |
|---|---|
| `copy()` | a dependent future: completes with the source, but completing/cancelling it doesn't affect the source (T1: one per caller) |
| `orTimeout(t, unit)` | completes exceptionally with `TimeoutException` after t (T1 tests) |
| `completeOnTimeout(value, t, unit)` | completes with a fallback value after t |
| `CompletableFuture.delayedExecutor(t, unit)` | an executor that runs tasks after a delay (T3 planned: background refresh) |
| `failedFuture(ex)`, `completedStage(v)` | factories |
| `exceptionallyCompose(fn)` (12) | recover with another future |

### `Duration`/`TimeUnit` interop (9 / 11) and `Thread` with `Duration` (19)

```java
TimeUnit.NANOSECONDS.convert(duration)      // 11, saturates instead of overflowing
Thread.sleep(Duration.ofMillis(50));        // 19
thread.join(Duration.ofSeconds(1));         // 19, returns boolean (terminated?)
duration.toMillisPart(), toSecondsPart()    // 9
```

---

## 3. Collections and streams

### Immutable collection factories (9) and copies (10)

```java
// Java 8
List<String> a = Collections.unmodifiableList(Arrays.asList("x", "y"));

// now
List<String> a = List.of("x", "y");
Map<String, Integer> m = Map.of("a", 1, "b", 2);
Map<String, Integer> big = Map.ofEntries(Map.entry("a", 1), Map.entry("b", 2));
List<String> safe = List.copyOf(input);      // 10
```

| | `List.of` / `List.copyOf` | `Collections.unmodifiableList` | `Arrays.asList` |
|---|---|---|---|
| modifiable | no | no (view) | `set` yes, `add` no |
| reflects later changes to the source | n/a / **no** (copy) | **yes** (it's a view) | yes |
| null elements | **rejected** (NPE) | allowed | allowed |

- `List.copyOf` returns the same instance if the input is already an immutable `List.of`: no needless copies.
- `Map.of` / `Set.of` iteration order is **unspecified** (and randomized per JVM run): don't depend on it.
- `Map.of` rejects duplicate keys and null keys/values.

### Streams

```java
list.stream().map(...).toList();                 // 16: unmodifiable; Java 8: collect(Collectors.toList())
Collectors.toUnmodifiableList()                  // 10
Stream.ofNullable(x)                             // 9: empty stream for null
stream.takeWhile(p), stream.dropWhile(p)         // 9
Stream.iterate(1, i -> i < 100, i -> i * 2)      // 9: with a stop condition
Collectors.teeing(c1, c2, merger)                // 12: two collectors in one pass
stream.mapMulti((x, sink) -> { ... })            // 16: flatMap without building streams
.filter(Predicate.not(String::isBlank))          // 11
```

- `toList()` vs `collect(Collectors.toList())`: `toList()` is **unmodifiable** and allows nulls; the collector
  returns an `ArrayList` in practice (not guaranteed). Don't mutate either.

### Sequenced collections (21)

```java
list.getFirst(); list.getLast(); list.reversed();       // Java 8: list.get(0), list.get(list.size() - 1)
linkedHashMap.firstEntry(); linkedHashSet.reversed();
```

A common supertype `SequencedCollection` for everything with a defined order (`List`, `Deque`, `LinkedHashSet`,
`SortedSet`).

---

## 4. Strings

```java
" ".isBlank()          // 11: true   (isEmpty() is false)
" x ".strip()          // 11: Unicode-aware trim()
"ab".repeat(3)         // 11: "ababab"
text.lines()           // 11: Stream<String>
"%s:%d".formatted(k, v) // 15: same as String.format, as an instance method
text.indent(2)         // 12
"x".transform(f)       // 12
```

- `trim()` removes chars ≤ U+0020 only; `strip()` removes Unicode whitespace. Prefer `strip`/`isBlank`.

---

## 5. `Optional`

```java
opt.ifPresentOrElse(v -> use(v), () -> miss());   // 9
opt.or(() -> loadFromDb())                        // 9: lazily another Optional
opt.stream()                                      // 9: 0 or 1 element
opt.orElseThrow()                                 // 10: preferred name for get()
opt.isEmpty()                                     // 11
```

Interview point: `Optional` is for **return types** that may be absent. Not for fields, parameters, or collections.

---

## 6. Other APIs

| API | Version | One line |
|---|---|---|
| `HttpClient` | 11 | built-in HTTP/1.1 + HTTP/2 client, sync and async (`sendAsync` → `CompletableFuture`) |
| `Files.readString` / `writeString` | 11 | one call |
| `HexFormat` | 17 | `HexFormat.of().formatHex(bytes)`: random tokens, digests |
| `RandomGenerator` | 17 | common interface for `Random`, `SplittableRandom`, `ThreadLocalRandom`, new algorithms |
| `Math.clamp(v, min, max)` | 21 | bound a value (e.g. a backoff with jitter) |
| `Objects.requireNonNullElse(x, def)`, `checkIndex` | 9 | |
| `String.chars()` etc. | 8 | (unchanged) |

---

## 7. JVM and tooling: know them for interviews (not practised)

- **Modules** (JPMS, 9): `module-info.java`, `requires`/`exports`. Spring Boot apps rarely use them; know what
  they are and that strong encapsulation is why some reflective libraries needed `--add-opens`.
- **`jshell`** (9): REPL. Good for trying the snippets in this file.
- **Single-file source launch** (11): `java Hello.java` without `javac`.
- **G1** is the default GC (9). **ZGC** (15; generational in 21): sub-millisecond pauses for large heaps.
- **Container awareness** (10): the JVM respects cgroup CPU/memory limits; `-XX:MaxRAMPercentage`.
- **Release cadence**: a release every 6 months, LTS every 2 years.

## 8. Preview / incubator in Java 22: not used here

Need `--enable-preview` and may change: **structured concurrency** (`StructuredTaskScope`), **scoped values**
(`ScopedValue`, the virtual-thread-friendly replacement for `ThreadLocal`), **stream gatherers**, **statements
before `super(...)`**, **implicitly declared classes**, **string templates** (withdrawn). Worth recognizing by name;
structured concurrency and scoped values come up in virtual-thread interviews.
