import { useEffect, useState, type FormEvent, type ReactElement } from 'react';
import { useNavigate } from 'react-router-dom';

import { workspacesApi } from '../../api/workspaces';
import { useAuth } from '../auth/useAuth';
import { getStoredWorkspaceId, persistWorkspaceId } from './workspaceStorage';
import type { WorkspaceSummary } from '../../types/workspace';
import { getApiErrorMessage, isApiError } from '../../api/errors';

type LoadState =
  | { status: 'error'; requestKey: number; message: string }
  | { status: 'ready'; requestKey: number; workspaces: WorkspaceSummary[] };

export function WorkspaceBootstrap(): ReactElement {
  const navigate = useNavigate();
  const { token, refreshSession } = useAuth();
  const [reloadKey, setReloadKey] = useState(0);
  const [loadState, setLoadState] = useState<LoadState | null>(null);
  const [workspaceName, setWorkspaceName] = useState('');
  const [formError, setFormError] = useState('');
  const [isCreating, setIsCreating] = useState(false);

  useEffect(() => {
    if (!token) {
      return;
    }

    let isActive = true;

    void workspacesApi
      .list(token)
      .then((workspaces) => {
        if (!isActive) {
          return;
        }

        if (workspaces.length === 1) {
          persistWorkspaceId(workspaces[0].id);
          navigate(`/app/${workspaces[0].id}/dashboard`, { replace: true });
          return;
        }

        if (workspaces.length > 1) {
          const storedId = getStoredWorkspaceId();
          const storedWorkspace = workspaces.find((workspace) => workspace.id === storedId);
          if (storedWorkspace) {
            navigate(`/app/${storedWorkspace.id}/dashboard`, { replace: true });
            return;
          }

          persistWorkspaceId(null);
        } else {
          persistWorkspaceId(null);
        }

        setLoadState({ status: 'ready', requestKey: reloadKey, workspaces });
      })
      .catch(async (error: unknown) => {
        if (!isActive) {
          return;
        }

        if (isApiError(error) && error.status === 401) {
          await refreshSession();
          return;
        }

        setLoadState({
          status: 'error',
          requestKey: reloadKey,
          message: getApiErrorMessage(error, 'Unable to load your workspaces.'),
        });
      });

    return () => {
      isActive = false;
    };
  }, [navigate, refreshSession, reloadKey, token]);

  const handleCreateWorkspace = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    const name = workspaceName.trim();

    if (!name) {
      setFormError('Workspace name is required.');
      return;
    }

    if (name.length > 100) {
      setFormError('Workspace name must be 100 characters or fewer.');
      return;
    }

    if (!token) {
      return;
    }

    setIsCreating(true);
    setFormError('');

    try {
      const workspace = await workspacesApi.create({ name }, token);
      persistWorkspaceId(workspace.id);
      navigate(`/app/${workspace.id}/dashboard`, { replace: true });
    } catch (error) {
      if (isApiError(error) && error.status === 401) {
        await refreshSession();
      } else {
        setFormError(getApiErrorMessage(error, 'Unable to create the workspace.'));
      }
    } finally {
      setIsCreating(false);
    }
  };

  if (!loadState || loadState.requestKey !== reloadKey) {
    return <div className="page-loading">Loading your workspaces…</div>;
  }

  if (loadState.status === 'error') {
    return (
      <div className="bootstrap-page">
        <section className="page-panel state-panel" role="alert">
          <p className="eyebrow">Workspace setup</p>
          <h1>We couldn’t load your workspaces</h1>
          <p>{loadState.message}</p>
          <button className="secondary-button" onClick={() => setReloadKey((current) => current + 1)}>
            Retry
          </button>
        </section>
      </div>
    );
  }

  if (loadState.workspaces.length === 0) {
    return (
      <div className="bootstrap-page">
        <section className="page-panel bootstrap-panel">
          <p className="eyebrow">Welcome to OpsPilot</p>
          <h1>Create your first workspace</h1>
          <p>A workspace is where your team’s operations will live.</p>
          <form className="auth-form workspace-form" onSubmit={handleCreateWorkspace} noValidate>
            <label className="field">
              <span>Workspace name</span>
              <input
                autoFocus
                maxLength={100}
                value={workspaceName}
                onChange={(event) => setWorkspaceName(event.target.value)}
                aria-invalid={Boolean(formError)}
                aria-describedby={formError ? 'workspace-name-error' : undefined}
                placeholder="e.g. Acme Operations"
              />
              {formError ? <small id="workspace-name-error">{formError}</small> : null}
            </label>
            <button className="primary-button" type="submit" disabled={isCreating}>
              {isCreating ? 'Creating workspace…' : 'Create workspace'}
            </button>
          </form>
        </section>
      </div>
    );
  }

  return (
    <div className="bootstrap-page">
      <section className="page-panel bootstrap-panel">
        <p className="eyebrow">Choose a workspace</p>
        <h1>Where would you like to work?</h1>
        <p>Select a workspace to open its Dashboard.</p>
        <div className="workspace-choice-list">
          {loadState.workspaces.map((workspace) => (
            <button
              className="workspace-choice"
              key={workspace.id}
              onClick={() => {
                persistWorkspaceId(workspace.id);
                navigate(`/app/${workspace.id}/dashboard`);
              }}
              type="button"
            >
              <span>
                <strong>{workspace.name}</strong>
                <small>{workspace.role}</small>
              </span>
              <span aria-hidden="true">→</span>
            </button>
          ))}
        </div>
      </section>
    </div>
  );
}
