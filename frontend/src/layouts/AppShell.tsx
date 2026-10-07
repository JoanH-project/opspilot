import { useEffect, useRef, useState, type FormEvent, type ReactElement, type ReactNode } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';

import { getApiErrorMessage, isApiError } from '../api/errors';
import { workspacesApi } from '../api/workspaces';
import { getStoredToken } from '../features/auth/authStorage';
import { useAuth } from '../features/auth/useAuth';
import { persistWorkspaceId } from '../features/workspaces/workspaceStorage';
import { WorkspaceDetailsDialog } from '../features/workspaces/WorkspaceDetailsDialog';
import { workspaceQueryKeys } from '../queryClient';
import type { WorkspaceSummary } from '../types/workspace';

type AppShellProps = {
  currentWorkspace: WorkspaceSummary;
  workspaces: WorkspaceSummary[];
  children: ReactNode;
};

const navigationItems = [{ label: 'Dashboard', to: 'dashboard' }];

export function AppShell({ children, currentWorkspace, workspaces }: AppShellProps): ReactElement {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { currentUser, logout, token } = useAuth();
  const [isDetailsOpen, setIsDetailsOpen] = useState(false);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [newWorkspaceName, setNewWorkspaceName] = useState('');
  const [createError, setCreateError] = useState('');
  const currentUserId = useRef(currentUser?.id);
  useEffect(() => {
    currentUserId.current = currentUser?.id;
  }, [currentUser?.id]);

  const createMutation = useMutation({
    mutationFn: (name: string) => workspacesApi.create({ name }, token!),
    onSuccess: async (created) => {
      const userId = currentUser?.id;
      if (!userId || currentUserId.current !== userId || getStoredToken() !== token) {
        return;
      }
      queryClient.setQueryData<WorkspaceSummary[]>(workspaceQueryKeys.list(userId), (previous) => [
        ...(previous ?? workspaces),
        {
          id: created.id,
          name: created.name,
          role: created.role,
          createdAt: created.createdAt,
        },
      ]);
      await queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.list(userId) });
      persistWorkspaceId(created.id, userId);
      setIsCreateOpen(false);
      setNewWorkspaceName('');
      navigate(`/app/${created.id}/dashboard`, { replace: true });
    },
  });

  const handleCreateWorkspace = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    const name = newWorkspaceName.trim();
    if (!name) {
      setCreateError('Workspace name is required.');
      return;
    }
    if (name.length > 100) {
      setCreateError('Workspace name must be 100 characters or fewer.');
      return;
    }
    setCreateError('');
    createMutation.mutate(name, {
      onError: (error) => {
        if (getStoredToken() !== token) {
          return;
        }
        if (isApiError(error) && error.status === 401) {
          logout();
          return;
        }
        setCreateError(getApiErrorMessage(error, 'Unable to create the workspace.'));
      },
    });
  };

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand-block">
          <div className="brand-mark">O</div>
          <div>
            <strong>OpsPilot</strong>
            <small>{currentWorkspace.role}</small>
          </div>
        </div>

        <label className="workspace-switcher">
          <span>Workspace</span>
          <select
            aria-label="Current workspace"
            value={currentWorkspace.id}
            onChange={(event) => {
              const nextWorkspaceId = Number(event.target.value);
              persistWorkspaceId(nextWorkspaceId, currentUser?.id);
              navigate(`/app/${nextWorkspaceId}/dashboard`);
            }}
          >
            {workspaces.map((workspace) => (
              <option key={workspace.id} value={workspace.id}>
                {workspace.name}
              </option>
            ))}
          </select>
        </label>
        <div className="workspace-actions">
          <button className="sidebar-action" onClick={() => setIsCreateOpen(true)} type="button">
            Create workspace
          </button>
          <button className="sidebar-action" onClick={() => setIsDetailsOpen(true)} type="button">
            Workspace details
          </button>
        </div>

        <nav className="sidebar-nav" aria-label="Primary navigation">
          {navigationItems.map((item) => (
            <NavLink
              key={item.to}
              to={`/app/${currentWorkspace.id}/${item.to}`}
              className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="content-panel">
        <header className="topbar">
          <div>
            <p className="eyebrow">Operations</p>
            <h2>{currentWorkspace.name}</h2>
          </div>

          <div className="topbar-actions">
            <div className="user-pill" aria-live="polite">
              {currentUser ? currentUser.name : 'User'}
            </div>
            <button type="button" className="secondary-button" onClick={logout}>
              Logout
            </button>
          </div>
        </header>

        <main className="main-content">{children}</main>
      </div>
      {isDetailsOpen ? (
        <WorkspaceDetailsDialog
          onClose={() => setIsDetailsOpen(false)}
          workspace={currentWorkspace}
        />
      ) : null}
      {isCreateOpen ? (
        <div className="dialog-backdrop" onMouseDown={(event) => {
          if (event.target === event.currentTarget) {
            setIsCreateOpen(false);
          }
        }}>
          <section aria-labelledby="create-workspace-heading" aria-modal="true" className="page-panel workspace-dialog" role="dialog">
            <header className="workspace-dialog-header">
              <div>
                <p className="eyebrow">New workspace</p>
                <h2 id="create-workspace-heading">Create workspace</h2>
              </div>
              <button aria-label="Close create workspace" className="icon-button" onClick={() => setIsCreateOpen(false)} type="button">
                ×
              </button>
            </header>
            <form className="workspace-rename-form" onSubmit={handleCreateWorkspace} noValidate>
              <label className="field" htmlFor="new-workspace-name">
                <span>Workspace name</span>
                <input
                  autoFocus
                  id="new-workspace-name"
                  maxLength={100}
                  onChange={(event) => setNewWorkspaceName(event.target.value)}
                  value={newWorkspaceName}
                />
                {createError ? <small role="alert">{createError}</small> : null}
                {createMutation.isError &&
                isApiError(createMutation.error) &&
                createMutation.error.status === 400 &&
                createMutation.error.fieldErrors.name ? (
                  <small role="alert">{createMutation.error.fieldErrors.name}</small>
                ) : null}
              </label>
              <button className="primary-button" disabled={createMutation.isPending} type="submit">
                {createMutation.isPending ? 'Creating…' : 'Create workspace'}
              </button>
            </form>
          </section>
        </div>
      ) : null}
    </div>
  );
}
