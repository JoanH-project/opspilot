import { useEffect, useState, type FormEvent, type ReactElement } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { isApiError } from '../../api/errors';
import { workspacesApi } from '../../api/workspaces';
import { workspaceQueryKeys } from '../../queryClient';
import type { WorkspaceMemberResponse, WorkspaceSummary } from '../../types/workspace';
import { getStoredToken } from '../auth/authStorage';
import { useAuth } from '../auth/useAuth';

type WorkspaceDetailsDialogProps = {
  workspace: WorkspaceSummary;
  onClose: () => void;
};

function formatJoinedAt(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(date);
}

function getWorkspaceErrorMessage(error: unknown): string {
  if (isApiError(error)) {
    if (error.status === 403) {
      return 'Only workspace owners and admins can rename this workspace.';
    }
    if (error.status === 404) {
      return 'This workspace is no longer available to your account.';
    }
    return error.message;
  }
  return 'Unable to load or update this workspace.';
}

export function WorkspaceDetailsDialog({
  workspace,
  onClose,
}: WorkspaceDetailsDialogProps): ReactElement {
  const { currentUser, token, logout } = useAuth();
  const queryClient = useQueryClient();
  const [name, setName] = useState(workspace.name);
  const [validationError, setValidationError] = useState('');
  const userId = currentUser?.id;

  const detailsQuery = useQuery({
    queryKey: workspaceQueryKeys.detail(userId ?? 0, workspace.id),
    queryFn: () => workspacesApi.getById(workspace.id, token!),
    enabled: Boolean(token && userId),
  });
  const membersQuery = useQuery({
    queryKey: workspaceQueryKeys.members(userId ?? 0, workspace.id),
    queryFn: () => workspacesApi.listMembers(workspace.id, token!),
    enabled: Boolean(token && userId),
  });
  const renameMutation = useMutation({
    mutationFn: (nextName: string) => workspacesApi.update(workspace.id, { name: nextName }, token!),
    onSuccess: async (updated) => {
      if (!userId || getStoredToken() !== token) {
        return;
      }
      queryClient.setQueryData(workspaceQueryKeys.detail(userId, workspace.id), updated);
      queryClient.setQueryData<WorkspaceSummary[]>(
        workspaceQueryKeys.list(userId),
        (previous) =>
          previous?.map((item) =>
            item.id === workspace.id ? { ...item, name: updated.name, role: updated.role } : item,
          ),
      );
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.list(userId) }),
        queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.dashboard(userId, workspace.id) }),
      ]);
      setName(updated.name);
      setValidationError('');
    },
  });

  useEffect(() => {
    if (
      (detailsQuery.error && isApiError(detailsQuery.error) && detailsQuery.error.status === 401) ||
      (membersQuery.error && isApiError(membersQuery.error) && membersQuery.error.status === 401) ||
      (renameMutation.error &&
        isApiError(renameMutation.error) &&
        renameMutation.error.status === 401)
    ) {
      logout();
    }
  }, [detailsQuery.error, logout, membersQuery.error, renameMutation.error]);

  const workspaceDetails = detailsQuery.data;
  const canRename = workspaceDetails?.role === 'OWNER' || workspaceDetails?.role === 'ADMIN';
  const members: WorkspaceMemberResponse[] = membersQuery.data ?? [];

  const submitRename = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    const normalizedName = name.trim();
    if (!normalizedName) {
      setValidationError('Workspace name is required.');
      return;
    }
    if (normalizedName.length > 100) {
      setValidationError('Workspace name must be 100 characters or fewer.');
      return;
    }
    setValidationError('');
    renameMutation.mutate(normalizedName);
  };

  const error = detailsQuery.error ?? membersQuery.error ?? renameMutation.error;

  return (
    <div className="dialog-backdrop" onMouseDown={(event) => {
      if (event.target === event.currentTarget) {
        onClose();
      }
    }}>
      <section
        aria-labelledby="workspace-details-heading"
        aria-modal="true"
        className="page-panel workspace-dialog"
        role="dialog"
      >
        <header className="workspace-dialog-header">
          <div>
            <p className="eyebrow">Workspace settings</p>
            <h2 id="workspace-details-heading">{workspaceDetails?.name ?? workspace.name}</h2>
          </div>
          <button aria-label="Close workspace details" className="icon-button" onClick={onClose} type="button">
            ×
          </button>
        </header>

        {error ? (
          <div className="form-error-banner" role="alert">
            {getWorkspaceErrorMessage(error)}
          </div>
        ) : null}

        {detailsQuery.isPending ? <p>Loading workspace details…</p> : null}

        {workspaceDetails ? (
          <dl className="workspace-owner">
            <dt>Owner</dt>
            <dd>{workspaceDetails.owner.name} · {workspaceDetails.owner.email}</dd>
          </dl>
        ) : null}

        <section aria-labelledby="workspace-members-heading" className="workspace-members">
          <h3 id="workspace-members-heading">Members</h3>
          {membersQuery.isPending ? <p>Loading members…</p> : null}
          {membersQuery.isSuccess && members.length === 0 ? <p>No members are available.</p> : null}
          {members.length > 0 ? (
            <ul>
              {members.map((member) => (
                <li key={member.user.id}>
                  <span>
                    <strong>{member.user.name}</strong>
                    <small>{member.user.email}</small>
                  </span>
                  <span>{member.role}</span>
                  <time dateTime={member.joinedAt}>{formatJoinedAt(member.joinedAt)}</time>
                </li>
              ))}
            </ul>
          ) : null}
        </section>

        {canRename ? (
          <form className="workspace-rename-form" onSubmit={submitRename} noValidate>
            <label className="field" htmlFor="workspace-rename">
              <span>Rename workspace</span>
              <input
                id="workspace-rename"
                maxLength={100}
                onChange={(event) => setName(event.target.value)}
                value={name}
              />
              {validationError ? <small role="alert">{validationError}</small> : null}
              {renameMutation.isError && isApiError(renameMutation.error) &&
              renameMutation.error.status === 400 &&
              renameMutation.error.fieldErrors.name ? (
                <small role="alert">{renameMutation.error.fieldErrors.name}</small>
              ) : null}
            </label>
            <button className="primary-button" disabled={renameMutation.isPending} type="submit">
              {renameMutation.isPending ? 'Saving…' : 'Save name'}
            </button>
          </form>
        ) : null}

        <footer className="workspace-dialog-footer">
          <button className="secondary-button" onClick={onClose} type="button">Close</button>
        </footer>
      </section>
    </div>
  );
}
