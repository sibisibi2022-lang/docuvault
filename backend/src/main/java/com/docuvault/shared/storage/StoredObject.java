package com.docuvault.shared.storage;

/**
 * What the application keeps after handing a file to storage.
 *
 * @param key         opaque handle, meaningful only to the StorageService that issued it
 * @param sizeBytes   byte count as actually written
 * @param checksum    lowercase hex SHA-256 of the content
 */
public record StoredObject(String key, long sizeBytes, String checksum) {
}
