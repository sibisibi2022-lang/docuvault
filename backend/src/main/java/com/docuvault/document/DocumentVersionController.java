package com.docuvault.document;

import com.docuvault.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Downloading a specific historical version. */
@RestController
@RequestMapping("/api/document-versions")
@Tag(name = "Document versions")
public class DocumentVersionController {

    private final DocumentService documentService;

    public DocumentVersionController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/{versionId}/download")
    public ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal AuthenticatedUser caller,
                                                        @PathVariable Long versionId) {
        DocumentService.DownloadPayload payload = documentService.downloadVersion(caller, versionId);

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
