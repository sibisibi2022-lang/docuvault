package com.docuvault.shared.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Stores files in any S3-compatible bucket: Cloudflare R2, Supabase Storage,
 * MinIO or AWS S3 itself.
 *
 * <p>This is the production adapter. Object storage is what keeps uploads alive
 * across the restarts and redeploys that free hosting tiers do constantly, and
 * it keeps blobs out of the 0.5 GB Postgres allowance.
 */
public class S3StorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final S3Client client;
    private final String bucket;

    public S3StorageService(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
        log.info("S3-compatible object storage active for bucket {}", bucket);
    }

    @Override
    public StoredObject store(InputStream content, String originalFileName, String contentType) {
        String key = StorageKeys.generate(originalFileName);

        // S3 needs the content length up front, which a raw multipart stream
        // cannot supply. Spooling to a temp file keeps peak heap flat regardless
        // of upload size; the alternative is holding the whole file in memory.
        Path spool = null;
        try {
            spool = Files.createTempFile("docuvault-upload-", ".tmp");

            String checksum;
            long size;
            try (DigestingInputStream digesting = new DigestingInputStream(content)) {
                size = Files.copy(digesting, spool, StandardCopyOption.REPLACE_EXISTING);
                checksum = digesting.checksum();
            }

            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength(size)
                            .build(),
                    RequestBody.fromFile(spool));

            return new StoredObject(key, size, checksum);

        } catch (IOException | S3Exception e) {
            throw new StorageException("Failed to store file " + key, e);
        } finally {
            deleteQuietly(spool);
        }
    }

    @Override
    public InputStream retrieve(String key) {
        try {
            return client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (NoSuchKeyException e) {
            throw new StorageException("Stored file is missing: " + key, e);
        } catch (S3Exception e) {
            throw new StorageException("Failed to read file " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (S3Exception e) {
            log.warn("Could not delete stored object {}", key, e);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not remove upload spool file {}", path, e);
        }
    }
}
