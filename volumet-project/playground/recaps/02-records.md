# W1 recap: Java records (+ sealed types and pattern matching)

Code: `playground/src/main/java/volumet/project/playground/records/`
Spec: `RoomSummaryTest` (9), `TagsTest` (6), `DateRangeTest` (6), `LoadOutcomeTest` (5), all 26 green.
Q&A: 49/90. Review list at the end (section 9).

---

## 1. What a record is

A **transparent, immutable data carrier**. Its state is the header and nothing else.

```java
public record RoomSummary(long id, String name, int size) { }
```

From the header alone, the compiler generates:

| Generated | Detail |
|---|---|
| one `private final` field per component | no setters, ever |
| the **canonical constructor** `(long, String, int)` | no no-arg constructor (you may add one, but it must delegate with `this(...)`) |
| `public` accessors `id()`, `name()`, `size()` | **no `get` prefix** (Jackson, MapStruct and SpEL `#dto.size()` all understand this) |
| `equals` / `hashCode` over **all** components | value equality, so records work as `Set`/`Map` keys |
| `toString` | `RoomSummary[id=1, name=Lab, size=20]` |

What a record **cannot** do:
- **extend a class**: it implicitly extends `java.lang.Record` and is `final` (it can implement interfaces)
- **declare extra instance fields**: derived values are methods (`DateRange.days()`); static fields are fine
- **have mutable fields**: a "change" returns a new record (a *wither*, `withSize`)

## 2. Compact canonical constructor: validate + normalize

```java
public RoomSummary {                              // no parameter list
    if (id <= 0) throw new IllegalArgumentException("id must be positive");
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
    name = name.strip();                           // reassign the PARAMETER
    if (size < 1) throw new IllegalArgumentException("size must be at least 1");
}                                                  // the compiler assigns the fields here, from the parameters
```

Rules:
- It runs **before** the fields are assigned. At the end, the compiler assigns `this.x = x` for every component,
  using the parameters **as they are then**. That's why reassigning `name` normalizes it.
- `this.name = name;` inside it does **not compile** (the field is final and assigned by the compiler).
- Reading `this.size` inside it does **not compile** ("might not have been initialized"). Read the **parameter** `size`.
- Other constructors must call `this(...)` first, so they go through it too (`RoomSummary(id, name)` → size 1).

**Why validation belongs here:** every way of creating an instance goes through the canonical constructor:
`new`, delegating constructors, withers, **Jackson**, MapStruct. So no instance that breaks the rules can exist.
- A `validate()` method can be forgotten.
- Bean Validation only runs where something triggers it (`@Valid` on a controller parameter). A record read back
  from Redis or from a message would skip it.
- Keep `@NotBlank` and similar on DTOs for nice 400 responses, but the invariants live in the constructor.

**Jackson (2.12+)** detects records, maps JSON properties to components by name and calls the canonical constructor.
An exception thrown there arrives wrapped (`ValueInstantiationException`), so the test checks the **root cause**.

`trim()` vs `strip()` (Java 11): `isBlank()` uses Unicode whitespace, `trim()` only removes characters ≤ U+0020.
Use `strip()` with `isBlank()` so validation and normalization agree.

## 3. Immutability is shallow

`final` protects the **reference**, not the object it points to.

| Way to store a `List` | Caller mutates its list afterwards | `values().add(..)` | Nulls |
|---|---|---|---|
| `this.values = values` | **record changes** | allowed | allowed |
| `Collections.unmodifiableList(values)` | **record changes** (it's a *view*) | throws | allowed |
| `List.copyOf(values)` (10) | record unchanged (it's a *copy*) | throws | **NPE** |

- `List.copyOf` returns the **same instance** when the input is already an immutable `List.of`/`copyOf` list (no copy cost).
- Gotcha: `List.of`/`copyOf` lists throw NPE on `contains(null)` / `indexOf(null)`. Guard with `tag != null && ...`
  if `contains(null)` should be `false`.
- A wither on a list: `new ArrayList<>(values)`, add, then `new Tags(copy)`. The constructor copies it back into
  an immutable list.

### Arrays: the classic trap

```java
record Payload(String id, byte[] data) { }
map.put(new Payload("a", new byte[]{1}), ..);
map.put(new Payload("a", new byte[]{1}), ..);   // TWO entries
```

The generated `equals` uses `Objects.equals(data, other.data)`. Arrays don't override `equals`/`hashCode`, so
they compare by **identity**. `toString` prints `[B@1b6d3586`.

Fixes, in order of preference:
1. Don't use an array: use an immutable type (`String` as hex/Base64, `List<Byte>`, a hash).
2. Override `equals`/`hashCode`/`toString` with `Arrays.equals`/`hashCode`/`toString`, **and** `clone()` the array
   in the constructor and in the accessor. Otherwise someone can change the bytes after the key is in a map, and
   the entry is lost.

## 4. Cross-field rules, factories, derived values (`DateRange`)

- Cross-field validation goes in the compact constructor (`from.isAfter(to)` → IAE).
- Static factories (`of`, `singleDay`) give alternative names; they call the canonical constructor.
- Derived values are methods, since no extra fields are allowed.

**The bug found in review:** `from.until(to).getDays() + 1`.
`until(LocalDate)` returns a **`Period`** (years / months / days), and `getDays()` is only the *days part*.
Jan 1 → Feb 1 is "1 month, 0 days", which gave **1** instead of 32. The original spec missed it; the test now
checks Jan 1..Feb 1 = 32 and Jan 1..Dec 31 = 365.

| Type | Meaning | Use |
|---|---|---|
| `Period` | calendar amount ("1 month 3 days") | adding to dates, human durations |
| `Duration` | exact time (seconds + nanos) | timeouts, TTLs |
| `ChronoUnit.DAYS.between(a, b)` | **total count** | "how many days" |

Inclusive ranges:
- `contains`: `!day.isBefore(from) && !day.isAfter(to)`
- `overlaps`: `!from.isAfter(other.to) && !other.from.isAfter(to)`

## 5. Sealed interfaces + records (`LoadOutcome`)

```java
public sealed interface LoadOutcome<T> permits Hit, Miss, Failed {
    record Hit<T>(T value)          implements LoadOutcome<T> { public Hit { Objects.requireNonNull(value); } }
    record Miss<T>()                implements LoadOutcome<T> { }
    record Failed<T>(Throwable cause) implements LoadOutcome<T> { public Failed { Objects.requireNonNull(cause); } }
}
```

- `sealed ... permits` lists **every** subtype. A `switch` over it can then be **exhaustive without `default`**.
- This combination is called an *algebraic data type* / *sum type*: "exactly one of these cases".
- Permitted subtypes must be `final`, `sealed` or `non-sealed`. Records are implicitly final.

**Why no `default`:** add `Expired<T>` to `permits` and every exhaustive switch **stops compiling**
("does not cover all possible input values"), pointing at each place to update. With a `default`, `Expired`
would silently land in the existing branch: a runtime bug nobody notices. If code compiled against the old
hierarchy meets a new subtype at runtime, the exhaustive switch throws **`MatchException`**. It still fails
loudly instead of misrouting.

## 6. Pattern matching for switch + record patterns (21)

```java
static String describe(LoadOutcome<?> outcome) {
    return switch (outcome) {                         // switch EXPRESSION: yields a value, no fall-through
        case Hit<?>(var value)    -> "hit: " + value;   // record pattern: deconstructs + binds
        case Miss<?>()            -> "miss";
        case Failed<?>(var cause) -> "failed: " + cause.getMessage();
    };
}

default T orElse(T fallback) {                        // no `throws` clause needed
    return switch (this) {
        case Hit<T>(T value)                -> value;
        case Miss<T>()                      -> fallback;
        case Failed<T>(RuntimeException e)  -> throw e;   // nested TYPE pattern: e is statically unchecked
        case Failed<T>(Error e)             -> throw e;
        case Failed<T>(var e)               -> throw new IllegalStateException(e);  // the rest: checked
    };
}
```

- **Order matters (dominance):** `Failed<T>(var e)` matches every `Failed`. Put it first and the later cases are
  "dominated by a preceding case label": a compile error, the same rule as `catch` blocks.
- **Why no `throws Throwable`:** `throw cause` with a `Throwable` static type forces the method to declare
  `throws Throwable`, and every caller must handle it. Inside `case Failed<T>(RuntimeException e)` the pattern
  gives `e` the static type `RuntimeException`, so the compiler knows `throw e` is unchecked. The only remaining
  checked case gets wrapped.
- A record pattern (`Hit<T>(var value)`) is better than a type pattern (`Hit<T> h -> h.value()`): it states
  what you need and needs no accessor call.

### Unnamed `_` (22)

```java
case Hit<?>(_)                 -> "hit";          // or Hit<?>(var _): component deliberately ignored
case Miss<?>(), Failed<?>(_)   -> "no value";     // several patterns in one label: only if none binds a variable
```

Why: it states the intent, can't be used by mistake, gives no unused-variable warning, and lets you **group
cases** (impossible with a named binding). Also works for unused lambda parameters and catch variables:
`catch (IOException _)`, `(_, v) -> v`.

## 7. Records vs JPA entities

A record **can't be a Hibernate entity**:
- it's `final`, so there's no **proxy subclass** for lazy loading
- its fields are `final`, so Hibernate can't populate, dirty-check, merge, assign a generated id after
  `persist`, or increment `@Version`
- it has no no-arg constructor (Hibernate instantiates first, then fills in the fields)
- equality is over **all** components, while an entity's identity is its **id**, and its state changes over time
  (this breaks `Set`s of entities and the persistence context)

Hibernate **does** support records as `@Embeddable` (6.2+) and as **query projections** (this project:
`@QueryProjection` on a compact constructor).

Where records shine in this project: DTOs, `KeySpace`/`VersionedKey` (keys), `VersionedValue<T>` (the cache
entry plus its version), `CoalescerExecutorSettings` (settings), sealed result types like `LoadOutcome`.

## 8. Java features in this lesson

| Feature | Version | Where | Java 8 equivalent |
|---|---|---|---|
| records | 16 | all four | class + fields + constructor + getters + equals/hashCode/toString (or Lombok `@Value`) |
| compact constructor | 16 | `RoomSummary`, `Tags`, `DateRange`, `Hit`, `Failed` | validation in a hand-written constructor |
| sealed interfaces | 17 | `LoadOutcome` | an abstract class with a private constructor, or nothing |
| switch expressions `->` | 14 | `describe`, `orElse` | `switch` statement + `break` + a variable |
| pattern matching for switch, record patterns | 21 | `describe`, `orElse` | `instanceof` + cast chains, the visitor pattern |
| unnamed `_` | 22 | Q8 | an unused named variable |
| `List.copyOf` | 10 | `Tags` | `Collections.unmodifiableList(new ArrayList<>(list))` |
| `String.isBlank` / `strip` | 11 | `RoomSummary` | `s.trim().isEmpty()` |
| text blocks `"""` | 15 | `RoomSummaryTest` | `"{\"id\":1,...}"` with escapes |
| `ChronoUnit.DAYS.between` | 8 | `DateRange` | (same; the trap is `Period.getDays()`) |

## 9. Q&A: model answers (review these)

| Q | Score | Topic |
|---|---|---|
| Q1 | 4 | what a record generates and can't do |
| Q2 | 5 | `copyOf` vs `unmodifiableList` |
| Q3 | 6 | sealed + no `default` |
| Q4 | **2** | `byte[]` component as a map key |
| Q5 | 5 | Jackson goes through the compact constructor |
| Q6 | 7 | case order, no `throws Throwable` |
| Q7 | 6 | records vs JPA entities |
| Q8 | 5 | unnamed `_` |
| Q9 | 9 | compact constructor assignment rules |

**Q1. What does a record generate, and what can't it do?**
> Private final fields, a canonical constructor, public accessors without a get prefix, and equals/hashCode/toString
> over all components. **No setters.** It's final and extends Record, so it can't extend a class; it can't have
> extra instance fields. Validation goes in a compact constructor.

**Q2. `Collections.unmodifiableList` instead of `List.copyOf`?**
> `unmodifiableList` is a **view**: the record can't change the list, but the caller still can, so the record changes
> and so do its equals/hashCode. `copyOf` makes an immutable copy. It also rejects nulls, and returns the same
> instance if the list is already immutable.

**Q3. A fourth case is added to the sealed interface.**
> Every exhaustive switch over it **stops compiling**, pointing at each place to update. With a `default`, the
> new case would silently go to the existing branch: a runtime bug. Old compiled code that meets the new
> subtype throws `MatchException`.

**Q4. `record Payload(String id, byte[] data)` as a map key.**
> **Two entries.** The generated equals calls `data.equals(..)`, and arrays use identity. Fix: avoid arrays (an
> immutable type), or override equals/hashCode/toString with `Arrays.*` and clone the array in the constructor and
> the accessor.

**Q5. Jackson and the compact constructor.**
> Jackson 2.12+ maps properties to components and calls the canonical constructor: there are no setters, and it
> doesn't write to final fields. Every construction path goes through that constructor, so no instance that
> breaks the rules can exist. A `validate()` can be forgotten; Bean Validation only runs where `@Valid` triggers it.

**Q6. Swap `Failed<T>(RuntimeException e)` and `Failed<T>(var e)`; why no `throws`?**
> (a) `var e` dominates, so the more specific case is unreachable: a compile error. Specific first, like catch blocks.
> (b) The nested type pattern gives `e` the static type `RuntimeException`/`Error`, so `throw e` is unchecked. The
> rest gets wrapped in `IllegalStateException`, so no checked exception can escape.

**Q7. Why can't a record be a Hibernate entity?**
> Final (no lazy-loading proxy), final fields (no hydration, dirty checking, generated id or `@Version` updates), no
> no-arg constructor, and all-component equality instead of id identity. Records work as `@Embeddable` (6.2+) and
> as projections.

**Q8. Ignoring a component.**
> `case Hit<?>(_)` (22). It states the intent, can't be misused, and lets you group patterns in one case label,
> which requires that none of them binds a variable.

**Q9. `this.name = name` in a compact constructor; reading `this.size`.**
> Neither compiles. The compiler assigns the fields after the body, from the (possibly reassigned) parameters.
> You can read the **parameter** `size`, not the field.

## 10. Interview one-liners

- "A record is a nominal tuple: state = header, equality = all components, immutable but **shallowly**."
- "Validate in the compact constructor: every construction path, including Jackson, goes through it."
- "`List.copyOf` copies, `unmodifiableList` is a view."
- "Arrays in records break equals: identity, not content."
- "Sealed + exhaustive switch turns a forgotten case into a compile error; `default` turns it into a runtime bug."
- "Records for values (DTOs, keys, events, results), classes for entities with identity and a lifecycle."
- "`Period.getDays()` is a field, not a total. Use `ChronoUnit.DAYS.between`."

## 11. Open items

- `Tags.with`: the comment "Singleton list is immutable" is out of date (the reason for the `ArrayList` is that
  `values` is unmodifiable).
- Optional: `Tags.contains(null)` throws NPE. Decide whether it should return `false`.
