import { QueryClient } from '@tanstack/react-query';
import type { DocumentStatus } from './types/document';
import type { ProjectStatus } from './types/project';
import type { TaskFilters } from './types/task';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: false,
    },
  },
});

export const workspaceQueryKeys = {
  list: (userId: number) => ['workspaces', userId] as const,
  detail: (userId: number, workspaceId: number) => ['workspace', userId, workspaceId] as const,
  members: (userId: number, workspaceId: number) =>
    ['workspace-members', userId, workspaceId] as const,
  dashboard: (userId: number, workspaceId: number) =>
    ['workspace-dashboard', userId, workspaceId] as const,
};

export const projectQueryKeys = {
  list: (userId: number, workspaceId: number, status: ProjectStatus) =>
    ['projects', userId, workspaceId, status] as const,
  detail: (userId: number, projectId: number) => ['project', userId, projectId] as const,
};

export const taskQueryKeys = {
  list: (userId: number, projectId: number, filters: TaskFilters) =>
    ['tasks', userId, projectId, filters] as const,
};

export const documentQueryKeys = {
  list: (userId: number, workspaceId: number, status: DocumentStatus) =>
    ['documents', userId, workspaceId, status] as const,
  detail: (userId: number, documentId: number) => ['document', userId, documentId] as const,
};
