package com.youngplace.iam.repository;

import com.youngplace.iam.entity.IamAuthAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface IamAuthAuditLogRepository extends JpaRepository<IamAuthAuditLogEntity, Long>,
        JpaSpecificationExecutor<IamAuthAuditLogEntity> {
}
