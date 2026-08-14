package com.cuevas.powerspike.repository;

import com.cuevas.powerspike.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByMail(String mail);

    Optional<UserEntity> findByUsername(String username);

    boolean existsByMail(String mail);

    boolean existsByUsername(String username);
}
