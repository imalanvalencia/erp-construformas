package es.construformas.api.repository;

import es.construformas.api.model.User;
import es.construformas.api.model.UserRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
     * S9/S12: administrators that could still guard the system. Used before an account
     * is deactivated or deleted, so the count must include only active ones.
     */
    @Query("SELECT COUNT(DISTINCT u) FROM User u JOIN u.roles r "
            + "WHERE r.name = 'ADMIN' AND u.active = true")
    long countActiveAdmins();
}
