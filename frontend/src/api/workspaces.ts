import { apiRequest } from './client';
import type { WorkspaceDashboard, WorkspaceSummary } from '../types/workspace';

export type CreateWorkspaceRequest = {
  name: string;
};

export const workspacesApi = {
  list: (token: string) =>
    apiRequest<WorkspaceSummary[]>(
      '/api/workspaces',
      {
        method: 'GET',
      },
      token,
    ),

  create: (request: CreateWorkspaceRequest, token: string) =>
    apiRequest<WorkspaceSummary>(
      '/api/workspaces',
      {
        method: 'POST',
        body: JSON.stringify(request),
      },
      token,
    ),

  getDashboard: (workspaceId: number, token: string) =>
    apiRequest<WorkspaceDashboard>(
      `/api/workspaces/${workspaceId}/dashboard`,
      {
        method: 'GET',
      },
      token,
    ),
};
