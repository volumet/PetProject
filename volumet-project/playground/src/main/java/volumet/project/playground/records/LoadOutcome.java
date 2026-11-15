package volumet.project.playground.records;

import java.util.Objects;

/**
 * W1 records, part 4: records + sealed interfaces + pattern matching (Java 21). YOUR IMPLEMENTATION GOES HERE.
 * <p>
 * The outcome of a cache load is exactly one of three cases. A {@code sealed} interface lists them all
 * ({@code permits}), so a {@code switch} over a {@code LoadOutcome} can be exhaustive without a {@code default}:
 * if someone adds a fourth case later, every such switch stops compiling instead of silently falling through.
 * <p>
 * Record patterns deconstruct a record inside {@code switch}/{@code instanceof}:
 * <pre>
 * switch (outcome) {
 *     case LoadOutcome.Hit&lt;?&gt;(var value) -> ...   // binds the component
 *     ...
 * }
 * </pre>
 * Rules (spec: {@code LoadOutcomeTest}):
 * <ul>
 *   <li>{@code Hit.value} must not be null (a missing value is a {@code Miss}): {@link NullPointerException};</li>
 *   <li>{@code Failed.cause} must not be null: {@link NullPointerException};</li>
 *   <li>implement {@link #describe} and {@link #orElse} with a {@code switch} using record patterns,
 *       and <b>no {@code default} branch</b>.</li>
 * </ul>
 */
public sealed interface LoadOutcome<T> permits LoadOutcome.Hit, LoadOutcome.Miss, LoadOutcome.Failed {

    record Hit<T>(T value) implements LoadOutcome<T> {
        public Hit {
            Objects.requireNonNull(value);
        }
    }

    record Miss<T>() implements LoadOutcome<T> {
    }

    record Failed<T>(Throwable cause) implements LoadOutcome<T> {
        public Failed {
            Objects.requireNonNull(cause);
        }
    }

    /** {@code "hit: <value>"}, {@code "miss"}, or {@code "failed: <cause message>"}. */
    static String describe(LoadOutcome<?> outcome) {
        return switch (outcome) {
            case Hit<?>(var value) -> "hit: " + value;
            case Miss<?>() -> "miss";
            case Failed<?>(var cause) -> "failed: " + cause.getMessage();
        };
    }

    /** The value for a hit, {@code fallback} for a miss, and the cause rethrown for a failure. */
    default T orElse(T fallback) {
        return switch (this) {
            case Hit<T>(T value) -> value;
            case Miss<T>() -> fallback;
            case Failed<T>(RuntimeException e) -> throw e;
            case Failed<T>(Error e) -> throw e;
            case Failed<T>(var e) -> throw new IllegalStateException(e);
        };

    }
}
