package com.docuvault.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.docuvault.category.Category;
import com.docuvault.category.CategoryRepository;
import com.docuvault.security.AuthenticatedUser;
import com.docuvault.support.IntegrationTest;
import com.docuvault.user.Role;
import com.docuvault.user.User;
import com.docuvault.user.UserRepository;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

/** Covers the bulk endpoints, including their per-row access rules. */
class BulkActionsTest extends IntegrationTest {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository versionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    private AuthenticatedUser alice;
    private AuthenticatedUser bob;
    private Long aliceOne;
    private Long aliceTwo;
    private Long bobsDocument;
    private Long categoryId;

    @BeforeEach
    void seed() {
        documentRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        alice = principal(persist("Alice", "alice@example.com"));
        bob = principal(persist("Bob", "bob@example.com"));

        categoryId = categoryRepository.save(Category.builder().name("Reports").build()).getId();

        aliceOne = upload(alice, "one", "alice one");
        aliceTwo = upload(alice, "two", "alice two");
        bobsDocument = upload(bob, "bob", "bob content");
    }

    @Test
    @DisplayName("bulk delete removes the caller's documents and their versions")
    void bulkDeleteRemovesOwnDocuments() {
        var result = documentService.bulkDelete(alice, List.of(aliceOne, aliceTwo));

        assertThat(result.requested()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(2);
        assertThat(result.failures()).isEmpty();
        assertThat(documentRepository.existsById(aliceOne)).isFalse();
        assertThat(versionRepository.countByDocumentId(aliceOne)).isZero();
    }

    @Test
    @DisplayName("bulk delete skips documents the caller does not own, without losing the rest")
    void bulkDeleteSkipsForeignDocuments() {
        // The whole point of reporting per-id failures: one id Alice cannot
        // touch must not roll back the two she can.
        var result = documentService.bulkDelete(alice, List.of(aliceOne, bobsDocument, aliceTwo));

        assertThat(result.requested()).isEqualTo(3);
        assertThat(result.succeeded()).isEqualTo(2);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).id()).isEqualTo(bobsDocument);

        assertThat(documentRepository.existsById(aliceOne)).isFalse();
        assertThat(documentRepository.existsById(aliceTwo)).isFalse();
        assertThat(documentRepository.existsById(bobsDocument)).isTrue();
    }

    @Test
    @DisplayName("duplicate ids in one request are collapsed")
    void bulkDeleteDeduplicatesIds() {
        var result = documentService.bulkDelete(alice, List.of(aliceOne, aliceOne, aliceOne));

        assertThat(result.requested()).isEqualTo(1);
        assertThat(result.succeeded()).isEqualTo(1);
    }

    @Test
    @DisplayName("bulk move files the caller's documents under one category")
    void bulkMoveSetsCategory() {
        var result = documentService.bulkMoveToCategory(alice, List.of(aliceOne, aliceTwo), categoryId);

        assertThat(result.succeeded()).isEqualTo(2);
        assertThat(documentService.get(alice, aliceOne).categoryName()).isEqualTo("Reports");
    }

    @Test
    @DisplayName("bulk move with a null category clears it")
    void bulkMoveClearsCategory() {
        documentService.bulkMoveToCategory(alice, List.of(aliceOne), categoryId);
        documentService.bulkMoveToCategory(alice, List.of(aliceOne), null);

        assertThat(documentService.get(alice, aliceOne).categoryId()).isNull();
    }

    @Test
    @DisplayName("the zip contains one entry per readable document and omits the rest")
    void zipOmitsUnreadableDocuments() throws Exception {
        var buffer = new ByteArrayOutputStream();
        documentService.streamAsZip(alice, List.of(aliceOne, bobsDocument, aliceTwo), buffer);

        assertThat(entryNames(buffer.toByteArray()))
                .containsExactlyInAnyOrder("one.pdf", "two.pdf");
    }

    @Test
    @DisplayName("documents sharing a filename get distinct zip entries")
    void zipDisambiguatesDuplicateNames() throws Exception {
        Long thirdWithSameName = upload(alice, "one", "different bytes entirely");

        var buffer = new ByteArrayOutputStream();
        documentService.streamAsZip(alice, List.of(aliceOne, thirdWithSameName), buffer);

        // A zip with two identical entry names is malformed; the second becomes "one (2).pdf".
        assertThat(entryNames(buffer.toByteArray()))
                .containsExactlyInAnyOrder("one.pdf", "one (2).pdf");
    }

    private List<String> entryNames(byte[] zipBytes) throws Exception {
        var names = new java.util.ArrayList<String>();
        try (var zip = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    private Long upload(AuthenticatedUser caller, String name, String content) {
        return documentService.upload(
                caller,
                new DocumentDtos.UploadMetadata(name, null, null),
                new MockMultipartFile("file", name + ".pdf", "application/pdf", content.getBytes())).id();
    }

    private User persist(String name, String email) {
        return userRepository.save(User.builder()
                .name(name).email(email).password("{noop}irrelevant")
                .role(Role.EMPLOYEE).enabled(true).build());
    }

    private AuthenticatedUser principal(User user) {
        return AuthenticatedUser.fromToken(user.getId(), user.getEmail(), user.getName(), user.getRole());
    }
}
