const rejectedWorkspaceIds = new Set<number>();

export function clearRejectedWorkspaces(): void {
  rejectedWorkspaceIds.clear();
}

export function markWorkspaceRejected(workspaceId: number): void {
  rejectedWorkspaceIds.add(workspaceId);
}

export function clearWorkspaceRejected(workspaceId: number): void {
  rejectedWorkspaceIds.delete(workspaceId);
}

export function isWorkspaceRejected(workspaceId: number): boolean {
  return rejectedWorkspaceIds.has(workspaceId);
}
