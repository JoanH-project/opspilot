import { apiRequest } from './client';
import type {
  UpdateWorkspaceRequest,
  WorkspaceDashboard,
  WorkspaceMemberResponse,
  WorkspaceResponse,
  WorkspaceSummary,
} from '../types/workspace';

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
    apiRequest<WorkspaceResponse>(
      '/api/workspaces',
      {
        method: 'POST',
        body: JSON.stringify(request),
      },
      token,
    ),

  getById: (workspaceId: number, token: string) =>
    apiRequest<WorkspaceResponse>(
      `/api/workspaces/${workspaceId}`,
      {
        method: 'GET',
      },
      token,
    ),

  update: (workspaceId: number, request: UpdateWorkspaceRequest, token: string) =>
    apiRequest<WorkspaceResponse>(
      `/api/workspaces/${workspaceId}`,
      {
        method: 'PATCH',
        body: JSON.stringify(request),
      },
      token,
    ),

  listMembers: (workspaceId: number, token: string) =>
    apiRequest<WorkspaceMemberResponse[]>(
      `/api/workspaces/${workspaceId}/members`,
      {
        method: 'GET',
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
