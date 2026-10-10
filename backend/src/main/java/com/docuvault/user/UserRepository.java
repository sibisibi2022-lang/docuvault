package com.docuvault.user;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            SELECT u FROM User u
            WHERE (CAST(:search AS String) IS NULL
                   OR LOWER(u.name)  LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%')))
            """)
    Page<User> search(@Param("search") String search, Pageable pageable);

    long countByRole(Role role);
}
