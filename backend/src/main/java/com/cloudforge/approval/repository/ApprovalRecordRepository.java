package com.cloudforge.approval.repository;

import java.util.Optional;
import java.util.UUID;

import com.cloudforge.approval.domain.ApprovalRecord;

import org.springframework.data.repository.Repository;

/** Insert-and-read only: decisions are never changed or deleted. */
public interface ApprovalRecordRepository extends Repository<ApprovalRecord, UUID> {

    ApprovalRecord save(ApprovalRecord record);

    Optional<ApprovalRecord> findByRequestId(UUID requestId);
}
