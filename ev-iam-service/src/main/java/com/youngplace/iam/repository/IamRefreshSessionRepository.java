package com.youngplace.iam.repository;

import com.youngplace.iam.entity.IamRefreshSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IamRefreshSessionRepository extends JpaRepository<IamRefreshSessionEntity, String> {
    List<IamRefreshSessionEntity> findByUsernameAndRevokedFalse(String username);
    void deleteByExpireAtEpochMillisLessThan(long epochMillis);
}
