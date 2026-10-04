import type { ReactElement, ReactNode } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';

import { useAuth } from '../features/auth/useAuth';
import { persistWorkspaceId } from '../features/workspaces/workspaceStorage';
import type { WorkspaceSummary } from '../types/workspace';

type AppShellProps = {
  currentWorkspace: WorkspaceSummary;
  workspaces: WorkspaceSummary[];
  children: ReactNode;
};

const navigationItems = [{ label: 'Dashboard', to: 'dashboard' }];

export function AppShell({ children, currentWorkspace, workspaces }: AppShellProps): ReactElement {
  const navigate = useNavigate();
  const { currentUser, logout } = useAuth();

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
              persistWorkspaceId(nextWorkspaceId);
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
    </div>
  );
}
