package com.youngplace.iam.repository;

import com.youngplace.iam.entity.IamUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IamUserRepository extends JpaRepository<IamUserEntity, Long> {
    Optional<IamUserEntity> findByUsername(String username);
}
