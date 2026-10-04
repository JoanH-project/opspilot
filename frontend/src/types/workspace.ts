export type WorkspaceRole = 'OWNER' | 'ADMIN' | 'MEMBER';

export type WorkspaceSummary = {
  id: number;
  name: string;
  role: WorkspaceRole;
  createdAt: string;
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
