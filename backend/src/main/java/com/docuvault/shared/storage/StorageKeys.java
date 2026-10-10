package com.docuvault.shared.storage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** Builds storage keys that are unique, sortable and safe to use as a path. */
final class StorageKeys {

    private static final DateTimeFormatter DATE_PREFIX = DateTimeFormatter.ofPattern("yyyy/MM");
    private static final int MAX_NAME_LENGTH = 80;

    private StorageKeys() {
    }

    /**
     * Produces a key of the form {@code 2026/10/<uuid>-<sanitised-name>}.
     *
     * <p>The UUID guarantees uniqueness, so two users uploading "report.pdf" on
     * the same day cannot collide. The sanitised original name is kept only to
     * make the bucket browsable by a human.
     */
    static String generate(String originalFileName) {
        String safeName = sanitise(originalFileName);
        return "%s/%s-%s".formatted(LocalDate.now().format(DATE_PREFIX), UUID.randomUUID(), safeName);
    }

    /**
     * Strips everything that could escape the storage root or confuse a shell:
     * path separators, traversal sequences, control characters and unicode
     * lookalikes all collapse to underscores.
     */
    private static String sanitise(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "file";
        }

        // Take the last path segment first: a browser on Windows may submit a
        // full "C:\Users\me\report.pdf" as the original filename.
        String name = originalFileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        name = name.replaceAll("_{2,}", "_").replaceAll("^[._-]+", "");

        if (name.isBlank()) {
            return "file";
        }
        return name.length() > MAX_NAME_LENGTH ? name.substring(name.length() - MAX_NAME_LENGTH) : name;
    }
}
