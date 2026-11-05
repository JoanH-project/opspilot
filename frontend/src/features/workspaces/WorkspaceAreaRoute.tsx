import { useEffect, type ReactElement, type ReactNode } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';

import { getApiErrorMessage, isApiError } from '../../api/errors';
import { workspacesApi } from '../../api/workspaces';
import { AppShell } from '../../layouts/AppShell';
import { workspaceQueryKeys } from '../../queryClient';
import type { WorkspaceSummary } from '../../types/workspace';
import { useAuth } from '../auth/useAuth';
import { persistWorkspaceId } from './workspaceStorage';

export function WorkspaceAreaRoute({
  children,
}: {
  children: (workspace: WorkspaceSummary) => ReactNode;
}): ReactElement {
  const { workspaceId: routeId } = useParams();
  const { token, currentUser, logout } = useAuth();
  const workspaceId = routeId && /^[1-9]\d*$/.test(routeId) ? Number(routeId) : null;
  const userId = currentUser?.id;
  const workspacesQuery = useQuery({
    queryKey: workspaceQueryKeys.list(userId ?? 0),
    queryFn: () => workspacesApi.list(token!),
    enabled: Boolean(token && userId && Number.isSafeInteger(workspaceId)),
    staleTime: 30_000,
  });
  const workspaces = workspacesQuery.data ?? [];
  const currentWorkspace = workspaces.find((workspace) => workspace.id === workspaceId);

  useEffect(() => {
    if (workspacesQuery.error && isApiError(workspacesQuery.error) && workspacesQuery.error.status === 401) {
      logout();
    }
  }, [logout, workspacesQuery.error]);

  useEffect(() => {
    if (currentWorkspace && userId) {
      persistWorkspaceId(currentWorkspace.id, userId);
    }
  }, [currentWorkspace, userId]);

  if (workspaceId === null || !Number.isSafeInteger(workspaceId)) {
    return <Navigate to="/app" replace />;
  }

  if (workspacesQuery.isError) {
    return (
      <div className="bootstrap-page">
        <section className="page-panel state-panel" role="alert">
          <p className="eyebrow">Workspace</p>
          <h1>We couldn’t load this workspace</h1>
          <p>{getApiErrorMessage(workspacesQuery.error, 'Unable to load your workspaces.')}</p>
          <button className="secondary-button" onClick={() => void workspacesQuery.refetch()} type="button">
            Retry
          </button>
        </section>
      </div>
    );
  }

  if (workspacesQuery.isPending || !workspacesQuery.data) {
    return <div className="page-loading">Loading your workspace…</div>;
  }

  if (!currentWorkspace) {
    return <Navigate to="/app" replace />;
  }

  return (
    <AppShell workspaces={workspaces} currentWorkspace={currentWorkspace}>
      {children(currentWorkspace)}
    </AppShell>
  );
}
