import { useEffect, type ReactElement } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';

import { isApiError } from '../../api/errors';
import { workspacesApi } from '../../api/workspaces';
import { AppShell } from '../../layouts/AppShell';
import { DashboardPage } from '../../pages/DashboardPage';
import { workspaceQueryKeys } from '../../queryClient';
import { useAuth } from '../auth/useAuth';
import { clearWorkspaceRejected, isWorkspaceRejected, markWorkspaceRejected } from './workspaceRecovery';
import { getStoredWorkspaceId, persistWorkspaceId } from './workspaceStorage';

function parseWorkspaceId(value: string | undefined): number | null {
  if (!value || !/^[1-9]\d*$/.test(value)) {
    return null;
  }
  const workspaceId = Number(value);
  return Number.isSafeInteger(workspaceId) ? workspaceId : null;
}

export function WorkspaceDashboardRoute(): ReactElement {
  const { workspaceId: routeWorkspaceId } = useParams();
  const navigate = useNavigate();
  const { token, currentUser, logout } = useAuth();
  const parsedWorkspaceId = parseWorkspaceId(routeWorkspaceId);
  const userId = currentUser?.id;

  const workspacesQuery = useQuery({
    queryKey: workspaceQueryKeys.list(userId ?? 0),
    queryFn: () => workspacesApi.list(token!),
    enabled: Boolean(token && userId),
    staleTime: 30_000,
  });
  const workspaces = workspacesQuery.data ?? [];
  const currentWorkspace = workspaces.find((workspace) => workspace.id === parsedWorkspaceId);
  const dashboardQuery = useQuery({
    queryKey: workspaceQueryKeys.dashboard(userId ?? 0, parsedWorkspaceId ?? 0),
    queryFn: () => workspacesApi.getDashboard(parsedWorkspaceId!, token!),
    enabled: Boolean(token && userId && currentWorkspace && !isWorkspaceRejected(parsedWorkspaceId!)),
  });

  useEffect(() => {
    if (parsedWorkspaceId === null || routeWorkspaceId === undefined) {
      navigate('/app', { replace: true });
    }
  }, [navigate, parsedWorkspaceId, routeWorkspaceId]);

  useEffect(() => {
    if (workspacesQuery.error && isApiError(workspacesQuery.error) && workspacesQuery.error.status === 401) {
      logout();
    }
  }, [logout, workspacesQuery.error]);

  useEffect(() => {
    if (!workspacesQuery.isSuccess || parsedWorkspaceId === null || !userId) {
      return;
    }
    if (!currentWorkspace) {
      if (getStoredWorkspaceId(userId) === parsedWorkspaceId) {
        persistWorkspaceId(null, userId);
      }
      navigate('/app', { replace: true });
      return;
    }
    if (!isWorkspaceRejected(parsedWorkspaceId)) {
      persistWorkspaceId(parsedWorkspaceId, userId);
    }
  }, [currentWorkspace, navigate, parsedWorkspaceId, userId, workspacesQuery.isSuccess]);

  useEffect(() => {
    const error = dashboardQuery.error;
    if (!error || parsedWorkspaceId === null || !userId) {
      return;
    }
    if (isApiError(error) && error.status === 401) {
      logout();
      return;
    }
    if (isApiError(error) && error.status === 404) {
      markWorkspaceRejected(parsedWorkspaceId);
      if (getStoredWorkspaceId(userId) === parsedWorkspaceId) {
        persistWorkspaceId(null, userId);
      }
    }
  }, [dashboardQuery.error, logout, parsedWorkspaceId, userId]);

  const dashboard = dashboardQuery.data;
  const dashboardMismatch =
    dashboard !== undefined && dashboard.workspaceId !== parsedWorkspaceId;
  const unavailable =
    (parsedWorkspaceId !== null && isWorkspaceRejected(parsedWorkspaceId)) ||
    (isApiError(dashboardQuery.error) && dashboardQuery.error.status === 404);
  const errorMessage = unavailable
    ? 'This workspace is unavailable right now. Choose another workspace or retry access.'
    : isApiError(dashboardQuery.error)
      ? dashboardQuery.error.message
      : dashboardMismatch
        ? 'The Dashboard response did not match the selected workspace.'
        : 'Unable to load this workspace Dashboard.';

  const retryDashboard = (): void => {
    if (parsedWorkspaceId !== null) {
      clearWorkspaceRejected(parsedWorkspaceId);
    }
    void dashboardQuery.refetch();
  };

  if (workspacesQuery.isPending || !workspacesQuery.data) {
    return <div className="page-loading">Loading your workspace…</div>;
  }

  if (workspacesQuery.isError) {
    return (
      <div className="page-loading">
        <section className="page-panel state-panel" role="alert">
          <p className="eyebrow">Workspace</p>
          <h1>We couldn’t load this workspace</h1>
          <p>{isApiError(workspacesQuery.error) ? workspacesQuery.error.message : 'Unable to load your workspaces.'}</p>
          <button className="secondary-button" onClick={() => void workspacesQuery.refetch()} type="button">
            Retry
          </button>
        </section>
      </div>
    );
  }

  if (parsedWorkspaceId === null || !currentWorkspace) {
    return <div className="page-loading">Returning to workspace selection…</div>;
  }

  return (
    <AppShell workspaces={workspaces} currentWorkspace={currentWorkspace}>
      {dashboardQuery.isError || dashboardMismatch || unavailable ? (
        <section className="page-panel state-panel" role="alert">
          <p className="eyebrow">Dashboard</p>
          <h1>Dashboard unavailable</h1>
          <p>{errorMessage}</p>
          <button className="secondary-button" onClick={retryDashboard} type="button">
            Retry access
          </button>
          {unavailable ? (
            <button className="secondary-button" onClick={() => navigate('/app')} type="button">
              Choose another workspace
            </button>
          ) : null}
        </section>
      ) : dashboard ? (
        <DashboardPage dashboard={dashboard} workspace={currentWorkspace} />
      ) : (
        <section className="page-panel dashboard-loading" aria-live="polite">
          <p className="eyebrow">Overview</p>
          <h1>{currentWorkspace.name}</h1>
          <p>Loading Dashboard data…</p>
        </section>
      )}
      {unavailable ? (
        <p className="workspace-recovery-hint" role="status">
          Your workspace list is still available above. Switch to another workspace or retry access.
        </p>
      ) : null}
    </AppShell>
  );
}
