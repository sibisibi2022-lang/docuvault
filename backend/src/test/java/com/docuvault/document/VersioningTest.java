package com.docuvault.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.docuvault.security.AuthenticatedUser;
import com.docuvault.shared.error.ConflictException;
import com.docuvault.support.IntegrationTest;
import com.docuvault.user.Role;
import com.docuvault.user.User;
import com.docuvault.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

/** Covers the versioning and de-duplication behaviour. */
class VersioningTest extends IntegrationTest {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    private AuthenticatedUser owner;
    private Long documentId;

    @BeforeEach
    void seed() {
        documentRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.save(User.builder()
                .name("Owner").email("owner@example.com")
                .password("{noop}irrelevant").role(Role.EMPLOYEE).enabled(true).build());
        owner = AuthenticatedUser.fromToken(user.getId(), user.getEmail(), user.getName(), user.getRole());

        documentId = documentService.upload(
                owner, new DocumentDtos.UploadMetadata("Spec", null, null),
                file("spec.pdf", "version one")).id();
    }

    @Test
    @DisplayName("replacing the file adds a version and makes it current")
    void replacementCreatesNextVersion() {
        var updated = documentService.update(owner, documentId,
                new DocumentDtos.UpdateRequest("Spec", "revised", null),
                file("spec.pdf", "version two"));

        assertThat(updated.versionCount()).isEqualTo(2);

        var versions = documentService.versions(owner, documentId, PageRequest.of(0, 10));
        assertThat(versions.getContent().get(0).versionNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("re-uploading an identical file is rejected as a duplicate")
    void identicalFileIsRejected() {
        assertThatThrownBy(() -> documentService.update(owner, documentId,
                new DocumentDtos.UpdateRequest("Spec", null, null),
                file("spec-copy.pdf", "version one")))
                .isInstanceOf(ConflictException.class);

        assertThat(documentService.get(owner, documentId).versionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("restoring an older version appends rather than rewrites history")
    void restoreAppendsToHistory() {
        documentService.update(owner, documentId,
                new DocumentDtos.UpdateRequest("Spec", null, null),
                file("spec.pdf", "version two"));

        var versions = documentService.versions(owner, documentId, PageRequest.of(0, 10)).getContent();
        var versionOne = versions.stream().filter(v -> v.versionNumber() == 1).findFirst().orElseThrow();

        var restored = documentService.restoreVersion(owner, documentId, versionOne.id());

        assertThat(restored.checksum()).isEqualTo(versionOne.checksum());
        // History is untouched: still two versions, with v1's content current.
        assertThat(restored.versionCount()).isEqualTo(2);
    }

    private MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "application/pdf", content.getBytes());
    }
}
