import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';

import { useAuth } from '../auth/useAuth';
import { DocumentDetailRoute, DocumentsRoute } from '../documents/DocumentsRoute';
import { ProjectDetailRoute, ProjectsRoute } from '../projects/ProjectsRoute';
import { workspaceQueryKeys } from '../../queryClient';
import type { Document } from '../../types/document';
import type { Project } from '../../types/project';
import type { CreateTaskRequest, Task, TaskFilters, UpdateTaskRequest } from '../../types/task';
import type { WorkspaceDashboard, WorkspaceSummary } from '../../types/workspace';

const mocks = vi.hoisted(() => ({
  projectList: vi.fn(), projectCreate: vi.fn(), projectGet: vi.fn(), projectUpdate: vi.fn(), projectArchive: vi.fn(),
  taskList: vi.fn(), taskCreate: vi.fn(), taskUpdate: vi.fn(), taskStatus: vi.fn(),
  documentList: vi.fn(), documentCreate: vi.fn(), documentGet: vi.fn(), documentUpdate: vi.fn(), documentArchive: vi.fn(),
  workspaceList: vi.fn(), workspaceMembers: vi.fn(), dashboard: vi.fn(),
  auth: {
    token: 'resource-test-token',
    currentUser: { id: 10, email: 'owner@example.com', name: 'Owner', createdAt: '2026-01-01T00:00:00Z' },
    isAuthenticated: true,
    isLoading: false,
    logout: vi.fn(),
    refreshSession: vi.fn(),
    login: vi.fn(),
    register: vi.fn(),
  },
}));

vi.mock('../auth/useAuth', () => ({ useAuth: () => mocks.auth }));
vi.mock('../../api/projects', () => ({ projectsApi: {
  list: mocks.projectList, create: mocks.projectCreate, getById: mocks.projectGet,
  update: mocks.projectUpdate, archive: mocks.projectArchive,
} }));
vi.mock('../../api/tasks', () => ({ tasksApi: {
  list: mocks.taskList, create: mocks.taskCreate, update: mocks.taskUpdate, updateStatus: mocks.taskStatus,
} }));
vi.mock('../../api/documents', () => ({ documentsApi: {
  list: mocks.documentList, create: mocks.documentCreate, getById: mocks.documentGet,
  update: mocks.documentUpdate, archive: mocks.documentArchive,
} }));
vi.mock('../../api/workspaces', () => ({ workspacesApi: {
  list: mocks.workspaceList,
  listMembers: mocks.workspaceMembers,
  getDashboard: mocks.dashboard,
} }));

const workspace: WorkspaceSummary = { id: 1, name: 'Operations', role: 'OWNER', createdAt: '2026-01-01T00:00:00Z' };
const projectBase: Project = {
  id: 5,
  workspaceId: 1,
  name: 'Launch',
  description: 'Release work',
  status: 'ACTIVE',
  creator: { id: 10, email: 'owner@example.com', name: 'Owner' },
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};
const member = { user: { id: 11, email: 'member@example.com', name: 'Member' }, role: 'MEMBER', joinedAt: '2026-01-02T00:00:00Z' };

function emptyDashboard(workspaceId: number): WorkspaceDashboard {
  return {
    workspaceId,
    projects: { active: 0, archived: 0 },
    tasks: { total: 0, todo: 0, inProgress: 0, done: 0, overdue: 0 },
    documents: { active: 0, archived: 0 },
    recentActivities: [],
  };
}

function apiError(status: number, message: string) {
  return { status, message, error: 'Request failed', fieldErrors: {}, timestamp: '2026-01-01T00:00:00Z' };
}

function DashboardProbe() {
  const { currentUser } = useAuth();
  useQuery({
    queryKey: workspaceQueryKeys.dashboard(currentUser?.id ?? 0, workspace.id),
    queryFn: () => mocks.dashboard(workspace.id, mocks.auth.token),
  });
  return null;
}

function LocationProbe() {
  return <output aria-label="Current route">{useLocation().pathname}</output>;
}

function renderApp(initialPath: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } } });
  const view = render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[initialPath]}>
        <LocationProbe />
        <DashboardProbe />
        <Routes>
          <Route path="/app/:workspaceId/projects" element={<ProjectsRoute />} />
          <Route path="/app/:workspaceId/projects/:projectId" element={<ProjectDetailRoute />} />
          <Route path="/app/:workspaceId/documents" element={<DocumentsRoute />} />
          <Route path="/app/:workspaceId/documents/:documentId" element={<DocumentDetailRoute />} />
          <Route path="/app" element={<p>Workspace selection</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { ...view, client };
}

describe('V1 project, task, and document views', () => {
  let projects: Project[];
  let tasks: Task[];
  let documents: Document[];

  beforeEach(() => {
    window.localStorage.clear();
    window.localStorage.setItem('opspilot_access_token', mocks.auth.token);
    mocks.auth.currentUser = { id: 10, email: 'owner@example.com', name: 'Owner', createdAt: '2026-01-01T00:00:00Z' };
    vi.clearAllMocks();
    projects = [];
    tasks = [];
    documents = [];
    mocks.workspaceList.mockResolvedValue([workspace]);
    mocks.workspaceMembers.mockResolvedValue([member]);
    mocks.dashboard.mockImplementation(async (id: number) => emptyDashboard(id));
    mocks.projectList.mockImplementation(async (_id: number, status: 'ACTIVE' | 'ARCHIVED') => projects.filter((item) => item.status === status));
    mocks.projectCreate.mockImplementation(async (id: number, request: { name: string; description?: string }) => {
      const created = { ...projectBase, id: 5, workspaceId: id, name: request.name, description: request.description ?? null };
      projects = [created, ...projects];
      return created;
    });
    mocks.projectGet.mockImplementation(async () => projects.find((item) => item.id === 5) ?? projectBase);
    mocks.projectUpdate.mockImplementation(async (_id: number, request: Partial<Project>) => {
      const updated = { ...(projects.find((item) => item.id === 5) ?? projectBase), ...request } as Project;
      projects = projects.map((item) => item.id === updated.id ? updated : item);
      return updated;
    });
    mocks.projectArchive.mockImplementation(async (id: number) => {
      projects = projects.map((item) => item.id === id ? { ...item, status: 'ARCHIVED' } : item);
      return projects.find((item) => item.id === id)!;
    });
    mocks.taskList.mockImplementation(async (_id: number, filters: TaskFilters) => tasks.filter((task) =>
      (!filters.status || task.status === filters.status) &&
      (!filters.priority || task.priority === filters.priority) &&
      (filters.assigneeId === undefined || task.assignee?.id === filters.assigneeId),
    ));
    mocks.taskCreate.mockImplementation(async (id: number, request: CreateTaskRequest) => {
      const created: Task = {
        ...projectTaskBase,
        id: 20,
        projectId: id,
        title: request.title ?? '',
        description: request.description ?? null,
        priority: request.priority ?? 'MEDIUM',
        assignee: request.assigneeId === 11 ? member.user : null,
        dueDate: request.dueDate ?? null,
      };
      tasks = [created, ...tasks];
      return created;
    });
    mocks.taskUpdate.mockImplementation(async (id: number, request: UpdateTaskRequest) => {
      const updated: Task = {
        ...tasks.find((item) => item.id === id)!,
        ...request,
        assignee: request.clearAssignee ? null : request.assigneeId === 11 ? member.user : tasks[0]?.assignee ?? null,
        dueDate: request.clearDueDate ? null : request.dueDate ?? tasks[0]?.dueDate ?? null,
      };
      tasks = tasks.map((item) => item.id === id ? updated : item);
      return updated;
    });
    mocks.taskStatus.mockImplementation(async (id: number, status: Task['status']) => {
      tasks = tasks.map((item) => item.id === id ? { ...item, status } : item);
      return tasks.find((item) => item.id === id)!;
    });
    mocks.documentList.mockImplementation(async (_id: number, status: 'ACTIVE' | 'ARCHIVED') =>
      documents.filter((item) => item.status === status).map((item) => ({
        id: item.id,
        title: item.title,
        status: item.status,
        creator: item.creator,
        createdAt: item.createdAt,
        updatedAt: item.updatedAt,
      })),
    );
    mocks.documentCreate.mockImplementation(async (id: number, request: { title: string; content: string }) => {
      const created: Document = { ...documentBase, id: 30, workspaceId: id, title: request.title, content: request.content };
      documents = [created, ...documents];
      return created;
    });
    mocks.documentGet.mockImplementation(async (id: number) => documents.find((item) => item.id === id) ?? documentBase);
    mocks.documentUpdate.mockImplementation(async (id: number, request: Partial<Document>) => {
      const updated = { ...documents.find((item) => item.id === id)!, ...request, updatedAt: '2026-01-04T00:00:00Z' };
      documents = documents.map((item) => item.id === id ? updated : item);
      return updated;
    });
    mocks.documentArchive.mockImplementation(async (id: number) => {
      const updated = { ...documents.find((item) => item.id === id)!, status: 'ARCHIVED' as const };
      documents = documents.map((item) => item.id === id ? updated : item);
      return updated;
    });
    vi.spyOn(window, 'confirm').mockReturnValue(true);
  });

  afterEach(() => cleanup());

  it('creates, edits, and archives projects, with status filtering and Dashboard refresh', async () => {
    const user = userEvent.setup();
    renderApp('/app/1/projects');

    await user.type(await screen.findByLabelText('Project name'), 'Launch');
    await user.type(screen.getByLabelText('Description'), 'Release work');
    await user.click(screen.getByRole('button', { name: 'Create project' }));
    expect(await screen.findByRole('link', { name: 'Launch' })).toHaveAttribute('href', '/app/1/projects/5');
    expect(mocks.projectCreate).toHaveBeenCalledWith(1, { name: 'Launch', description: 'Release work' }, mocks.auth.token);
    await waitFor(() => expect(mocks.dashboard).toHaveBeenCalledTimes(2));

    await user.click(screen.getByRole('button', { name: 'Edit' }));
    const dialog = screen.getByRole('dialog', { name: 'Edit project' });
    await user.clear(within(dialog).getByLabelText('Project name'));
    await user.type(within(dialog).getByLabelText('Project name'), 'Launch V1');
    await user.click(within(dialog).getByRole('button', { name: 'Save project' }));
    expect(await screen.findByRole('link', { name: 'Launch V1' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Archive' }));
    expect(window.confirm).toHaveBeenCalledOnce();
    expect(await screen.findByText('No active projects yet. Create one to get started.')).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText('Project status'), 'ARCHIVED');
    expect(await screen.findByRole('link', { name: 'Launch V1' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
    expect(mocks.projectArchive).toHaveBeenCalledWith(5, mocks.auth.token);
  });

  it('creates and updates tasks, applies server filters, and sends clear flags', async () => {
    projects = [projectBase];
    const user = userEvent.setup();
    renderApp('/app/1/projects/5');

    await user.type(await screen.findByLabelText('Task title'), 'Fix login');
    await user.type(screen.getByLabelText('Description'), 'Investigate the redirect');
    const createTaskSection = screen.getByRole('heading', { name: 'Create a task' }).closest('section');
    if (!createTaskSection) throw new Error('Create task form section was not rendered');
    await user.selectOptions(within(createTaskSection).getByLabelText('Assignee'), '11');
    await user.type(screen.getByLabelText('Due date'), '2026-12-01');
    await user.click(screen.getByRole('button', { name: 'Create task' }));
    expect(await screen.findByText('Fix login')).toBeInTheDocument();
    expect(mocks.taskCreate).toHaveBeenCalledWith(5, {
      title: 'Fix login', description: 'Investigate the redirect', priority: 'MEDIUM', assigneeId: 11, dueDate: '2026-12-01',
    }, mocks.auth.token);
    await waitFor(() => expect(mocks.dashboard).toHaveBeenCalledTimes(2));

    await user.selectOptions(screen.getByLabelText('Status for Fix login'), 'IN_PROGRESS');
    await waitFor(() => expect(mocks.taskStatus).toHaveBeenCalledWith(20, 'IN_PROGRESS', mocks.auth.token));
    await user.selectOptions(screen.getByLabelText('Filter tasks by priority'), 'MEDIUM');
    await waitFor(() => expect(mocks.taskList).toHaveBeenCalledWith(5, { priority: 'MEDIUM' }, mocks.auth.token));
    await user.selectOptions(screen.getByLabelText('Filter tasks by status'), 'IN_PROGRESS');
    await waitFor(() => expect(mocks.taskList).toHaveBeenCalledWith(5, { status: 'IN_PROGRESS', priority: 'MEDIUM' }, mocks.auth.token));
    await user.selectOptions(screen.getByLabelText('Filter tasks by assignee'), '11');
    await waitFor(() => expect(mocks.taskList).toHaveBeenCalledWith(5, { status: 'IN_PROGRESS', priority: 'MEDIUM', assigneeId: 11 }, mocks.auth.token));

    await user.selectOptions(screen.getByLabelText('Filter tasks by priority'), '');
    await user.selectOptions(screen.getByLabelText('Filter tasks by status'), '');
    await user.selectOptions(screen.getByLabelText('Filter tasks by assignee'), '');
    await user.click(await screen.findByRole('button', { name: 'Edit task' }));
    const taskRow = screen.getByText('Fix login').closest('article');
    if (!taskRow) throw new Error('Task row was not rendered');
    const assigneeSelect = within(taskRow).getByLabelText('Assignee');
    await user.selectOptions(assigneeSelect, '');
    await user.clear(within(taskRow).getByLabelText('Due date'));
    await user.click(within(taskRow).getByRole('button', { name: 'Save task' }));
    await waitFor(() => expect(mocks.taskUpdate).toHaveBeenCalledWith(20, expect.objectContaining({
      title: 'Fix login', description: 'Investigate the redirect', priority: 'MEDIUM', clearAssignee: true, clearDueDate: true,
    }), mocks.auth.token));
  });

  it('loads document content on demand, previews Markdown safely, edits, and archives read-only', async () => {
    const user = userEvent.setup();
    renderApp('/app/1/documents');

    await user.type(await screen.findByLabelText('Document title'), 'Runbook');
    await user.type(screen.getByLabelText('Markdown content'), '# Safe heading\n\n<script>alert(1)</script>');
    await user.click(screen.getByRole('button', { name: 'Create document' }));
    expect(await screen.findByRole('heading', { name: 'Runbook' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Safe heading' })).toBeInTheDocument();
    expect(document.querySelector('script')).toBeNull();
    expect(mocks.documentCreate).toHaveBeenCalledWith(1, {
      title: 'Runbook', content: '# Safe heading\n\n<script>alert(1)</script>',
    }, mocks.auth.token);
    expect(mocks.documentGet).toHaveBeenCalledWith(30, mocks.auth.token);
    await waitFor(() => expect(mocks.dashboard).toHaveBeenCalledTimes(2));

    await user.click(screen.getByRole('button', { name: 'Edit document' }));
    await user.clear(screen.getByLabelText('Document title'));
    await user.type(screen.getByLabelText('Document title'), 'Runbook v2');
    await user.clear(screen.getByLabelText('Markdown content'));
    await user.type(screen.getByLabelText('Markdown content'), '## Updated steps');
    await user.click(screen.getByRole('tab', { name: 'Preview' }));
    expect(screen.getByRole('heading', { name: 'Updated steps' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Save document' }));
    expect(await screen.findByRole('heading', { name: 'Runbook v2' })).toBeInTheDocument();
    expect(mocks.documentUpdate).toHaveBeenCalledWith(30, { title: 'Runbook v2', content: '## Updated steps' }, mocks.auth.token);

    await user.click(screen.getByRole('button', { name: 'Archive' }));
    expect(await screen.findByText('This document is archived and read-only.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit document' })).not.toBeInTheDocument();
    expect(mocks.documentArchive).toHaveBeenCalledWith(30, mocks.auth.token);
  });

  it('does not load project data under a workspace URL the user does not belong to', async () => {
    mocks.workspaceList.mockResolvedValue([workspace]);
    renderApp('/app/2/projects');

    expect(await screen.findByText('Workspace selection')).toBeInTheDocument();
    expect(screen.getByLabelText('Current route')).toHaveTextContent('/app');
    expect(mocks.projectList).not.toHaveBeenCalled();
  });

  it('loads document summaries first and fetches the full Markdown only after opening one', async () => {
    documents = [documentBase];
    const user = userEvent.setup();
    renderApp('/app/1/documents');

    expect(await screen.findByRole('link', { name: 'Runbook' })).toBeInTheDocument();
    expect(screen.queryByText('Safe heading')).not.toBeInTheDocument();
    expect(mocks.documentGet).not.toHaveBeenCalled();
    await user.click(screen.getByRole('link', { name: 'Runbook' }));
    expect(await screen.findByRole('heading', { name: 'Safe heading' })).toBeInTheDocument();
    expect(mocks.documentGet).toHaveBeenCalledWith(30, mocks.auth.token);
  });

  it('keeps archived project task controls read-only', async () => {
    projects = [{ ...projectBase, status: 'ARCHIVED' }];
    mocks.projectGet.mockResolvedValue({ ...projectBase, status: 'ARCHIVED' });
    renderApp('/app/1/projects/5');

    expect(await screen.findByText('This project is archived. Tasks are read-only.')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Create a task' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit task' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Status for Fix login')).not.toBeInTheDocument();
  });

  it('does not show a project whose ID belongs to a different workspace route', async () => {
    projects = [{ ...projectBase, workspaceId: 2 }];
    mocks.projectGet.mockResolvedValue({ ...projectBase, workspaceId: 2 });
    renderApp('/app/1/projects/5');

    expect(await screen.findByRole('heading', { name: 'Project unavailable' })).toBeInTheDocument();
    expect(screen.getByText('This project is not available in the selected workspace.')).toBeInTheDocument();
    expect(mocks.taskList).not.toHaveBeenCalled();
  });

  it('hides project edit/archive actions from a member who is not the creator', async () => {
    mocks.auth.currentUser = { id: 11, email: 'member@example.com', name: 'Member', createdAt: '2026-01-02T00:00:00Z' };
    mocks.workspaceList.mockResolvedValue([{ ...workspace, role: 'MEMBER' }]);
    projects = [projectBase];
    renderApp('/app/1/projects');

    expect(await screen.findByRole('link', { name: 'Launch' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create project' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Archive' })).not.toBeInTheDocument();
  });

  it('logs out when a task request receives HTTP 401', async () => {
    projects = [projectBase];
    mocks.taskList.mockRejectedValue(apiError(401, 'Unauthorized'));
    renderApp('/app/1/projects/5');

    await waitFor(() => expect(mocks.auth.logout).toHaveBeenCalledOnce());
  });
});

const projectTaskBase: Task = {
  id: 20,
  projectId: 5,
  title: '',
  description: null,
  status: 'TODO',
  priority: 'MEDIUM',
  assignee: null,
  creator: { id: 10, email: 'owner@example.com', name: 'Owner' },
  dueDate: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

const documentBase: Document = {
  id: 30,
  workspaceId: 1,
  title: 'Runbook',
  content: '# Safe heading\n\n<script>alert(1)</script>',
  status: 'ACTIVE',
  creator: { id: 10, name: 'Owner' },
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};
