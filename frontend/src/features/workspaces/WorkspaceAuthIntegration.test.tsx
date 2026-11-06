import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';

import { authApi } from '../../api/auth';
import { workspacesApi } from '../../api/workspaces';
import { AuthProvider } from '../auth/AuthContext';
import { useAuth } from '../auth/useAuth';
import { WorkspaceBootstrap } from './WorkspaceBootstrap';
import type { WorkspaceResponse, WorkspaceSummary } from '../../types/workspace';

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
const createdWorkspace: WorkspaceResponse = {
  id: 3,
  name: 'New space',
  role: 'OWNER',
  owner: { id: 10, email: 'owner@example.com', name: 'Owner' },
  createdAt: '2026-01-03T00:00:00Z',
  updatedAt: '2026-01-03T00:00:00Z',
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((complete) => {
    resolve = complete;
  });
  return { promise, resolve };
}

function LocationProbe() {
  return <output aria-label="Current route">{useLocation().pathname}</output>;
}

function SessionControls() {
  const { logout } = useAuth();
  return <button onClick={logout} type="button">End session</button>;
}

describe('workspace creation session boundary', () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.localStorage.setItem('opspilot_access_token', 'integration-token');
    vi.restoreAllMocks();
  });

  afterEach(() => cleanup());

  it('uses the real AuthProvider and QueryClient when logout happens during create invalidation', async () => {
    const refresh = deferred<WorkspaceSummary[]>();
    const listSpy = vi.spyOn(workspacesApi, 'list')
      .mockResolvedValueOnce([firstWorkspace, secondWorkspace])
      .mockImplementationOnce(() => refresh.promise);
    vi.spyOn(workspacesApi, 'create').mockResolvedValue(createdWorkspace);
    vi.spyOn(authApi, 'getCurrentUser').mockResolvedValue({
      id: 10,
      email: 'owner@example.com',
      name: 'Owner',
      createdAt: '2026-01-01T00:00:00Z',
    });

    const client = new QueryClient({
      defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
    });
    const user = userEvent.setup();
    render(
      <QueryClientProvider client={client}>
        <AuthProvider>
          <MemoryRouter initialEntries={['/app']}>
            <SessionControls />
            <LocationProbe />
            <Routes>
              <Route path="/app" element={<WorkspaceBootstrap />} />
              <Route path="/app/:workspaceId/dashboard" element={<p>Dashboard</p>} />
            </Routes>
          </MemoryRouter>
        </AuthProvider>
      </QueryClientProvider>,
    );

    await user.type(await screen.findByRole('textbox', { name: 'Create another workspace' }), 'New space');
    await user.click(screen.getByRole('button', { name: 'Create another workspace' }));
    await waitFor(() => expect(listSpy).toHaveBeenCalledTimes(2));

    await user.click(screen.getByRole('button', { name: 'End session' }));
    refresh.resolve([firstWorkspace, secondWorkspace, { ...firstWorkspace, id: 3, name: 'New space' }]);

    await waitFor(() => expect(window.localStorage.getItem('opspilot_access_token')).toBeNull());
    expect(screen.getByLabelText('Current route')).toHaveTextContent('/app');
    expect(window.localStorage.getItem('opspilot_last_workspace_id_user_10')).toBeNull();
    expect(listSpy).toHaveBeenCalledTimes(2);
  });
});
