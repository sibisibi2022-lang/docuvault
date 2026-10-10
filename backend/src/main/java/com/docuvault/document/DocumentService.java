package com.docuvault.document;

import com.docuvault.category.Category;
import com.docuvault.category.CategoryRepository;
import com.docuvault.security.AuthenticatedUser;
import com.docuvault.shared.audit.AuditAction;
import com.docuvault.shared.audit.AuditService;
import com.docuvault.shared.config.AppProperties;
import com.docuvault.shared.error.BadRequestException;
import com.docuvault.shared.error.ConflictException;
import com.docuvault.shared.error.NotFoundException;
import com.docuvault.shared.storage.StorageService;
import com.docuvault.shared.storage.StoredObject;
import com.docuvault.user.User;
import com.docuvault.user.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * All document behaviour, including the access rules.
 *
 * <p>Access model: an EMPLOYEE may read and change only documents they own;
 * ADMIN and MANAGER act across the whole workspace. Every public method that
 * touches a specific document runs that check — the URL-pattern rules in
 * {@code SecurityConfig} cannot express "owns this row", so relying on them
 * alone (as the original did) left every document readable by every account.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final AuditService auditService;
    private final Set<String> allowedContentTypes;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentVersionRepository versionRepository,
                           CategoryRepository categoryRepository,
                           UserRepository userRepository,
                           StorageService storageService,
                           AuditService auditService,
                           AppProperties properties) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.auditService = auditService;
        this.allowedContentTypes = properties.upload().allowedContentTypes();
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public Page<DocumentDtos.DocumentResponse> search(AuthenticatedUser caller,
                                                      String query,
                                                      Long categoryId,
                                                      String contentType,
                                                      Pageable pageable) {
        // The ownership rule becomes a SQL predicate rather than a post-filter,
        // so pagination counts stay correct for employees.
        Long ownerScope = caller.canActOnAnyDocument() ? null : caller.id();

        return documentRepository
                .search(ownerScope, categoryId, contentType, blankToNull(query), pageable)
                .map(document -> DocumentDtos.DocumentResponse.from(
                        document, (int) versionRepository.countByDocumentId(document.getId())));
    }

    @Transactional(readOnly = true)
    public DocumentDtos.DocumentResponse get(AuthenticatedUser caller, Long id) {
        Document document = loadReadable(caller, id);
        return DocumentDtos.DocumentResponse.from(
                document, (int) versionRepository.countByDocumentId(id));
    }

    @Transactional(readOnly = true)
    public Page<DocumentDtos.VersionResponse> versions(AuthenticatedUser caller, Long documentId, Pageable pageable) {
        loadReadable(caller, documentId);
        return versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId, pageable)
                .map(DocumentDtos.VersionResponse::from);
    }

    @Transactional(readOnly = true)
    public DocumentDtos.DashboardStats dashboard(AuthenticatedUser caller) {
        Long ownerScope = caller.canActOnAnyDocument() ? null : caller.id();

        var byFileType = documentRepository.countByContentType(ownerScope).stream()
                .map(row -> new DocumentDtos.FileTypeCount(row.getContentType(), row.getCount()))
                .toList();

        return new DocumentDtos.DashboardStats(
                ownerScope == null ? documentRepository.count() : documentRepository.countByOwnerId(ownerScope),
                documentRepository.totalBytesStored(ownerScope),
                categoryRepository.count(),
                caller.canActOnAnyDocument() ? userRepository.count() : 1,
                byFileType);
    }

    // ----------------------------------------------------------------- writes

    @Transactional
    public DocumentDtos.DocumentResponse upload(AuthenticatedUser caller,
                                                DocumentDtos.UploadMetadata metadata,
                                                MultipartFile file) {
        validateUpload(file);

        User owner = requireUser(caller.id());
        Category category = resolveCategory(metadata.categoryId());
        StoredObject stored = store(file);

        Document document = Document.builder()
                .title(metadata.title().trim())
                .description(trimToNull(metadata.description()))
                .fileName(originalName(file))
                .contentType(file.getContentType())
                .sizeBytes(stored.sizeBytes())
                .storageKey(stored.key())
                .checksum(stored.checksum())
                .owner(owner)
                .category(category)
                .build();

        documentRepository.save(document);

        // The first upload is version 1, so history is complete from the start.
        // The original only began tracking versions on the first *replacement*,
        // which meant the original file was never recoverable.
        versionRepository.save(DocumentVersion.builder()
                .document(document)
                .versionNumber(1)
                .fileName(document.getFileName())
                .contentType(document.getContentType())
                .sizeBytes(document.getSizeBytes())
                .storageKey(document.getStorageKey())
                .checksum(document.getChecksum())
                .uploadedBy(owner)
                .build());

        auditService.record(AuditAction.DOCUMENT_UPLOADED, owner, document.getId(),
                "Uploaded \"" + document.getTitle() + "\"");
        log.info("User {} uploaded document {}", caller.id(), document.getId());

        return DocumentDtos.DocumentResponse.from(document, 1);
    }

    @Transactional
    public DocumentDtos.DocumentResponse update(AuthenticatedUser caller,
                                                Long id,
                                                DocumentDtos.UpdateRequest request,
                                                MultipartFile replacementFile) {
        Document document = loadWritable(caller, id);
        User actor = requireUser(caller.id());

        document.setTitle(request.title().trim());
        document.setDescription(trimToNull(request.description()));
        document.setCategory(resolveCategory(request.categoryId()));

        if (replacementFile != null && !replacementFile.isEmpty()) {
            addVersion(document, actor, replacementFile);
        }

        auditService.record(AuditAction.DOCUMENT_UPDATED, actor, document.getId(),
                "Updated \"" + document.getTitle() + "\"");

        return DocumentDtos.DocumentResponse.from(
                document, (int) versionRepository.countByDocumentId(id));
    }

    /**
     * Stores a replacement file as the next version and points the document at it.
     *
     * <p>De-duplication compares the new checksum against the stored checksums
     * for this document — an indexed lookup. The original downloaded and
     * re-hashed every previous version on every upload, so a document with
     * twenty versions re-read twenty files to accept the twenty-first.
     */
    private void addVersion(Document document, User actor, MultipartFile file) {
        validateUpload(file);

        StoredObject stored = store(file);

        if (versionRepository.existsByDocumentIdAndChecksum(document.getId(), stored.checksum())) {
            // Roll the orphan back out of storage before refusing the request;
            // the transaction will not clean up the bucket for us.
            storageService.delete(stored.key());
            throw new ConflictException("That exact file is already in this document's version history");
        }

        int nextVersion = versionRepository.highestVersionNumber(document.getId()) + 1;

        document.setFileName(originalName(file));
        document.setContentType(file.getContentType());
        document.setSizeBytes(stored.sizeBytes());
        document.setStorageKey(stored.key());
        document.setChecksum(stored.checksum());

        versionRepository.save(DocumentVersion.builder()
                .document(document)
                .versionNumber(nextVersion)
                .fileName(document.getFileName())
                .contentType(document.getContentType())
                .sizeBytes(document.getSizeBytes())
                .storageKey(document.getStorageKey())
                .checksum(document.getChecksum())
                .uploadedBy(actor)
                .build());

        auditService.record(AuditAction.VERSION_CREATED, actor, document.getId(),
                "Created version " + nextVersion);
    }

    /** Makes an earlier version current again, as a new version rather than a rewrite. */
    @Transactional
    public DocumentDtos.DocumentResponse restoreVersion(AuthenticatedUser caller, Long documentId, Long versionId) {
        Document document = loadWritable(caller, documentId);
        User actor = requireUser(caller.id());

        DocumentVersion source = versionRepository.findWithDocumentById(versionId)
                .orElseThrow(() -> new NotFoundException("Version", versionId));

        if (!source.getDocument().getId().equals(documentId)) {
            throw new BadRequestException("That version belongs to a different document");
        }

        // History is append-only: restoring points the document back at an older
        // blob and records the move, so nothing in the trail is ever rewritten.
        document.setFileName(source.getFileName());
        document.setContentType(source.getContentType());
        document.setSizeBytes(source.getSizeBytes());
        document.setStorageKey(source.getStorageKey());
        document.setChecksum(source.getChecksum());

        auditService.record(AuditAction.VERSION_RESTORED, actor, documentId,
                "Restored version " + source.getVersionNumber());

        return DocumentDtos.DocumentResponse.from(
                document, (int) versionRepository.countByDocumentId(documentId));
    }

    @Transactional
    public void delete(AuthenticatedUser caller, Long id) {
        Document document = loadWritable(caller, id);
        User actor = requireUser(caller.id());
        String title = document.getTitle();

        // Collect the keys before the rows go, then delete the blobs. Doing it
        // in this order means a storage failure leaves orphaned objects rather
        // than rows pointing at files that no longer exist.
        var keys = versionRepository.findByDocumentId(id).stream()
                .map(DocumentVersion::getStorageKey)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        keys.add(document.getStorageKey());

        documentRepository.delete(document);
        documentRepository.flush();

        keys.forEach(storageService::delete);

        auditService.record(AuditAction.DOCUMENT_DELETED, actor, null, "Deleted \"" + title + "\"");
        log.info("User {} deleted document {}", caller.id(), id);
    }

    // -------------------------------------------------------------- downloads

    /** A document's current file, as a stream the controller pipes to the client. */
    @Transactional(readOnly = true)
    public DownloadPayload download(AuthenticatedUser caller, Long id) {
        Document document = loadReadable(caller, id);
        return new DownloadPayload(
                storageService.retrieve(document.getStorageKey()),
                document.getFileName(),
                document.getContentType(),
                document.getSizeBytes());
    }

    @Transactional(readOnly = true)
    public DownloadPayload downloadVersion(AuthenticatedUser caller, Long versionId) {
        DocumentVersion version = versionRepository.findWithDocumentById(versionId)
                .orElseThrow(() -> new NotFoundException("Version", versionId));

        requireReadAccess(caller, version.getDocument());

        return new DownloadPayload(
                storageService.retrieve(version.getStorageKey()),
                version.getFileName(),
                version.getContentType(),
                version.getSizeBytes());
    }

    public record DownloadPayload(InputStream content, String fileName, String contentType, long sizeBytes) {
    }

    // ----------------------------------------------------------- access rules

    private Document loadReadable(AuthenticatedUser caller, Long id) {
        Document document = documentRepository.findWithRelationsById(id)
                .orElseThrow(() -> new NotFoundException("Document", id));
        requireReadAccess(caller, document);
        return document;
    }

    /**
     * Loads a document the caller may change.
     *
     * <p>Reports 404 rather than 403 for the same reason the read path does. A
     * 403 here would confirm the id exists, so an employee could enumerate the
     * whole workspace by issuing deletes and watching which ids answer 403
     * instead of 404. Read and write access coincide in this model — an
     * employee may change exactly what they may see — so the two paths answer
     * identically.
     */
    private Document loadWritable(AuthenticatedUser caller, Long id) {
        Document document = documentRepository.findWithRelationsById(id)
                .orElseThrow(() -> new NotFoundException("Document", id));
        requireReadAccess(caller, document);
        return document;
    }

    private void requireReadAccess(AuthenticatedUser caller, Document document) {
        if (!caller.canActOnAnyDocument() && !document.isOwnedBy(caller.id())) {
            // 404 rather than 403 on a read: telling an employee "that document
            // exists but is not yours" leaks the existence of other people's
            // documents and makes ids worth enumerating.
            throw new NotFoundException("Document", document.getId());
        }
    }

    // ------------------------------------------------------------- validation

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("A file is required");
        }

        String contentType = file.getContentType();
        if (contentType == null || !allowedContentTypes.contains(contentType)) {
            // An allowlist, not a blocklist: anything not explicitly permitted is
            // refused, so an unanticipated type cannot slip through.
            throw new BadRequestException("Files of type " + contentType + " are not accepted");
        }
    }

    private StoredObject store(MultipartFile file) {
        try (InputStream content = file.getInputStream()) {
            return storageService.store(content, originalName(file), file.getContentType());
        } catch (IOException e) {
            throw new BadRequestException("The uploaded file could not be read");
        }
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category", categoryId));
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User", id));
    }

    private String originalName(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name == null || name.isBlank() ? "file" : name;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
