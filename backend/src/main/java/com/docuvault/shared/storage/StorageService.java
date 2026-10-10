package com.docuvault.shared.storage;

import java.io.InputStream;

/**
 * The port the domain talks to. Two adapters implement it: a local directory for
 * development and an S3-compatible bucket for production.
 *
 * <p>This interface is the reason the app can be deployed to a host with an
 * ephemeral filesystem. The original wrote to {@code uploads/} with a relative
 * path, so every container restart silently lost every file.
 */
public interface StorageService {

    /**
     * Persists {@code content} and returns its handle.
     *
     * @param originalFileName used only to derive a readable suffix; never trusted as a path
     */
    StoredObject store(InputStream content, String originalFileName, String contentType);

    /** Opens a stored object for reading. */
    InputStream retrieve(String key);

    /** Removes a stored object. Must not throw if the key is already gone. */
    void delete(String key);
}
