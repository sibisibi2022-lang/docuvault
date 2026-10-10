package com.docuvault.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class DocumentDtos {

    private DocumentDtos() {
    }

    /** Metadata supplied alongside a multipart upload. */
    public record UploadMetadata(
            @NotBlank(message = "Title is required")
            @Size(max = 200, message = "Title must be at most 200 characters")
            String title,

            @Size(max = 2000, message = "Description must be at most 2000 characters")
            String description,

            Long categoryId) {
    }

    public record UpdateRequest(
            @NotBlank(message = "Title is required")
            @Size(max = 200, message = "Title must be at most 200 characters")
            String title,

            @Size(max = 2000, message = "Description must be at most 2000 characters")
            String description,

            Long categoryId) {
    }

    /**
     * What the API returns for a document. Note it carries no {@code storageKey}:
     * the bucket path is an internal detail and exposing it would invite callers
     * to construct their own object URLs.
     */
    public record DocumentResponse(
            Long id,
            String title,
            String description,
            String fileName,
            String contentType,
            long sizeBytes,
            String checksum,
            Long ownerId,
            String ownerName,
            Long categoryId,
            String categoryName,
            int versionCount,
            Instant createdAt,
            Instant updatedAt) {

        static DocumentResponse from(Document document, int versionCount) {
            return new DocumentResponse(
                    document.getId(),
                    document.getTitle(),
                    document.getDescription(),
                    document.getFileName(),
                    document.getContentType(),
                    document.getSizeBytes(),
                    document.getChecksum(),
                    document.getOwner().getId(),
                    document.getOwner().getName(),
                    document.getCategory() == null ? null : document.getCategory().getId(),
                    document.getCategory() == null ? null : document.getCategory().getName(),
                    versionCount,
                    document.getCreatedAt(),
                    document.getUpdatedAt());
        }
    }

    public record VersionResponse(
            Long id,
            Long documentId,
            int versionNumber,
            String fileName,
            String contentType,
            long sizeBytes,
            String checksum,
            Long uploadedById,
            String uploadedByName,
            Instant createdAt) {

        static VersionResponse from(DocumentVersion version) {
            return new VersionResponse(
                    version.getId(),
                    version.getDocument().getId(),
                    version.getVersionNumber(),
                    version.getFileName(),
                    version.getContentType(),
                    version.getSizeBytes(),
                    version.getChecksum(),
                    version.getUploadedBy().getId(),
                    version.getUploadedBy().getName(),
                    version.getCreatedAt());
        }
    }

    /** Aggregates for the dashboard, computed in SQL rather than in Java. */
    public record DashboardStats(
            long documentCount,
            long totalBytes,
            long categoryCount,
            long userCount,
            java.util.List<FileTypeCount> byFileType) {
    }

    public record FileTypeCount(String contentType, long count) {
    }
}
