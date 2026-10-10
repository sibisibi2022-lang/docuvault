package com.docuvault.document;

import com.docuvault.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    @Operation(summary = "List documents the caller may see",
            description = "Employees see only their own documents; admins and managers see all of them.")
    public Page<DocumentDtos.DocumentResponse> list(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String contentType,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return documentService.search(caller, query, categoryId, contentType, pageable);
    }

    @GetMapping("/stats")
    public DocumentDtos.DashboardStats stats(@AuthenticationPrincipal AuthenticatedUser caller) {
        return documentService.dashboard(caller);
    }

    @GetMapping("/{id}")
    public DocumentDtos.DocumentResponse get(@AuthenticationPrincipal AuthenticatedUser caller,
                                             @PathVariable Long id) {
        return documentService.get(caller, id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a new document (becomes version 1)")
    public DocumentDtos.DocumentResponse upload(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestPart("metadata") DocumentDtos.UploadMetadata metadata,
            @RequestPart("file") MultipartFile file) {
        return documentService.upload(caller, metadata, file);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update metadata, optionally replacing the file",
            description = "Supplying a file creates a new version; an identical file is rejected as a duplicate.")
    public DocumentDtos.DocumentResponse update(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable Long id,
            @Valid @RequestPart("metadata") DocumentDtos.UpdateRequest metadata,
            @RequestPart(name = "file", required = false) MultipartFile file) {
        return documentService.update(caller, id, metadata, file);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id) {
        documentService.delete(caller, id);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal AuthenticatedUser caller,
                                                        @PathVariable Long id) {
        return streamed(documentService.download(caller, id));
    }

    @GetMapping("/{id}/versions")
    public Page<DocumentDtos.VersionResponse> versions(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable Long id,
            @PageableDefault(size = 20) Pageable pageable) {
        return documentService.versions(caller, id, pageable);
    }

    @PostMapping("/{id}/versions/{versionId}/restore")
    @Operation(summary = "Make an earlier version current again")
    public DocumentDtos.DocumentResponse restore(@AuthenticationPrincipal AuthenticatedUser caller,
                                                 @PathVariable Long id,
                                                 @PathVariable Long versionId) {
        return documentService.restoreVersion(caller, id, versionId);
    }

    /**
     * Streams the stored object straight through to the client.
     *
     * <p>The filename is carried in a {@code ContentDisposition} builder rather
     * than a hand-built header string, which handles the quoting and the UTF-8
     * encoding that a name like "rapport final.pdf" needs.
     */
    private ResponseEntity<InputStreamResource> streamed(DocumentService.DownloadPayload payload) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(payload.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .contentLength(payload.sizeBytes())
                .body(new InputStreamResource(payload.content()));
    }
}
