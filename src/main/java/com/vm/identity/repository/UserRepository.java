package com.vm.identity.repository;

import com.vm.identity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    // Methods with optional includeDeleted flag (defaults to false)
    @Query("SELECT u FROM User u WHERE u.id = :id AND (COALESCE(:includeDeleted, false) = true OR u.deleted = false)")
    Optional<User> findById(@Param("id") UUID id, @Param("includeDeleted") Boolean includeDeleted);

    @Query("SELECT u FROM User u WHERE u.idpId = :idpId AND (COALESCE(:includeDeleted, false) = true OR u.deleted = false)")
    Optional<User> findByIdpId(@Param("idpId") String idpId, @Param("includeDeleted") Boolean includeDeleted);

    @Query("SELECT u FROM User u WHERE u.email = :email AND (COALESCE(:includeDeleted, false) = true OR u.deleted = false)")
    Optional<User> findByEmail(@Param("email") String email, @Param("includeDeleted") Boolean includeDeleted);

    @Query("SELECT u FROM User u WHERE u.username = :username AND (COALESCE(:includeDeleted, false) = true OR u.deleted = false)")
    Optional<User> findByUsername(@Param("username") String username, @Param("includeDeleted") Boolean includeDeleted);

    // Convenience methods that exclude deleted users by default
    default Optional<User> findById(UUID id) {
        return findById(id, false);
    }

    default Optional<User> findByIdpId(String idpId) {
        return findByIdpId(idpId, false);
    }

    default Optional<User> findByEmail(String email) {
        return findByEmail(email, false);
    }

    default Optional<User> findByUsername(String username) {
        return findByUsername(username, false);
    }

    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByIdpId(String idpId);
}
