package me.pepmon270.serverhomes;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/** Validation and normalization of home names. */
final class HomeNames {

    /** Maximum allowed length of a home name. */
    static final int MAX_LENGTH = 32;

    private static final Pattern VALID = Pattern.compile("[a-z0-9_-]{1," + MAX_LENGTH + "}");

    private HomeNames() {
    }

    /**
     * Lowercases a name and checks it contains only letters, digits, {@code _} and {@code -}.
     *
     * @param raw the name as typed by the player
     * @return the normalized name, or {@code null} if it is invalid
     */
    static @Nullable String normalize(String raw) {
        String name = raw.toLowerCase(Locale.ROOT);
        return VALID.matcher(name).matches() ? name : null;
    }
}
