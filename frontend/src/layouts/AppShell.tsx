import type { ReactElement, ReactNode } from 'react';
import { NavLink } from 'react-router-dom';

import { useAuth } from '../features/auth/useAuth';

type AppShellProps = {
  children: ReactNode;
};

const navigationItems = [
  { label: 'Dashboard', to: '/dashboard' },
  { label: 'Projects', to: '/projects' },
  { label: 'Documents', to: '/documents' },
];

export function AppShell({ children }: AppShellProps): ReactElement {
  const { currentUser, logout } = useAuth();

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand-block">
          <div className="brand-mark">O</div>
          <div>
            <strong>OpsPilot</strong>
            <small>Workspace</small>
          </div>
        </div>

        <nav className="sidebar-nav" aria-label="Primary navigation">
          {navigationItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
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
            <h2>Team workspace</h2>
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
