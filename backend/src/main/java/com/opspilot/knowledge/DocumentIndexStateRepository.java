package com.opspilot.knowledge;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.QueryHints;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface DocumentIndexStateRepository extends JpaRepository<DocumentIndexState, Long> {
    List<DocumentIndexState> findByWorkspaceId(Long workspaceId);

    List<DocumentIndexState> findByWorkspaceIdAndStatus(Long workspaceId, DocumentIndexStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select state from DocumentIndexState state
            where state.status = com.opspilot.knowledge.DocumentIndexStatus.PENDING
               or (state.status = com.opspilot.knowledge.DocumentIndexStatus.FAILED
                   and state.attempts < :maxAttempts
                   and (state.nextAttemptAt is null or state.nextAttemptAt <= :now))
               or (state.status = com.opspilot.knowledge.DocumentIndexStatus.INDEXING
                   and state.leaseUntil < :now
                   and state.attempts < :maxAttempts)
            order by state.updatedAt asc
            """)
    List<DocumentIndexState> lockNextClaimable(
            @Param("now") Instant now, @Param("maxAttempts") int maxAttempts, Pageable pageable);

    @Query("select count(state) from DocumentIndexState state where state.workspaceId = :workspaceId and state.status = :status")
    long countByWorkspaceAndStatus(@Param("workspaceId") Long workspaceId,
                                   @Param("status") DocumentIndexStatus status);

    long countByWorkspaceId(Long workspaceId);
}
