package es.construformas.api.repository;

import es.construformas.api.model.User;
import es.construformas.api.model.UserRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
