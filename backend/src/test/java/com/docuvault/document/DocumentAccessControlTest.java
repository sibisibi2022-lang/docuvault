package com.docuvault.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.docuvault.security.AuthenticatedUser;
import com.docuvault.shared.error.NotFoundException;
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

/**
 * Guards the broken-access-control fix.
 *
 * <p>In the original, {@code getAllDocuments()} returned every row to every
 * authenticated caller and no endpoint checked ownership, so any employee could
 * read, edit or delete any other employee's documents by guessing an id.
 */
class DocumentAccessControlTest extends IntegrationTest {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    private AuthenticatedUser alice;
    private AuthenticatedUser bob;
    private AuthenticatedUser manager;
    private Long aliceDocumentId;

    @BeforeEach
    void seed() {
        documentRepository.deleteAll();
        userRepository.deleteAll();

        alice = principal(persist("Alice", "alice@example.com", Role.EMPLOYEE));
        bob = principal(persist("Bob", "bob@example.com", Role.EMPLOYEE));
        manager = principal(persist("Mia", "mia@example.com", Role.MANAGER));

        aliceDocumentId = documentService.upload(
                alice,
                new DocumentDtos.UploadMetadata("Alice's report", "Private", null),
                file("report.pdf", "alice-content")).id();
    }

    @Test
    @DisplayName("an employee's list contains only their own documents")
    void listIsScopedToOwner() {
        documentService.upload(bob, new DocumentDtos.UploadMetadata("Bob's notes", null, null),
                file("notes.pdf", "bob-content"));

        var aliceSees = documentService.search(alice, null, null, null, PageRequest.of(0, 20));

        assertThat(aliceSees.getTotalElements()).isEqualTo(1);
        assertThat(aliceSees.getContent())
                .singleElement()
                .extracting(DocumentDtos.DocumentResponse::title)
                .isEqualTo("Alice's report");
    }

    @Test
    @DisplayName("reading someone else's document reports 404, not 403")
    void readingAnotherUsersDocumentIsNotFound() {
        // 404 rather than 403 on purpose: a 403 would confirm that the id exists,
        // which makes ids worth enumerating.
        assertThatThrownBy(() -> documentService.get(bob, aliceDocumentId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("an employee cannot delete a document they do not own")
    void deletingAnotherUsersDocumentIsRefused() {
        // 404, not 403: a 403 would confirm the id exists and let an employee
        // enumerate the workspace by probing deletes.
        assertThatThrownBy(() -> documentService.delete(bob, aliceDocumentId))
                .isInstanceOf(NotFoundException.class);

        assertThat(documentRepository.existsById(aliceDocumentId)).isTrue();
    }

    @Test
    @DisplayName("a manager sees and may act on every document")
    void managerSeesEverything() {
        documentService.upload(bob, new DocumentDtos.UploadMetadata("Bob's notes", null, null),
                file("notes.pdf", "bob-content"));

        assertThat(documentService.search(manager, null, null, null, PageRequest.of(0, 20)).getTotalElements())
                .isEqualTo(2);
        assertThat(documentService.get(manager, aliceDocumentId).title()).isEqualTo("Alice's report");
    }

    @Test
    @DisplayName("the first upload is recorded as version 1")
    void firstUploadCreatesVersionOne() {
        var versions = documentService.versions(alice, aliceDocumentId, PageRequest.of(0, 10));

        assertThat(versions.getTotalElements()).isEqualTo(1);
        assertThat(versions.getContent().get(0).versionNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("an unsupported file type is refused")
    void rejectsDisallowedContentType() {
        var executable = new MockMultipartFile(
                "file", "payload.sh", "application/x-sh", "#!/bin/sh".getBytes());

        assertThatThrownBy(() -> documentService.upload(
                alice, new DocumentDtos.UploadMetadata("Script", null, null), executable))
                .isInstanceOf(com.docuvault.shared.error.BadRequestException.class);
    }

    private User persist(String name, String email, Role role) {
        return userRepository.save(User.builder()
                .name(name)
                .email(email)
                .password("{noop}irrelevant-for-this-test")
                .role(role)
                .enabled(true)
                .build());
    }

    private AuthenticatedUser principal(User user) {
        return AuthenticatedUser.fromToken(user.getId(), user.getEmail(), user.getName(), user.getRole());
    }

    private MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "application/pdf", content.getBytes());
    }
}
