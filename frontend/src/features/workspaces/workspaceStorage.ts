const LEGACY_WORKSPACE_STORAGE_KEY = 'opspilot_last_workspace_id';
const USER_WORKSPACE_STORAGE_KEY_PREFIX = 'opspilot_last_workspace_id_user_';

function getStorageKeyForUser(userId?: number | null): string | null {
  if (!userId || !Number.isSafeInteger(userId) || userId <= 0) {
    return null;
  }

  return `${USER_WORKSPACE_STORAGE_KEY_PREFIX}${userId}`;
}

export function getStoredWorkspaceId(userId?: number | null): number | null {
  const storageKey = getStorageKeyForUser(userId);
  window.localStorage.removeItem(LEGACY_WORKSPACE_STORAGE_KEY);
  if (!storageKey) {
    return null;
  }
  const storedValue = window.localStorage.getItem(storageKey);
  if (!storedValue) {
    return null;
  }

  const workspaceId = Number(storedValue);
  if (Number.isSafeInteger(workspaceId) && workspaceId > 0) {
    return workspaceId;
  }

  window.localStorage.removeItem(storageKey);
  return null;
}

export function persistWorkspaceId(workspaceId: number | null, userId?: number | null): void {
  const storageKey = getStorageKeyForUser(userId);
  window.localStorage.removeItem(LEGACY_WORKSPACE_STORAGE_KEY);

  if (workspaceId === null && storageKey) {
    window.localStorage.removeItem(storageKey);
    return;
  }

  if (workspaceId !== null && storageKey) {
    window.localStorage.setItem(storageKey, String(workspaceId));
  }
}
