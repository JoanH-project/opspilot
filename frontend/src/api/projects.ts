import { apiRequest } from './client';
import type { Project, ProjectRequest, ProjectStatus, ProjectUpdateRequest } from '../types/project';

export const projectsApi = {
  list: (workspaceId: number, status: ProjectStatus, token: string) =>
    apiRequest<Project[]>(`/api/workspaces/${workspaceId}/projects?status=${status}`, {}, token),

  create: (workspaceId: number, request: ProjectRequest, token: string) =>
    apiRequest<Project>(`/api/workspaces/${workspaceId}/projects`, {
      method: 'POST',
      body: JSON.stringify(request),
    }, token),

  getById: (projectId: number, token: string) =>
    apiRequest<Project>(`/api/projects/${projectId}`, {}, token),

  update: (projectId: number, request: ProjectUpdateRequest, token: string) =>
    apiRequest<Project>(`/api/projects/${projectId}`, {
      method: 'PATCH',
      body: JSON.stringify(request),
    }, token),

  archive: (projectId: number, token: string) =>
    apiRequest<Project>(`/api/projects/${projectId}/archive`, { method: 'POST' }, token),
};
