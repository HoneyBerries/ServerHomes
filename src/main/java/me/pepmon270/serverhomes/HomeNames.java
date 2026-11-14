package me.pepmon270.serverhomes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/** Validation and normalization of home names. */
public final class HomeNames {

    /** Maximum allowed length of a home name. */
    public static final int MAX_LENGTH = 32;

    /** Pattern a normalized home name must fully match: 1 to {@link #MAX_LENGTH} characters from {@code [a-z0-9_-]}. */
    private static final Pattern VALID = Pattern.compile("[a-z0-9_-]{1," + MAX_LENGTH + "}");

    /**
     * Private constructor to prevent instantiation; this class only holds static members.
     */
    private HomeNames() {
    }

    /**
     * Lowercases a name and checks it contains only letters, digits, {@code _} and {@code -}.
     *
     * @param raw the name as typed by the player
     * @return the normalized name, or {@code null} if it is invalid
     */
    public static @Nullable String normalize(@NotNull String raw) {
        String name = raw.toLowerCase(Locale.ROOT);
        return VALID.matcher(name).matches() ? name : null;
    }
}
