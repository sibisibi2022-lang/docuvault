package com.docuvault.shared.config;

import com.docuvault.shared.storage.LocalStorageService;
import com.docuvault.shared.storage.S3StorageService;
import com.docuvault.shared.storage.StorageService;
import java.net.URI;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Chooses the storage adapter from configuration. Swapping local for S3 is an
 * environment variable, not a code change.
 */
@Configuration
public class StorageConfig {

    @Bean
    public StorageService storageService(AppProperties properties) {
        AppProperties.Storage storage = properties.storage();

        if ("s3".equalsIgnoreCase(storage.type())) {
            AppProperties.Storage.S3 s3 = storage.s3();
            requireConfigured(s3);
            return new S3StorageService(buildClient(s3), s3.bucket());
        }

        String directory = storage.localDirectory() == null ? "./storage" : storage.localDirectory();
        return new LocalStorageService(Path.of(directory));
    }

    private S3Client buildClient(AppProperties.Storage.S3 s3) {
        var builder = S3Client.builder()
                .region(Region.of(s3.region() == null || s3.region().isBlank() ? "us-east-1" : s3.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.accessKey(), s3.secretKey())));

        if (s3.endpoint() != null && !s3.endpoint().isBlank()) {
            // Non-AWS providers (R2, Supabase, MinIO) need a custom endpoint and
            // path-style addressing — virtual-host style assumes AWS DNS.
            builder.endpointOverride(URI.create(s3.endpoint()))
                   .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }

        return builder.build();
    }

    private void requireConfigured(AppProperties.Storage.S3 s3) {
        if (s3 == null || isBlank(s3.bucket()) || isBlank(s3.accessKey()) || isBlank(s3.secretKey())) {
            throw new IllegalStateException(
                    "STORAGE_TYPE=s3 requires S3_BUCKET, S3_ACCESS_KEY and S3_SECRET_KEY to be set");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
