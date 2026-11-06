import { useMemo, useState, type FormEvent, type ReactElement } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { getApiErrorMessage, isApiError } from '../../api/errors';
import { projectsApi } from '../../api/projects';
import { tasksApi } from '../../api/tasks';
import { workspacesApi } from '../../api/workspaces';
import { WorkspaceAreaRoute } from '../../features/workspaces/WorkspaceAreaRoute';
import { useAuth } from '../../features/auth/useAuth';
import { useLogoutOnUnauthorized } from '../../features/auth/useLogoutOnUnauthorized';
import { projectQueryKeys, taskQueryKeys, workspaceQueryKeys } from '../../queryClient';
import type { Project, ProjectStatus, ProjectUpdateRequest } from '../../types/project';
import type { CreateTaskRequest, Task, TaskFilters, TaskPriority, TaskStatus, UpdateTaskRequest } from '../../types/task';
import type { WorkspaceSummary } from '../../types/workspace';

export function ProjectsRoute(): ReactElement {
  return <WorkspaceAreaRoute>{(workspace) => <ProjectsPage workspace={workspace} />}</WorkspaceAreaRoute>;
}

function ProjectsPage({ workspace }: { workspace: WorkspaceSummary }): ReactElement {
  const { token, currentUser } = useAuth();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<ProjectStatus>('ACTIVE');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [formError, setFormError] = useState('');
  const [editingProject, setEditingProject] = useState<Project | null>(null);
  const userId = currentUser?.id ?? 0;
  const projectsQuery = useQuery({
    queryKey: projectQueryKeys.list(userId, workspace.id, status),
    queryFn: () => projectsApi.list(workspace.id, status, token!),
    enabled: Boolean(token && currentUser),
  });
  const invalidateProjects = async (): Promise<void> => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['projects', userId, workspace.id] }),
      queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.dashboard(userId, workspace.id) }),
    ]);
  };
  const createMutation = useMutation({
    mutationFn: () => projectsApi.create(workspace.id, { name: name.trim(), description: description.trim() }, token!),
    onSuccess: async () => {
      setName('');
      setDescription('');
      setFormError('');
      await invalidateProjects();
    },
  });
  const updateMutation = useMutation({
    mutationFn: ({ projectId, request }: { projectId: number; request: ProjectUpdateRequest }) =>
      projectsApi.update(projectId, request, token!),
    onSuccess: async () => {
      setEditingProject(null);
      await invalidateProjects();
    },
  });
  const archiveMutation = useMutation({
    mutationFn: (projectId: number) => projectsApi.archive(projectId, token!),
    onSuccess: invalidateProjects,
  });
  useLogoutOnUnauthorized(projectsQuery.error);
  useLogoutOnUnauthorized(createMutation.error);
  useLogoutOnUnauthorized(updateMutation.error);
  useLogoutOnUnauthorized(archiveMutation.error);
  const projects = useMemo(() => projectsQuery.data ?? [], [projectsQuery.data]);

  const createProject = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    if (!name.trim()) {
      setFormError('Project name is required.');
      return;
    }
    setFormError('');
    createMutation.mutate(undefined, {
      onError: (error) => setFormError(getApiErrorMessage(error, 'Unable to create the project.')),
    });
  };

  const canManage = (project: Project): boolean => Boolean(
    currentUser &&
      (['OWNER', 'ADMIN'].includes(workspace.role) || project.creator.id === currentUser.id),
  );

  return (
    <div className="resource-page">
      <header className="resource-header">
        <div>
          <p className="eyebrow">Workspace projects</p>
          <h1>Projects</h1>
          <p>Plan work and track delivery within {workspace.name}.</p>
        </div>
        <label className="filter-field">
          <span>Project status</span>
          <select aria-label="Project status" onChange={(event) => setStatus(event.target.value as ProjectStatus)} value={status}>
            <option value="ACTIVE">Active</option>
            <option value="ARCHIVED">Archived</option>
          </select>
        </label>
      </header>

      {status === 'ACTIVE' ? (
        <section className="resource-card" aria-labelledby="new-project-heading">
          <h2 id="new-project-heading">Create a project</h2>
          <form className="resource-form" onSubmit={createProject} noValidate>
            <label className="field">
              <span>Project name</span>
              <input maxLength={150} onChange={(event) => setName(event.target.value)} value={name} />
            </label>
            <label className="field">
              <span>Description</span>
              <textarea maxLength={5000} onChange={(event) => setDescription(event.target.value)} rows={3} value={description} />
            </label>
            {formError ? <p className="form-error-banner" role="alert">{formError}</p> : null}
            {createMutation.isError && isApiError(createMutation.error) && createMutation.error.status === 400 && createMutation.error.fieldErrors.name ? (
              <p className="field-error" role="alert">{createMutation.error.fieldErrors.name}</p>
            ) : null}
            <button className="primary-button" disabled={createMutation.isPending} type="submit">
              {createMutation.isPending ? 'Creating…' : 'Create project'}
            </button>
          </form>
        </section>
      ) : null}

      <section className="resource-card" aria-labelledby="project-list-heading">
        <div className="section-heading">
          <div>
            <p className="eyebrow">{status === 'ACTIVE' ? 'Current work' : 'Past work'}</p>
            <h2 id="project-list-heading">{status === 'ACTIVE' ? 'Active projects' : 'Archived projects'}</h2>
          </div>
          <span className="activity-limit">{projects.length} total</span>
        </div>
        {projectsQuery.isPending ? <p role="status">Loading projects…</p> : null}
        {projectsQuery.isError ? (
          <div className="inline-error" role="alert">
            <p>{getApiErrorMessage(projectsQuery.error, 'Unable to load projects.')}</p>
            <button className="secondary-button" onClick={() => void projectsQuery.refetch()} type="button">Retry</button>
          </div>
        ) : null}
        {projectsQuery.isSuccess && projects.length === 0 ? (
          <p className="empty-state">{status === 'ACTIVE' ? 'No active projects yet. Create one to get started.' : 'No archived projects.'}</p>
        ) : null}
        <div className="resource-list">
          {projects.map((project) => (
            <article className="resource-row project-row" key={project.id}>
              <div className="resource-copy">
                <div className="resource-title-line">
                  <Link className="resource-title" to={`/app/${workspace.id}/projects/${project.id}`}>{project.name}</Link>
                  <span className={`status-badge ${project.status.toLowerCase()}`}>{project.status.toLowerCase()}</span>
                </div>
                <p>{project.description || 'No description provided.'}</p>
                <small>Created by {project.creator.name}</small>
              </div>
              <div className="resource-actions">
                {canManage(project) && project.status === 'ACTIVE' ? (
                  <>
                    <button className="secondary-button" onClick={() => setEditingProject(project)} type="button">Edit</button>
                    <button
                      className="secondary-button danger-button"
                      disabled={archiveMutation.isPending}
                      onClick={() => {
                        if (window.confirm(`Archive “${project.name}”? Its tasks will become read-only.`)) {
                          archiveMutation.mutate(project.id);
                        }
                      }}
                      type="button"
                    >Archive</button>
                  </>
                ) : null}
              </div>
            </article>
          ))}
        </div>
        {archiveMutation.isError ? <p className="field-error" role="alert">{getApiErrorMessage(archiveMutation.error, 'Unable to archive the project.')}</p> : null}
      </section>

      {editingProject ? (
        <ProjectEditor
          project={editingProject}
          busy={updateMutation.isPending}
          error={updateMutation.isError ? getApiErrorMessage(updateMutation.error, 'Unable to save changes.') : ''}
          onCancel={() => setEditingProject(null)}
          onSave={(request) => updateMutation.mutate({ projectId: editingProject.id, request })}
        />
      ) : null}
    </div>
  );
}

function ProjectEditor({
  project,
  busy,
  error,
  onCancel,
  onSave,
}: {
  project: Project;
  busy: boolean;
  error: string;
  onCancel: () => void;
  onSave: (request: ProjectUpdateRequest) => void;
}): ReactElement {
  const [name, setName] = useState(project.name);
  const [description, setDescription] = useState(project.description ?? '');
  return (
    <div className="dialog-backdrop">
      <section aria-labelledby="edit-project-heading" aria-modal="true" className="page-panel resource-dialog" role="dialog">
        <h2 id="edit-project-heading">Edit project</h2>
        <form className="resource-form" onSubmit={(event) => {
          event.preventDefault();
          if (name.trim()) onSave({ name: name.trim(), description });
        }}>
          <label className="field"><span>Project name</span><input maxLength={150} value={name} onChange={(event) => setName(event.target.value)} /></label>
          <label className="field"><span>Description</span><textarea maxLength={5000} rows={4} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
          {error ? <p className="form-error-banner" role="alert">{error}</p> : null}
          {!name.trim() ? <p className="field-error" role="alert">Project name is required.</p> : null}
          <div className="resource-actions">
            <button className="secondary-button" onClick={onCancel} type="button">Cancel</button>
            <button className="primary-button" disabled={busy || !name.trim()} type="submit">{busy ? 'Saving…' : 'Save project'}</button>
          </div>
        </form>
      </section>
    </div>
  );
}

export function ProjectDetailRoute(): ReactElement {
  const { projectId: rawProjectId } = useParams();
  const projectId = rawProjectId && /^[1-9]\d*$/.test(rawProjectId) ? Number(rawProjectId) : null;
  return (
    <WorkspaceAreaRoute>
      {(workspace) => projectId ? <ProjectDetailPage workspace={workspace} projectId={projectId} /> : <Navigate to={`/app/${workspace.id}/projects`} replace />}
    </WorkspaceAreaRoute>
  );
}

function ProjectDetailPage({ workspace, projectId }: { workspace: WorkspaceSummary; projectId: number }): ReactElement {
  const { token, currentUser } = useAuth();
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState<TaskStatus | ''>('');
  const [priorityFilter, setPriorityFilter] = useState<TaskPriority | ''>('');
  const [assigneeFilter, setAssigneeFilter] = useState('');
  const [editingTask, setEditingTask] = useState<Task | null>(null);
  const [mutationError, setMutationError] = useState('');
  const userId = currentUser?.id ?? 0;
  const projectQuery = useQuery({
    queryKey: projectQueryKeys.detail(userId, projectId),
    queryFn: () => projectsApi.getById(projectId, token!),
    enabled: Boolean(token && currentUser),
  });
  const project = projectQuery.data;
  const filters: TaskFilters = {
    ...(statusFilter ? { status: statusFilter } : {}),
    ...(priorityFilter ? { priority: priorityFilter } : {}),
    ...(assigneeFilter ? { assigneeId: Number(assigneeFilter) } : {}),
  };
  const tasksQuery = useQuery({
    queryKey: taskQueryKeys.list(userId, projectId, filters),
    queryFn: () => tasksApi.list(projectId, filters, token!),
    enabled: Boolean(token && currentUser && project && project.workspaceId === workspace.id),
  });
  const membersQuery = useQuery({
    queryKey: workspaceQueryKeys.members(userId, workspace.id),
    queryFn: () => workspacesApi.listMembers(workspace.id, token!),
    enabled: Boolean(token && currentUser),
    staleTime: 30_000,
  });
  const invalidateProjectData = async (): Promise<void> => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['tasks', userId, projectId] }),
      queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.dashboard(userId, workspace.id) }),
    ]);
  };
  const createTaskMutation = useMutation({
    mutationFn: (request: CreateTaskRequest) => tasksApi.create(projectId, request, token!),
    onSuccess: async () => {
      setMutationError('');
      await invalidateProjectData();
    },
  });
  const updateTaskMutation = useMutation({
    mutationFn: ({ taskId, request }: { taskId: number; request: UpdateTaskRequest }) => tasksApi.update(taskId, request, token!),
    onSuccess: async () => {
      setEditingTask(null);
      setMutationError('');
      await invalidateProjectData();
    },
  });
  const statusMutation = useMutation({
    mutationFn: ({ taskId, status }: { taskId: number; status: TaskStatus }) => tasksApi.updateStatus(taskId, status, token!),
    onSuccess: invalidateProjectData,
    onError: (error) => setMutationError(getApiErrorMessage(error, 'Unable to update task status.')),
  });
  useLogoutOnUnauthorized(projectQuery.error);
  useLogoutOnUnauthorized(tasksQuery.error);
  useLogoutOnUnauthorized(membersQuery.error);
  useLogoutOnUnauthorized(createTaskMutation.error);
  useLogoutOnUnauthorized(updateTaskMutation.error);
  useLogoutOnUnauthorized(statusMutation.error);

  if (projectQuery.isPending) return <p role="status">Loading project…</p>;
  if (projectQuery.isError || !project || project.workspaceId !== workspace.id) {
    return (
      <section className="resource-card inline-error" role="alert">
        <h1>Project unavailable</h1>
        <p>{project && project.workspaceId !== workspace.id
          ? 'This project is not available in the selected workspace.'
          : getApiErrorMessage(projectQuery.error, 'Unable to load this project.')}</p>
        <button className="secondary-button" onClick={() => void projectQuery.refetch()} type="button">Retry</button>
      </section>
    );
  }

  const isArchived = project.status === 'ARCHIVED';
  const tasks = tasksQuery.data ?? [];
  const members = membersQuery.data ?? [];
  const taskError = (error: unknown): void => setMutationError(getApiErrorMessage(error, 'Unable to save the task.'));

  return (
    <div className="resource-page">
      <header className="resource-header project-detail-header">
        <div>
          <Link className="back-link" to={`/app/${workspace.id}/projects`}>← Projects</Link>
          <p className="eyebrow">Project</p>
          <h1>{project.name}</h1>
          <p>{project.description || 'No description provided.'}</p>
        </div>
        <span className={`status-badge ${project.status.toLowerCase()}`}>{project.status.toLowerCase()}</span>
      </header>

      {isArchived ? <p className="form-error-banner" role="status">This project is archived. Tasks are read-only.</p> : null}

      {!isArchived ? (
        <section className="resource-card" aria-labelledby="new-task-heading">
          <h2 id="new-task-heading">Create a task</h2>
          <TaskEditor
            members={members}
            busy={createTaskMutation.isPending}
            submitLabel="Create task"
            onSubmit={(request) => {
              if ('clearAssignee' in request || 'clearDueDate' in request) return;
              createTaskMutation.mutate(request as CreateTaskRequest, { onError: taskError });
            }}
          />
        </section>
      ) : null}

      <section className="resource-card" aria-labelledby="task-list-heading">
        <div className="section-heading">
          <div><p className="eyebrow">Project work</p><h2 id="task-list-heading">Tasks</h2></div>
          <span className="activity-limit">{tasks.length} shown</span>
        </div>
        <div className="filter-row" aria-label="Task filters">
          <label className="filter-field"><span>Status</span><select aria-label="Filter tasks by status" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as TaskStatus | '')}>
            <option value="">All statuses</option><option value="TODO">To do</option><option value="IN_PROGRESS">In progress</option><option value="DONE">Done</option>
          </select></label>
          <label className="filter-field"><span>Priority</span><select aria-label="Filter tasks by priority" value={priorityFilter} onChange={(event) => setPriorityFilter(event.target.value as TaskPriority | '')}>
            <option value="">All priorities</option><option value="LOW">Low</option><option value="MEDIUM">Medium</option><option value="HIGH">High</option>
          </select></label>
          <label className="filter-field"><span>Assignee</span><select aria-label="Filter tasks by assignee" value={assigneeFilter} onChange={(event) => setAssigneeFilter(event.target.value)}>
            <option value="">All assignees</option>{members.map(({ user }) => <option key={user.id} value={user.id}>{user.name}</option>)}
          </select></label>
        </div>
        {membersQuery.isError ? <p className="field-error" role="alert">{getApiErrorMessage(membersQuery.error, 'Unable to load workspace members.')}</p> : null}
        {tasksQuery.isPending && project ? <p role="status">Loading tasks…</p> : null}
        {tasksQuery.isError ? <div className="inline-error" role="alert"><p>{getApiErrorMessage(tasksQuery.error, 'Unable to load tasks.')}</p><button className="secondary-button" onClick={() => void tasksQuery.refetch()} type="button">Retry</button></div> : null}
        {tasksQuery.isSuccess && tasks.length === 0 ? <p className="empty-state">No tasks match these filters.</p> : null}
        {mutationError ? <p className="form-error-banner" role="alert">{mutationError}</p> : null}
        <div className="resource-list">
          {tasks.map((task) => (
            <article className="resource-row task-row" key={task.id}>
              <div className="resource-copy">
                <div className="resource-title-line"><strong className="resource-title">{task.title}</strong><span className={`status-badge ${task.status.toLowerCase()}`}>{task.status.replace('_', ' ').toLowerCase()}</span></div>
                <p>{task.description || 'No description provided.'}</p>
                <small>{task.priority.toLowerCase()} priority · {task.assignee ? `Assigned to ${task.assignee.name}` : 'Unassigned'}{task.dueDate ? ` · Due ${task.dueDate}` : ''}</small>
              </div>
              <div className="resource-actions">
                {!isArchived ? <>
                  <label className="status-control"><span className="sr-only">Status for {task.title}</span><select aria-label={`Status for ${task.title}`} value={task.status} onChange={(event) => statusMutation.mutate({ taskId: task.id, status: event.target.value as TaskStatus })}>
                    <option value="TODO">To do</option><option value="IN_PROGRESS">In progress</option><option value="DONE">Done</option>
                  </select></label>
                  <button className="secondary-button" onClick={() => setEditingTask(task)} type="button">Edit task</button>
                </> : null}
              </div>
              {editingTask?.id === task.id ? (
                <div className="task-edit-panel">
                  <TaskEditor
                    task={task}
                    members={members}
                    busy={updateTaskMutation.isPending}
                    submitLabel="Save task"
                    onCancel={() => setEditingTask(null)}
                    onSubmit={(request) => updateTaskMutation.mutate({ taskId: task.id, request: request as UpdateTaskRequest }, { onError: taskError })}
                  />
                </div>
              ) : null}
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}

function TaskEditor({
  task,
  members,
  busy,
  submitLabel,
  onSubmit,
  onCancel,
}: {
  task?: Task;
  members: { user: { id: number; email: string; name: string }; role: string; joinedAt: string }[];
  busy: boolean;
  submitLabel: string;
  onSubmit: (request: CreateTaskRequest | UpdateTaskRequest) => void;
  onCancel?: () => void;
}): ReactElement {
  const [title, setTitle] = useState(task?.title ?? '');
  const [description, setDescription] = useState(task?.description ?? '');
  const [priority, setPriority] = useState<TaskPriority>(task?.priority ?? 'MEDIUM');
  const [assigneeId, setAssigneeId] = useState(task?.assignee ? String(task.assignee.id) : '');
  const [dueDate, setDueDate] = useState(task?.dueDate ?? '');
  const [validationError, setValidationError] = useState('');

  return (
    <form className="resource-form task-form" onSubmit={(event) => {
      event.preventDefault();
      if (!title.trim()) {
        setValidationError('Task title is required.');
        return;
      }
      setValidationError('');
      if (task) {
        onSubmit({
          title: title.trim(), description, priority,
          ...(assigneeId ? { assigneeId: Number(assigneeId) } : {}),
          ...(dueDate ? { dueDate } : {}),
          ...(task.assignee && !assigneeId ? { clearAssignee: true } : {}),
          ...(task.dueDate && !dueDate ? { clearDueDate: true } : {}),
        });
      } else {
        onSubmit({
          title: title.trim(), description, priority,
          ...(assigneeId ? { assigneeId: Number(assigneeId) } : {}),
          ...(dueDate ? { dueDate } : {}),
        });
      }
    }} noValidate>
      <label className="field"><span>Task title</span><input maxLength={200} value={title} onChange={(event) => setTitle(event.target.value)} /></label>
      <label className="field"><span>Description</span><textarea maxLength={5000} rows={2} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
      <div className="task-form-grid">
        <label className="filter-field"><span>Priority</span><select value={priority} onChange={(event) => setPriority(event.target.value as TaskPriority)}><option value="LOW">Low</option><option value="MEDIUM">Medium</option><option value="HIGH">High</option></select></label>
        <label className="filter-field"><span>Assignee</span><select value={assigneeId} onChange={(event) => setAssigneeId(event.target.value)}><option value="">Unassigned</option>{members.map(({ user }) => <option key={user.id} value={user.id}>{user.name}</option>)}</select></label>
        <label className="filter-field"><span>Due date</span><input type="date" value={dueDate} onChange={(event) => setDueDate(event.target.value)} /></label>
      </div>
      {validationError ? <p className="field-error" role="alert">{validationError}</p> : null}
      <div className="resource-actions"><button className="primary-button" disabled={busy} type="submit">{busy ? 'Saving…' : submitLabel}</button>{onCancel ? <button className="secondary-button" onClick={onCancel} type="button">Cancel</button> : null}</div>
    </form>
  );
}
