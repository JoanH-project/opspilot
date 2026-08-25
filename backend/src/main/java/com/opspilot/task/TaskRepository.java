package com.opspilot.task;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByProject_IdOrderByCreatedAtDesc(Long projectId);

    @Query("select count(task) from Task task where task.project.workspace.id = :workspaceId")
    long countForWorkspace(@Param("workspaceId") Long workspaceId);

    @Query("""
            select count(task) from Task task
            where task.project.workspace.id = :workspaceId and task.status = :status
            """)
    long countForWorkspaceByStatus(@Param("workspaceId") Long workspaceId,
                                   @Param("status") TaskStatus status);

    @Query("""
            select count(task) from Task task
            where task.project.workspace.id = :workspaceId
              and task.dueDate < :today
              and task.status <> com.opspilot.task.TaskStatus.DONE
            """)
    long countOverdueForWorkspace(@Param("workspaceId") Long workspaceId,
                                  @Param("today") LocalDate today);
}
