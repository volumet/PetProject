package volumet.project.playground.records;

import java.util.ArrayList;
import java.util.List;

/**
 * W1 records, part 2: immutability is shallow. YOUR IMPLEMENTATION GOES HERE.
 * <p>
 * A record's fields are final, but a final field pointing to a mutable {@code List} still lets anyone change the
 * list, both the caller who passed it in and the caller who reads {@link #values()}. Records give you no
 * protection here: you add it yourself.
 * <p>
 * Rules (spec: {@code TagsTest}):
 * <ul>
 *   <li>{@code values} must not be null and must not contain null elements ({@link NullPointerException});</li>
 *   <li>changing the list passed to the constructor afterwards must not change this record;</li>
 *   <li>the list returned by {@link #values()} must be unmodifiable;</li>
 *   <li>two {@code Tags} with the same values in the same order are equal.</li>
 * </ul>
 * Hint: one JDK method does the copy, the unmodifiable wrapper and the null check at once.
 */
public record Tags(List<String> values) {

    public Tags {
        values = List.copyOf(values);
    }

    /** An empty set of tags. */
    public static Tags none() {
        return new Tags(List.of());
    }

    /** {@code true} if the tag is present (exact match). */
    public boolean contains(String tag) {
        return tag != null && values.contains(tag);
    }

    /** A new {@code Tags} with {@code tag} appended; {@code this} is unchanged. */
    public Tags with(String tag) {
        List<String> copied = new ArrayList<>(values);
        copied.add(tag);
        return new Tags(copied);
    }
}
