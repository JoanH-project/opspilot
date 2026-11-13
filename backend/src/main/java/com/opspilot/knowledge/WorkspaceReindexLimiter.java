package com.opspilot.knowledge;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class WorkspaceReindexLimiter {
    private static final Duration COOLDOWN = Duration.ofMinutes(5);
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public WorkspaceReindexLimiter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public boolean tryAcquire(Long workspaceId) {
        Instant now = clock.instant();
        int inserted = jdbc.update(
                "INSERT IGNORE INTO workspace_knowledge_reindex_limits (workspace_id, last_requested_at) VALUES (?, ?)",
                workspaceId, Timestamp.from(now));
        if (inserted == 1) {
            return true;
        }
        int updated = jdbc.update("""
                UPDATE workspace_knowledge_reindex_limits
                SET last_requested_at = ?
                WHERE workspace_id = ? AND last_requested_at <= ?
                """, Timestamp.from(now), workspaceId, Timestamp.from(now.minus(COOLDOWN)));
        return updated == 1;
    }
}
