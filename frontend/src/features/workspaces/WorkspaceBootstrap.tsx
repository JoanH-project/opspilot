import { useEffect, useMemo, useRef, useState, type FormEvent, type ReactElement } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { getApiErrorMessage, isApiError } from '../../api/errors';
import { workspacesApi } from '../../api/workspaces';
import { workspaceQueryKeys } from '../../queryClient';
import type { WorkspaceSummary } from '../../types/workspace';
import { useAuth } from '../auth/useAuth';
import { getStoredToken } from '../auth/authStorage';
import { clearWorkspaceRejected, isWorkspaceRejected } from './workspaceRecovery';
import { getStoredWorkspaceId, persistWorkspaceId } from './workspaceStorage';

export function WorkspaceBootstrap(): ReactElement {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { token, currentUser, logout } = useAuth();
  const [workspaceName, setWorkspaceName] = useState('');
  const [formError, setFormError] = useState('');
  const currentUserId = useRef(currentUser?.id);
  useEffect(() => {
    currentUserId.current = currentUser?.id;
  }, [currentUser?.id]);
  const userId = currentUser?.id;

  const workspacesQuery = useQuery({
    queryKey: workspaceQueryKeys.list(userId ?? 0),
    queryFn: () => workspacesApi.list(token!),
    enabled: Boolean(token && userId),
    staleTime: 30_000,
  });
  const workspaces = useMemo(() => workspacesQuery.data ?? [], [workspacesQuery.data]);
  const createMutation = useMutation({
    mutationFn: (name: string) => workspacesApi.create({ name }, token!),
    onSuccess: async (created) => {
      if (!userId || currentUserId.current !== userId || getStoredToken() !== token) {
        return;
      }
      const summary: WorkspaceSummary = {
        id: created.id,
        name: created.name,
        role: created.role,
        createdAt: created.createdAt,
      };
      queryClient.setQueryData<WorkspaceSummary[]>(workspaceQueryKeys.list(userId), (previous) => [
        ...(previous ?? []),
        summary,
      ]);
      await queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.list(userId) });
      persistWorkspaceId(created.id, userId);
      navigate(`/app/${created.id}/dashboard`, { replace: true });
    },
  });

  useEffect(() => {
    if (workspacesQuery.error && isApiError(workspacesQuery.error) && workspacesQuery.error.status === 401) {
      logout();
    }
  }, [logout, workspacesQuery.error]);

  useEffect(() => {
    if (!userId || workspacesQuery.isPending || workspacesQuery.isError) {
      return;
    }
    if (workspaces.length === 1 && !isWorkspaceRejected(workspaces[0].id)) {
      persistWorkspaceId(workspaces[0].id, userId);
      navigate(`/app/${workspaces[0].id}/dashboard`, { replace: true });
      return;
    }
    if (workspaces.length > 1) {
      const storedId = getStoredWorkspaceId(userId);
      const storedWorkspace = workspaces.find((workspace) => workspace.id === storedId);
      if (storedWorkspace && !isWorkspaceRejected(storedWorkspace.id)) {
        navigate(`/app/${storedWorkspace.id}/dashboard`, { replace: true });
      }
    }
  }, [navigate, userId, workspaces, workspacesQuery.isError, workspacesQuery.isPending]);

  const submitCreateWorkspace = (event: FormEvent<HTMLFormElement>): void => {
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
    setFormError('');
    createMutation.mutate(name, {
      onError: (error) => {
        if (getStoredToken() !== token) {
          return;
        }
        if (isApiError(error) && error.status === 401) {
          logout();
          return;
        }
        setFormError(getApiErrorMessage(error, 'Unable to create the workspace.'));
      },
    });
  };

  if (workspacesQuery.isPending) {
    return <div className="page-loading">Loading your workspaces…</div>;
  }

  if (workspacesQuery.isError) {
    return (
      <div className="bootstrap-page">
        <section className="page-panel state-panel" role="alert">
          <p className="eyebrow">Workspace setup</p>
          <h1>We couldn’t load your workspaces</h1>
          <p>{getApiErrorMessage(workspacesQuery.error, 'Unable to load your workspaces.')}</p>
          <button
            className="secondary-button"
            onClick={() => void workspacesQuery.refetch()}
            type="button"
          >
            Retry
          </button>
        </section>
      </div>
    );
  }

  const showCreateForm = workspaces.length === 0;
  const rejectedWorkspaces = workspaces.filter((workspace) => isWorkspaceRejected(workspace.id));

  return (
    <div className="bootstrap-page">
      <section className="page-panel bootstrap-panel">
        <p className="eyebrow">{showCreateForm ? 'Welcome to OpsPilot' : 'Choose a workspace'}</p>
        <h1>{showCreateForm ? 'Create your first workspace' : 'Where would you like to work?'}</h1>
        <p>
          {showCreateForm
            ? 'A workspace is where your team’s operations will live.'
            : 'Select a workspace to open its Dashboard.'}
        </p>

        {rejectedWorkspaces.length > 0 ? (
          <div className="form-error-banner" role="status">
            {rejectedWorkspaces.length === 1
              ? `${rejectedWorkspaces[0].name} is unavailable. Retry it or choose another workspace.`
              : 'Some workspaces are unavailable. Choose another workspace or retry access.'}
          </div>
        ) : null}

        {workspaces.length > 0 ? (
          <div className="workspace-choice-list">
            {workspaces.map((workspace) => {
              const rejected = isWorkspaceRejected(workspace.id);
              return (
                <div className="workspace-choice-row" key={workspace.id}>
                  <button
                    className="workspace-choice"
                    disabled={rejected}
                    onClick={() => {
                      persistWorkspaceId(workspace.id, userId);
                      navigate(`/app/${workspace.id}/dashboard`);
                    }}
                    type="button"
                  >
                    <span>
                      <strong>{workspace.name}</strong>
                      <small>{workspace.role}{rejected ? ' · unavailable' : ''}</small>
                    </span>
                    <span aria-hidden="true">→</span>
                  </button>
                  {rejected ? (
                    <button
                      className="secondary-button workspace-retry-button"
                      onClick={() => {
                        clearWorkspaceRejected(workspace.id);
                        persistWorkspaceId(workspace.id, userId);
                        navigate(`/app/${workspace.id}/dashboard`);
                      }}
                      type="button"
                    >
                      Retry access
                    </button>
                  ) : null}
                </div>
              );
            })}
          </div>
        ) : null}

        <form className="auth-form workspace-form" onSubmit={submitCreateWorkspace} noValidate>
          <label className="field" htmlFor="bootstrap-workspace-name">
            <span>{showCreateForm ? 'Workspace name' : 'Create another workspace'}</span>
            <input
              id="bootstrap-workspace-name"
              maxLength={100}
              onChange={(event) => setWorkspaceName(event.target.value)}
              placeholder="e.g. Acme Operations"
              value={workspaceName}
            />
            {formError ? <small role="alert">{formError}</small> : null}
            {createMutation.isError &&
            isApiError(createMutation.error) &&
            createMutation.error.status === 400 &&
            createMutation.error.fieldErrors.name ? (
              <small role="alert">{createMutation.error.fieldErrors.name}</small>
            ) : null}
          </label>
          <button className="primary-button" disabled={createMutation.isPending} type="submit">
            {createMutation.isPending
              ? 'Creating workspace…'
              : showCreateForm
                ? 'Create workspace'
                : 'Create another workspace'}
          </button>
        </form>
      </section>
    </div>
  );
}
