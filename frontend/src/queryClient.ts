import { QueryClient } from '@tanstack/react-query';

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
