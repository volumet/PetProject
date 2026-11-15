package volumet.project.playground.records;

import java.util.Objects;

/**
 * W1 records, part 1: the basics. YOUR IMPLEMENTATION GOES HERE.
 * <p>
 * A record is a transparent, immutable data carrier. From the header alone the compiler generates:
 * private final fields, a canonical constructor {@code (long, String, int)}, accessors {@code id()},
 * {@code name()}, {@code size()} (no "get" prefix), and {@code equals}/{@code hashCode}/{@code toString} based on
 * ALL components.
 * <p>
 * Rules for this record (spec: {@code RoomSummaryTest}):
 * <ul>
 *   <li>{@code id} must be positive;</li>
 *   <li>{@code name} must not be null or blank, and is stored trimmed ({@code "  Lab  "} becomes {@code "Lab"});</li>
 *   <li>{@code size} must be at least 1;</li>
 *   <li>violations throw {@link IllegalArgumentException} ({@link NullPointerException} for a null name).</li>
 * </ul>
 * Hint: a <b>compact canonical constructor</b> ({@code public RoomSummary { ... }}, no parameter list) runs before
 * the fields are assigned. You can validate there, and you can also reassign a parameter
 * ({@code name = name.trim();}) to normalize it.
 */
public record RoomSummary(long id, String name, int size) {

    private static final int DEFAULT_SIZE = 1;

    public RoomSummary {
        if (id <= 0) {
            throw new IllegalArgumentException("Id must be positive");
        }

        Objects.requireNonNull(name);
        if (name.isBlank()) {
            throw new IllegalArgumentException("Name must not be blank");
        }

        name = name.strip();

        if (size < 1) {
            throw new IllegalArgumentException("Size must be at least 1");
        }
    }

    /**
     * Convenience constructor: a room of size 1.
     * Every non-canonical constructor must delegate to the canonical one with {@code this(...)} first.
     */
    public RoomSummary(long id, String name) {
        this(id, name, DEFAULT_SIZE);
    }

    /**
     * A "wither": records are immutable, so "changing" a component means returning a new record.
     * The original must stay unchanged.
     */
    public RoomSummary withSize(int newSize) {
        return new RoomSummary(id, name, newSize);
    }
}
