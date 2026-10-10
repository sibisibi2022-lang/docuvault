package com.docuvault.shared.storage;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes the SHA-256 of a stream as it is consumed.
 *
 * <p>Lets the caller hash and write in a single pass instead of buffering the
 * whole upload to hash it and then buffering it again to store it.
 */
final class DigestingInputStream extends FilterInputStream {

    private final MessageDigest digest;

    DigestingInputStream(InputStream delegate) {
        this(delegate, newDigest());
    }

    private DigestingInputStream(InputStream delegate, MessageDigest digest) {
        super(new DigestInputStream(delegate, digest));
        this.digest = digest;
    }

    /** Valid only once the stream has been read to the end. */
    String checksum() {
        return HexFormat.of().formatHex(digest.digest());
    }

    @Override
    public void close() throws IOException {
        super.close();
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }
}
