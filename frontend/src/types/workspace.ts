export type WorkspaceRole = 'OWNER' | 'ADMIN' | 'MEMBER';

export type WorkspaceSummary = {
  id: number;
  name: string;
  role: WorkspaceRole;
  createdAt: string;
};

export type WorkspaceOwner = {
  id: number;
  email: string;
  name: string;
};

export type WorkspaceResponse = {
  id: number;
  name: string;
  owner: WorkspaceOwner;
  role: WorkspaceRole;
  createdAt: string;
  updatedAt: string;
};

export type WorkspaceMemberResponse = {
  user: WorkspaceOwner;
  role: WorkspaceRole;
  joinedAt: string;
};

export type UpdateWorkspaceRequest = {
  name: string;
};

export type ActivityResponse = {
  id: number;
  type: string;
  entityType: string;
  entityId: number;
  message: string;
  actor: {
    id: number;
    name: string;
  };
  createdAt: string;
};

export type WorkspaceDashboard = {
  workspaceId: number;
  projects: {
    active: number;
    archived: number;
  };
  tasks: {
    total: number;
    todo: number;
    inProgress: number;
    done: number;
    overdue: number;
  };
  documents: {
    active: number;
    archived: number;
  };
  recentActivities: ActivityResponse[];
};
