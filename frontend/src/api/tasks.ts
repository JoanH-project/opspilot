import { apiRequest } from './client';
import type { CreateTaskRequest, Task, TaskFilters, TaskStatus, UpdateTaskRequest } from '../types/task';

export const tasksApi = {
  list: (projectId: number, filters: TaskFilters, token: string) => {
    const params = new URLSearchParams();
    if (filters.status) params.set('status', filters.status);
    if (filters.priority) params.set('priority', filters.priority);
    if (filters.assigneeId !== undefined) params.set('assigneeId', String(filters.assigneeId));
    const query = params.size > 0 ? `?${params.toString()}` : '';
    return apiRequest<Task[]>(`/api/projects/${projectId}/tasks${query}`, {}, token);
  },

  create: (projectId: number, request: CreateTaskRequest, token: string) =>
    apiRequest<Task>(`/api/projects/${projectId}/tasks`, {
      method: 'POST',
      body: JSON.stringify(request),
    }, token),

  update: (taskId: number, request: UpdateTaskRequest, token: string) =>
    apiRequest<Task>(`/api/tasks/${taskId}`, {
      method: 'PATCH',
      body: JSON.stringify(request),
    }, token),

  updateStatus: (taskId: number, status: TaskStatus, token: string) =>
    apiRequest<Task>(`/api/tasks/${taskId}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    }, token),
};
