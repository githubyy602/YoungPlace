package com.youngplace.iam.service;

import com.youngplace.iam.entity.IamAuthAuditLogEntity;
import com.youngplace.iam.repository.IamAuthAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuthAuditService {

    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAIL = "LOGIN_FAIL";
    public static final String REFRESH_SUCCESS = "REFRESH_SUCCESS";
    public static final String REFRESH_FAIL = "REFRESH_FAIL";
    public static final String LOGOUT = "LOGOUT";
    public static final int MAX_EXPORT_SIZE = 2000;

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthAuditService.class);

    private final IamAuthAuditLogRepository auditLogRepository;

    public AuthAuditService(IamAuthAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(String eventType, String username, String traceId, String message) {
        try {
            IamAuthAuditLogEntity entity = new IamAuthAuditLogEntity();
            entity.setEventType(trimToDefault(eventType, "UNKNOWN"));
            entity.setUsername(trimToNull(username));
            entity.setTraceId(trimToNull(traceId));
            entity.setMessage(truncate(trimToNull(message), 512));
            auditLogRepository.save(entity);
        } catch (Exception ex) {
            // Audit write failures should not break authentication flow.
            LOGGER.warn("Failed to write auth audit log, eventType={}, username={}", eventType, username, ex);
        }
    }

    public Page<IamAuthAuditLogEntity> search(String username,
                                              String eventType,
                                              String traceId,
                                              String messageKeyword,
                                              Long startAtEpochMillis,
                                              Long endAtEpochMillis,
                                              int page,
                                              int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAtEpochMillis").and(Sort.by(Sort.Direction.DESC, "id")));
        Specification<IamAuthAuditLogEntity> specification = buildSpecification(
                username, eventType, traceId, messageKeyword, startAtEpochMillis, endAtEpochMillis);
        return auditLogRepository.findAll(specification, pageable);
    }

    public List<IamAuthAuditLogEntity> searchForExport(String username,
                                                       String eventType,
                                                       String traceId,
                                                       String messageKeyword,
                                                       Long startAtEpochMillis,
                                                       Long endAtEpochMillis,
                                                       int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_EXPORT_SIZE);
        Pageable pageable = PageRequest.of(0, safeLimit,
                Sort.by(Sort.Direction.DESC, "createdAtEpochMillis").and(Sort.by(Sort.Direction.DESC, "id")));
        Specification<IamAuthAuditLogEntity> specification = buildSpecification(
                username, eventType, traceId, messageKeyword, startAtEpochMillis, endAtEpochMillis);
        return auditLogRepository.findAll(specification, pageable).getContent();
    }

    private Specification<IamAuthAuditLogEntity> buildSpecification(String username,
                                                                     String eventType,
                                                                     String traceId,
                                                                     String messageKeyword,
                                                                     Long startAtEpochMillis,
                                                                     Long endAtEpochMillis) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<Predicate>();
            if (StringUtils.hasText(username)) {
                predicates.add(criteriaBuilder.equal(root.get("username"), username.trim()));
            }
            if (StringUtils.hasText(eventType)) {
                predicates.add(criteriaBuilder.equal(root.get("eventType"), eventType.trim()));
            }
            if (StringUtils.hasText(traceId)) {
                predicates.add(criteriaBuilder.equal(root.get("traceId"), traceId.trim()));
            }
            if (StringUtils.hasText(messageKeyword)) {
                predicates.add(criteriaBuilder.like(root.get("message"), "%" + messageKeyword.trim() + "%"));
            }
            if (startAtEpochMillis != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAtEpochMillis"), startAtEpochMillis));
            }
            if (endAtEpochMillis != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAtEpochMillis"), endAtEpochMillis));
            }
            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String trimToDefault(String value, String defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
