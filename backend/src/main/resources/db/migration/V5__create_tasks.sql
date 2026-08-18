CREATE TABLE tasks (
 id BIGINT NOT NULL AUTO_INCREMENT, project_id BIGINT NOT NULL, title VARCHAR(200) NOT NULL, description TEXT NULL,
 status VARCHAR(20) NOT NULL, priority VARCHAR(20) NOT NULL, assignee_id BIGINT NULL, created_by BIGINT NOT NULL, due_date DATE NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, PRIMARY KEY(id),
 INDEX idx_tasks_project_id (project_id), INDEX idx_tasks_assignee_id (assignee_id), INDEX idx_tasks_project_status (project_id,status),
 CONSTRAINT fk_tasks_project FOREIGN KEY(project_id) REFERENCES projects(id), CONSTRAINT fk_tasks_assignee FOREIGN KEY(assignee_id) REFERENCES users(id), CONSTRAINT fk_tasks_created_by FOREIGN KEY(created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
