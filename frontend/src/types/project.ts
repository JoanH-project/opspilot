export type ProjectStatus = 'ACTIVE' | 'ARCHIVED';

export type Project = {
  id: number;
  workspaceId: number;
  name: string;
  description: string | null;
  status: ProjectStatus;
  creator: { id: number; email: string; name: string };
  createdAt: string;
  updatedAt: string;
};

export type ProjectRequest = {
  name: string;
  description?: string;
};

export type ProjectUpdateRequest = {
  name?: string;
  description?: string;
};
