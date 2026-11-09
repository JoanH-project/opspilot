import { apiRequest } from './client';
import type {
  KnowledgeAnswerResponse,
  KnowledgeIndexStatus,
  KnowledgeReindexResponse,
  KnowledgeSearchResponse,
} from '../types/knowledge';

export const knowledgeApi = {
  ask: (workspaceId: number, question: string, token: string, signal?: AbortSignal) =>
    apiRequest<KnowledgeAnswerResponse>(`/api/workspaces/${workspaceId}/knowledge/ask`, {
      method: 'POST',
      body: JSON.stringify({ question }),
      signal,
    }, token),

  search: (workspaceId: number, query: string, token: string, signal?: AbortSignal) =>
    apiRequest<KnowledgeSearchResponse>(`/api/workspaces/${workspaceId}/knowledge/search`, {
      method: 'POST',
      body: JSON.stringify({ query, limit: 5 }),
      signal,
    }, token),

  indexStatus: (workspaceId: number, token: string, signal?: AbortSignal) =>
    apiRequest<KnowledgeIndexStatus>(
      `/api/workspaces/${workspaceId}/knowledge/index-status`,
      { signal },
      token,
    ),

  reindex: (workspaceId: number, token: string) =>
    apiRequest<KnowledgeReindexResponse>(`/api/workspaces/${workspaceId}/knowledge/reindex`, {
      method: 'POST',
    }, token),
};
