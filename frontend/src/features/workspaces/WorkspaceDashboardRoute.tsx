import { useEffect, useState, type ReactElement } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import { workspacesApi } from '../../api/workspaces';
import { isApiError } from '../../api/errors';
import { useAuth } from '../auth/useAuth';
import { getStoredWorkspaceId, persistWorkspaceId } from './workspaceStorage';
import { AppShell } from '../../layouts/AppShell';
import { DashboardPage } from '../../pages/DashboardPage';
import type { WorkspaceDashboard, WorkspaceSummary } from '../../types/workspace';

type RouteState = {
  routeWorkspaceId: string;
  workspaces: WorkspaceSummary[] | null;
  dashboard: WorkspaceDashboard | null;
  error: string | null;
  reloadKey: number;
};

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
  const { token, refreshSession } = useAuth();
  const parsedWorkspaceId = parseWorkspaceId(routeWorkspaceId);
  const [reloadKey, setReloadKey] = useState(0);
  const [routeState, setRouteState] = useState<RouteState>({
    routeWorkspaceId: '',
    workspaces: null,
    dashboard: null,
    error: null,
    reloadKey: -1,
  });

  useEffect(() => {
    if (!token) {
      return;
    }

    if (parsedWorkspaceId === null || routeWorkspaceId === undefined) {
      navigate('/app', { replace: true });
      return;
    }

    let isActive = true;

    void workspacesApi
      .list(token)
      .then(async (workspaces) => {
        if (!isActive) {
          return;
        }

        const workspace = workspaces.find((item) => item.id === parsedWorkspaceId);
        if (!workspace) {
          if (getStoredWorkspaceId() === parsedWorkspaceId) {
            persistWorkspaceId(null);
          }
          navigate('/app', { replace: true });
          return;
        }

        persistWorkspaceId(workspace.id);
        setRouteState({
          routeWorkspaceId,
          workspaces,
          dashboard: null,
          error: null,
          reloadKey,
        });

        try {
          const dashboard = await workspacesApi.getDashboard(parsedWorkspaceId, token);
          if (isActive) {
            if (dashboard.workspaceId !== parsedWorkspaceId) {
              setRouteState({
                routeWorkspaceId,
                workspaces,
                dashboard: null,
                error: 'The Dashboard response did not match the selected workspace.',
                reloadKey,
              });
              return;
            }

            setRouteState({
              routeWorkspaceId,
              workspaces,
              dashboard,
              error: null,
              reloadKey,
            });
          }
        } catch (error) {
          if (!isActive) {
            return;
          }

          if (isApiError(error) && error.status === 401) {
            await refreshSession();
            return;
          }

          if (isApiError(error) && error.status === 404) {
            if (getStoredWorkspaceId() === parsedWorkspaceId) {
              persistWorkspaceId(null);
            }
            navigate('/app', { replace: true });
            return;
          }

          setRouteState({
            routeWorkspaceId,
            workspaces,
            dashboard: null,
            error: isApiError(error) ? error.message : 'Unable to load this workspace Dashboard.',
            reloadKey,
          });
        }
      })
      .catch(async (error: unknown) => {
        if (!isActive) {
          return;
        }

        if (isApiError(error) && error.status === 401) {
          await refreshSession();
          return;
        }

        setRouteState({
          routeWorkspaceId,
          workspaces: null,
          dashboard: null,
          error: isApiError(error) ? error.message : 'Unable to load your workspaces.',
          reloadKey,
        });
      });

    return () => {
      isActive = false;
    };
  }, [navigate, parsedWorkspaceId, refreshSession, reloadKey, routeWorkspaceId, token]);

  const isCurrentRoute =
    routeState.routeWorkspaceId === routeWorkspaceId && routeState.reloadKey === reloadKey;
  const workspaces = isCurrentRoute ? routeState.workspaces : null;
  const dashboard = isCurrentRoute ? routeState.dashboard : null;
  const error = isCurrentRoute ? routeState.error : null;
  const currentWorkspace = workspaces?.find((workspace) => workspace.id === parsedWorkspaceId);

  if (!workspaces || !currentWorkspace) {
    if (error) {
      return (
        <div className="page-loading">
          <section className="page-panel state-panel" role="alert">
            <p className="eyebrow">Workspace</p>
            <h1>We couldn’t load this workspace</h1>
            <p>{error}</p>
            <button className="secondary-button" onClick={() => setReloadKey((current) => current + 1)}>
              Retry
            </button>
          </section>
        </div>
      );
    }

    return <div className="page-loading">Loading your workspace…</div>;
  }

  return (
    <AppShell workspaces={workspaces} currentWorkspace={currentWorkspace}>
      {error ? (
        <section className="page-panel state-panel" role="alert">
          <p className="eyebrow">Dashboard</p>
          <h1>Dashboard unavailable</h1>
          <p>{error}</p>
          <button className="secondary-button" onClick={() => setReloadKey((current) => current + 1)}>
            Retry
          </button>
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
    </AppShell>
  );
}
