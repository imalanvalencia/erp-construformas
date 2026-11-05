package es.construformas.api.repository;

import es.construformas.api.model.User;
import es.construformas.api.model.UserRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    /**
     * Same lookup as {@link #findByEmail(String)} but with {@code currentProject} fetched
     * in the same query, so a profile payload never depends on an open session to read it.
     */
    @EntityGraph(attributePaths = "currentProject")
    Optional<User> findWithCurrentProjectByEmail(String email);
    Optional<User> findByNie(String nie);
    Optional<User> findByPhone(String phone);
    @Deprecated List<User> findByRole(UserRole role);
    List<User> findByActive(boolean active);
    boolean existsByEmail(String email);
    boolean existsByNie(String nie);
    boolean existsByPhone(String phone);

    /**
     * S12: does this account own or review any row that would outlive a physical delete?
     * Every non-cascading {@code users(id)} foreign key from
     * {@code src/main/resources/db/migration} is listed here — a miss turns a guarded
     * 409 into a raw FK violation, and a stale table turns every delete into a false
     * refusal, so keep the two in step.
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM budgets b WHERE b.created_by = :id
                UNION ALL SELECT 1 FROM budgets b WHERE b.approved_by = :id
                UNION ALL SELECT 1 FROM budgets b WHERE b.deleted_by = :id
                UNION ALL SELECT 1 FROM budget_annexes a WHERE a.created_by = :id
                UNION ALL SELECT 1 FROM invoices i WHERE i.created_by = :id
                UNION ALL SELECT 1 FROM invoices i WHERE i.deleted_by = :id
                UNION ALL SELECT 1 FROM rectifying_invoices r WHERE r.created_by = :id
                UNION ALL SELECT 1 FROM payments p WHERE p.created_by = :id
                UNION ALL SELECT 1 FROM supplier_invoices s WHERE s.created_by = :id
                UNION ALL SELECT 1 FROM clock_entries c WHERE c.user_id = :id
                UNION ALL SELECT 1 FROM clock_corrections c WHERE c.user_id = :id
                UNION ALL SELECT 1 FROM clock_corrections c WHERE c.reviewed_by = :id
                UNION ALL SELECT 1 FROM work_photos w WHERE w.uploaded_by = :id
                UNION ALL SELECT 1 FROM work_progress w WHERE w.reported_by = :id
                UNION ALL SELECT 1 FROM document_lifecycle_events d WHERE d.actor_id = :id
            )
            """, nativeQuery = true)
    boolean hasHistory(@Param("id") Long id);

    /**
     * S9/S12: administrators that could still guard the system. Used before an account
     * is deactivated or deleted, so the count must include only active ones.
     */
    @Query("SELECT COUNT(DISTINCT u) FROM User u JOIN u.roles r "
            + "WHERE r.name = 'ADMIN' AND u.active = true")
    long countActiveAdmins();
}
