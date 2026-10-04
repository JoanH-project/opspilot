export const WORKSPACE_STORAGE_KEY = 'opspilot_last_workspace_id';

export function getStoredWorkspaceId(): number | null {
  const storedValue = window.localStorage.getItem(WORKSPACE_STORAGE_KEY);
  if (!storedValue) {
    return null;
  }

  const workspaceId = Number(storedValue);
  if (Number.isSafeInteger(workspaceId) && workspaceId > 0) {
    return workspaceId;
  }

  window.localStorage.removeItem(WORKSPACE_STORAGE_KEY);
  return null;
}

export function persistWorkspaceId(workspaceId: number | null): void {
  if (workspaceId === null) {
    window.localStorage.removeItem(WORKSPACE_STORAGE_KEY);
    return;
  }

  window.localStorage.setItem(WORKSPACE_STORAGE_KEY, String(workspaceId));
}
