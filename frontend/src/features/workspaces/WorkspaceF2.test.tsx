import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';

import { clearRejectedWorkspaces, isWorkspaceRejected } from './workspaceRecovery';
import { workspacesApi } from '../../api/workspaces';
import { WorkspaceBootstrap } from './WorkspaceBootstrap';
import { WorkspaceDashboardRoute } from './WorkspaceDashboardRoute';
import { getStoredWorkspaceId, persistWorkspaceId } from './workspaceStorage';
import type {
  WorkspaceDashboard,
  WorkspaceMemberResponse,
  WorkspaceResponse,
  WorkspaceSummary,
} from '../../types/workspace';

const api = {
  list: vi.mocked(workspacesApi.list),
  create: vi.mocked(workspacesApi.create),
  getById: vi.mocked(workspacesApi.getById),
  update: vi.mocked(workspacesApi.update),
  listMembers: vi.mocked(workspacesApi.listMembers),
  getDashboard: vi.mocked(workspacesApi.getDashboard),
};

const authState = vi.hoisted(() => ({
  token: 'test-token',
  currentUser: { id: 10, email: 'owner@example.com', name: 'Owner', createdAt: '2026-01-01T00:00:00Z' },
  isAuthenticated: true,
  isLoading: false,
  logout: vi.fn(),
  refreshSession: vi.fn(),
  login: vi.fn(),
  register: vi.fn(),
}));

vi.mock('../auth/useAuth', () => ({
  useAuth: () => authState,
}));

vi.mock('../../api/workspaces', () => ({
  workspacesApi: {
    list: vi.fn(),
    create: vi.fn(),
    getById: vi.fn(),
    update: vi.fn(),
    listMembers: vi.fn(),
    getDashboard: vi.fn(),
  },
}));

const firstWorkspace: WorkspaceSummary = {
  id: 1,
  name: 'Operations',
  role: 'OWNER',
  createdAt: '2026-01-01T00:00:00Z',
};
const secondWorkspace: WorkspaceSummary = {
  id: 2,
  name: 'Product',
  role: 'MEMBER',
  createdAt: '2026-01-02T00:00:00Z',
};
const createdWorkspace: WorkspaceSummary = {
  id: 3,
  name: 'Created workspace',
  role: 'OWNER',
  createdAt: '2026-01-04T00:00:00Z',
};
const firstWorkspaceDetails: WorkspaceResponse = {
  ...firstWorkspace,
  owner: { id: 10, email: 'owner@example.com', name: 'Owner' },
  updatedAt: '2026-01-01T00:00:00Z',
};
const member: WorkspaceMemberResponse = {
  user: { id: 11, email: 'member@example.com', name: 'Member Name' },
  role: 'MEMBER',
  joinedAt: '2026-01-03T00:00:00Z',
};

function makeDashboard(workspaceId: number): WorkspaceDashboard {
  return {
    workspaceId,
    projects: { active: 0, archived: 0 },
    tasks: { total: 0, todo: 0, inProgress: 0, done: 0, overdue: 0 },
    documents: { active: 0, archived: 0 },
    recentActivities: [],
  };
}

function makeApiError(status: number, message: string, fieldErrors: Record<string, string> = {}) {
  return { status, message, fieldErrors, error: 'Request failed', timestamp: '2026-01-01T00:00:00Z' };
}

function LocationProbe() {
  const location = useLocation();
  return <output aria-label="Current route">{location.pathname}</output>;
}

function renderWorkspaceApp(initialPath = '/app') {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route
            path="/app"
            element={<><LocationProbe /><WorkspaceBootstrap /></>}
          />
          <Route
            path="/app/:workspaceId/dashboard"
            element={<><LocationProbe /><WorkspaceDashboardRoute /></>}
          />
          <Route path="*" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('F2 workspace behavior', () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.localStorage.setItem('opspilot_access_token', 'test-token');
    vi.clearAllMocks();
    clearRejectedWorkspaces();
    api.list.mockResolvedValue([firstWorkspace]);
    api.create.mockResolvedValue({
      ...firstWorkspaceDetails,
      id: 3,
      name: 'Created workspace',
    });
    api.getById.mockResolvedValue(firstWorkspaceDetails);
    api.listMembers.mockResolvedValue([member]);
    api.update.mockImplementation(async (_id, request) => ({
      ...firstWorkspaceDetails,
      name: request.name,
    }));
    api.getDashboard.mockImplementation(async (id) => makeDashboard(id));
  });

  afterEach(() => {
    cleanup();
  });

  it('creates the first workspace and navigates to its dashboard', async () => {
    api.list.mockImplementation(async () => (api.create.mock.calls.length ? [createdWorkspace] : []));
    const user = userEvent.setup();
    renderWorkspaceApp();

    await user.type(await screen.findByLabelText('Workspace name'), 'First workspace');
    await user.click(screen.getByRole('button', { name: 'Create workspace' }));

    await waitFor(() => expect(screen.getByLabelText('Current route')).toHaveTextContent('/app/3/dashboard'));
    expect(api.create).toHaveBeenCalledWith({ name: 'First workspace' }, 'test-token');
  });

  it('allows creating an additional workspace from the selector', async () => {
    api.list.mockImplementation(async () =>
      api.create.mock.calls.length
        ? [firstWorkspace, secondWorkspace, createdWorkspace]
        : [firstWorkspace, secondWorkspace],
    );
    const user = userEvent.setup();
    renderWorkspaceApp();

    const nameField = await screen.findByRole('textbox', { name: 'Create another workspace' });
    await user.type(nameField, 'Additional workspace');
    expect(nameField).toHaveValue('Additional workspace');
    await user.click(screen.getByRole('button', { name: 'Create another workspace' }));

    await waitFor(() => expect(api.create).toHaveBeenCalledOnce());
    await waitFor(() => expect(screen.getByLabelText('Current route')).toHaveTextContent('/app/3/dashboard'));
    expect(api.create).toHaveBeenCalledOnce();
  });

  it('switches workspaces through the real dashboard route', async () => {
    api.list.mockResolvedValue([firstWorkspace, secondWorkspace]);
    const user = userEvent.setup();
    renderWorkspaceApp();

    await user.click(await screen.findByRole('button', { name: /Product/ }));
    await waitFor(() => expect(screen.getAllByRole('heading', { name: 'Product' })).toHaveLength(2));
    expect(api.getDashboard).toHaveBeenCalledWith(2, 'test-token');
    expect(screen.getByLabelText('Current workspace')).toHaveValue('2');
  });

  it('does not render a late dashboard response beneath the newly selected workspace', async () => {
    api.list.mockResolvedValue([firstWorkspace, secondWorkspace]);
    let resolveFirstDashboard: ((dashboard: WorkspaceDashboard) => void) | undefined;
    api.getDashboard.mockImplementation((id) => {
      if (id === 1) {
        return new Promise((resolve) => {
          resolveFirstDashboard = resolve;
        });
      }
      return Promise.resolve({
        ...makeDashboard(2),
        recentActivities: [{
          id: 20,
          type: 'TASK_CREATED',
          entityType: 'TASK',
          entityId: 20,
          message: 'Current workspace activity',
          actor: { id: 10, name: 'Owner' },
          createdAt: '2026-01-04T00:00:00Z',
        }],
      });
    });
    const user = userEvent.setup();
    renderWorkspaceApp('/app/1/dashboard');
    await user.selectOptions(await screen.findByLabelText('Current workspace'), '2');

    expect(await screen.findByText('Current workspace activity')).toBeInTheDocument();
    resolveFirstDashboard?.({
      ...makeDashboard(1),
      recentActivities: [{
        id: 10,
        type: 'TASK_CREATED',
        entityType: 'TASK',
        entityId: 10,
        message: 'Old workspace activity',
        actor: { id: 10, name: 'Owner' },
        createdAt: '2026-01-04T00:00:00Z',
      }],
    });

    await waitFor(() => expect(screen.getAllByRole('heading', { name: 'Product' })).toHaveLength(2));
    expect(screen.queryByText('Old workspace activity')).not.toBeInTheDocument();
  });

  it('loads read-only members and lets an owner rename the workspace', async () => {
    const user = userEvent.setup();
    renderWorkspaceApp('/app/1/dashboard');

    await user.click(await screen.findByRole('button', { name: 'Workspace details' }));
    const dialog = await screen.findByRole('dialog', { name: 'Operations' });
    expect(await within(dialog).findByText('Member Name')).toBeInTheDocument();
    expect(within(dialog).getByText('member@example.com')).toBeInTheDocument();
    expect(within(dialog).getByText('MEMBER')).toBeInTheDocument();
    expect(within(dialog).getByText('Jan 3, 2026')).toBeInTheDocument();

    await user.clear(within(dialog).getByLabelText('Rename workspace'));
    await user.type(within(dialog).getByLabelText('Rename workspace'), 'Renamed');
    await user.click(within(dialog).getByRole('button', { name: 'Save name' }));

    await waitFor(() => expect(api.update).toHaveBeenCalledWith(1, { name: 'Renamed' }, 'test-token'));
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Renamed' })).toBeInTheDocument());
  });

  it('keeps the rename draft after a server 403', async () => {
    api.getById.mockResolvedValue({ ...firstWorkspaceDetails, role: 'ADMIN' });
    api.update.mockRejectedValue(makeApiError(403, 'Forbidden'));
    const user = userEvent.setup();
    renderWorkspaceApp('/app/1/dashboard');

    await user.click(await screen.findByRole('button', { name: 'Workspace details' }));
    const dialog = await screen.findByRole('dialog', { name: 'Operations' });
    await user.clear(within(dialog).getByLabelText('Rename workspace'));
    await user.type(within(dialog).getByLabelText('Rename workspace'), 'Preserve me');
    await user.click(within(dialog).getByRole('button', { name: 'Save name' }));

    expect(await within(dialog).findByText(/Only workspace owners and admins/)).toBeInTheDocument();
    expect(within(dialog).getByLabelText('Rename workspace')).toHaveValue('Preserve me');
  });

  it('hides rename controls for workspace members', async () => {
    api.getById.mockResolvedValue({ ...firstWorkspaceDetails, role: 'MEMBER' });
    const user = userEvent.setup();
    renderWorkspaceApp('/app/1/dashboard');

    await user.click(await screen.findByRole('button', { name: 'Workspace details' }));
    const dialog = await screen.findByRole('dialog', { name: 'Operations' });
    await within(dialog).findByText('Member Name');
    expect(within(dialog).queryByLabelText('Rename workspace')).not.toBeInTheDocument();
    expect(within(dialog).queryByRole('button', { name: 'Save name' })).not.toBeInTheDocument();
  });

  it('keeps a single-workspace Dashboard 404 in bounded recovery until explicit retry', async () => {
    api.list.mockResolvedValue([firstWorkspace]);
    api.getDashboard.mockRejectedValue(makeApiError(404, 'Not found'));
    const user = userEvent.setup();
    renderWorkspaceApp('/app/1/dashboard');

    expect(await screen.findByRole('heading', { name: 'Dashboard unavailable' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry access' })).toBeInTheDocument();
    expect(screen.getByLabelText('Current route')).toHaveTextContent('/app/1/dashboard');
    expect(isWorkspaceRejected(1)).toBe(true);
    expect(api.list).toHaveBeenCalledTimes(1);
    expect(api.getDashboard).toHaveBeenCalledTimes(1);

    api.getDashboard.mockResolvedValue(makeDashboard(1));
    await user.click(screen.getByRole('button', { name: 'Choose another workspace' }));
    await waitFor(() => expect(screen.getByLabelText('Current route')).toHaveTextContent('/app'));
    expect(await screen.findByText(/Operations is unavailable/)).toBeInTheDocument();
    expect(api.getDashboard).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole('button', { name: 'Retry access' }));
    await waitFor(() => expect(within(screen.getByRole('main')).getByRole('heading', { name: 'Operations' })).toBeInTheDocument());
    expect(api.getDashboard).toHaveBeenCalledTimes(2);
    expect(screen.getByLabelText('Current route')).toHaveTextContent('/app/1/dashboard');
  });

  it('validates a blank workspace name before creating', async () => {
    api.list.mockResolvedValue([]);
    const user = userEvent.setup();
    renderWorkspaceApp();

    await user.click(await screen.findByRole('button', { name: 'Create workspace' }));
    expect(await screen.findByText('Workspace name is required.')).toBeInTheDocument();
    expect(api.create).not.toHaveBeenCalled();
  });

  it('shows backend field errors without discarding workspace creation input', async () => {
    api.list.mockResolvedValue([]);
    api.create.mockRejectedValue(
      makeApiError(400, 'Validation failed', { name: 'Workspace name was rejected.' }),
    );
    const user = userEvent.setup();
    renderWorkspaceApp();

    const nameField = await screen.findByLabelText('Workspace name');
    await user.type(nameField, 'Workspace name');
    await user.click(screen.getByRole('button', { name: 'Create workspace' }));

    expect(await screen.findByText('Workspace name was rejected.')).toBeInTheDocument();
    expect(nameField).toHaveValue('Workspace name');
  });

  it('maps server-side rename validation feedback to the form', async () => {
    api.update.mockRejectedValue(
      makeApiError(400, 'Validation failed', { name: 'Name is already in use.' }),
    );
    const user = userEvent.setup();
    renderWorkspaceApp('/app/1/dashboard');
    await user.click(await screen.findByRole('button', { name: 'Workspace details' }));
    const dialog = await screen.findByRole('dialog', { name: 'Operations' });
    await user.clear(within(dialog).getByLabelText('Rename workspace'));
    await user.type(within(dialog).getByLabelText('Rename workspace'), 'Duplicate');
    await user.click(within(dialog).getByRole('button', { name: 'Save name' }));

    expect(await within(dialog).findByText('Name is already in use.')).toBeInTheDocument();
    expect(within(dialog).getByRole('textbox', { name: /^Rename workspace/ })).toHaveValue('Duplicate');
  });

  it('settles a protected workspace 401 by logging out', async () => {
    api.getDashboard.mockRejectedValue(makeApiError(401, 'Unauthorized'));
    renderWorkspaceApp('/app/1/dashboard');

    await waitFor(() => expect(authState.logout).toHaveBeenCalledOnce());
    expect(authState.refreshSession).not.toHaveBeenCalled();
  });

  it('keeps last-workspace preferences isolated by user', () => {
    persistWorkspaceId(1, 10);
    persistWorkspaceId(2, 11);
    expect(getStoredWorkspaceId(10)).toBe(1);
    expect(getStoredWorkspaceId(11)).toBe(2);

    persistWorkspaceId(null, 10);
    expect(getStoredWorkspaceId(10)).toBeNull();
    expect(getStoredWorkspaceId(11)).toBe(2);
  });
});
