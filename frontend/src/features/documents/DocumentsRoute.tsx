import { useState, type FormEvent, type ReactElement } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Markdown from 'react-markdown';

import { documentsApi } from '../../api/documents';
import { getApiErrorMessage } from '../../api/errors';
import { useAuth } from '../../features/auth/useAuth';
import { useLogoutOnUnauthorized } from '../../features/auth/useLogoutOnUnauthorized';
import { WorkspaceAreaRoute } from '../../features/workspaces/WorkspaceAreaRoute';
import { documentQueryKeys, workspaceQueryKeys } from '../../queryClient';
import type { Document, DocumentStatus } from '../../types/document';
import type { WorkspaceSummary } from '../../types/workspace';

export function DocumentsRoute(): ReactElement {
  return <WorkspaceAreaRoute>{(workspace) => <DocumentsPage workspace={workspace} />}</WorkspaceAreaRoute>;
}

function DocumentsPage({ workspace }: { workspace: WorkspaceSummary }): ReactElement {
  const { token, currentUser } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<DocumentStatus>('ACTIVE');
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [formError, setFormError] = useState('');
  const userId = currentUser?.id ?? 0;
  const documentsQuery = useQuery({
    queryKey: documentQueryKeys.list(userId, workspace.id, status),
    queryFn: () => documentsApi.list(workspace.id, status, token!),
    enabled: Boolean(token && currentUser),
  });
  const createMutation = useMutation({
    mutationFn: () => documentsApi.create(workspace.id, { title: title.trim(), content }, token!),
    onSuccess: async (created) => {
      setTitle('');
      setContent('');
      setFormError('');
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['documents', userId, workspace.id] }),
        queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.dashboard(userId, workspace.id) }),
      ]);
      navigate(`/app/${workspace.id}/documents/${created.id}`);
    },
  });
  useLogoutOnUnauthorized(documentsQuery.error);
  useLogoutOnUnauthorized(createMutation.error);
  const createDocument = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    if (!title.trim()) {
      setFormError('Document title is required.');
      return;
    }
    setFormError('');
    createMutation.mutate(undefined, {
      onError: (error) => setFormError(getApiErrorMessage(error, 'Unable to create the document.')),
    });
  };

  return (
    <div className="resource-page">
      <header className="resource-header">
        <div>
          <p className="eyebrow">Workspace knowledge</p>
          <h1>Documents</h1>
          <p>Keep runbooks and operating notes for {workspace.name} in one place.</p>
        </div>
        <label className="filter-field">
          <span>Document status</span>
          <select aria-label="Document status" onChange={(event) => setStatus(event.target.value as DocumentStatus)} value={status}>
            <option value="ACTIVE">Active</option>
            <option value="ARCHIVED">Archived</option>
          </select>
        </label>
      </header>

      {status === 'ACTIVE' ? (
        <section className="resource-card" aria-labelledby="new-document-heading">
          <h2 id="new-document-heading">Create a document</h2>
          <form className="resource-form" onSubmit={createDocument} noValidate>
            <label className="field"><span>Document title</span><input maxLength={200} value={title} onChange={(event) => setTitle(event.target.value)} /></label>
            <label className="field"><span>Markdown content</span><textarea aria-label="Markdown content" maxLength={1_000_000} rows={6} value={content} onChange={(event) => setContent(event.target.value)} /></label>
            {formError ? <p className="form-error-banner" role="alert">{formError}</p> : null}
            <button className="primary-button" disabled={createMutation.isPending} type="submit">{createMutation.isPending ? 'Creating…' : 'Create document'}</button>
          </form>
        </section>
      ) : null}

      <section className="resource-card" aria-labelledby="document-list-heading">
        <div className="section-heading">
          <div><p className="eyebrow">{status === 'ACTIVE' ? 'Available' : 'Archived'}</p><h2 id="document-list-heading">{status === 'ACTIVE' ? 'Active documents' : 'Archived documents'}</h2></div>
          <span className="activity-limit">{documentsQuery.data?.length ?? 0} total</span>
        </div>
        {documentsQuery.isPending ? <p role="status">Loading documents…</p> : null}
        {documentsQuery.isError ? <div className="inline-error" role="alert"><p>{getApiErrorMessage(documentsQuery.error, 'Unable to load documents.')}</p><button className="secondary-button" onClick={() => void documentsQuery.refetch()} type="button">Retry</button></div> : null}
        {documentsQuery.isSuccess && documentsQuery.data.length === 0 ? <p className="empty-state">{status === 'ACTIVE' ? 'No active documents yet.' : 'No archived documents.'}</p> : null}
        <div className="resource-list">
          {documentsQuery.data?.map((document) => (
            <article className="resource-row" key={document.id}>
              <div className="resource-copy">
                <div className="resource-title-line">
                  <Link className="resource-title" to={`/app/${workspace.id}/documents/${document.id}`}>{document.title}</Link>
                  <span className={`status-badge ${document.status.toLowerCase()}`}>{document.status.toLowerCase()}</span>
                </div>
                <small>Updated {new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(document.updatedAt))} · {document.creator.name}</small>
              </div>
              <Link className="secondary-button button-link" to={`/app/${workspace.id}/documents/${document.id}`}>Open</Link>
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}

export function DocumentDetailRoute(): ReactElement {
  const { documentId: rawDocumentId } = useParams();
  const documentId = rawDocumentId && /^[1-9]\d*$/.test(rawDocumentId) ? Number(rawDocumentId) : null;
  return (
    <WorkspaceAreaRoute>
      {(workspace) => documentId ? <DocumentDetailPage workspace={workspace} documentId={documentId} /> : <Navigate to={`/app/${workspace.id}/documents`} replace />}
    </WorkspaceAreaRoute>
  );
}

function DocumentDetailPage({ workspace, documentId }: { workspace: WorkspaceSummary; documentId: number }): ReactElement {
  const { token, currentUser } = useAuth();
  const queryClient = useQueryClient();
  const [isEditing, setIsEditing] = useState(false);
  const [isPreview, setIsPreview] = useState(false);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [formError, setFormError] = useState('');
  const userId = currentUser?.id ?? 0;
  const detailKey = documentQueryKeys.detail(userId, documentId);
  const documentQuery = useQuery({
    queryKey: detailKey,
    queryFn: () => documentsApi.getById(documentId, token!),
    enabled: Boolean(token && currentUser),
  });
  const document = documentQuery.data;
  const canManage = Boolean(document && currentUser && (
    ['OWNER', 'ADMIN'].includes(workspace.role) || document.creator.id === currentUser.id
  ));
  const afterWrite = async (updated: Document): Promise<void> => {
    queryClient.setQueryData(detailKey, updated);
    setIsEditing(false);
    setIsPreview(false);
    setFormError('');
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['documents', userId, workspace.id] }),
      queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.dashboard(userId, workspace.id) }),
    ]);
  };
  const updateMutation = useMutation({
    mutationFn: () => documentsApi.update(documentId, { title: title.trim(), content }, token!),
    onSuccess: afterWrite,
  });
  const archiveMutation = useMutation({
    mutationFn: () => documentsApi.archive(documentId, token!),
    onSuccess: afterWrite,
  });
  useLogoutOnUnauthorized(documentQuery.error);
  useLogoutOnUnauthorized(updateMutation.error);
  useLogoutOnUnauthorized(archiveMutation.error);

  if (documentQuery.isPending) return <p role="status">Loading document…</p>;
  if (documentQuery.isError || !document || document.workspaceId !== workspace.id) {
    return <section className="resource-card inline-error" role="alert"><h1>Document unavailable</h1><p>{getApiErrorMessage(documentQuery.error, 'This document is not available in the selected workspace.')}</p><button className="secondary-button" onClick={() => void documentQuery.refetch()} type="button">Retry</button></section>;
  }

  const isArchived = document.status === 'ARCHIVED';
  const beginEditing = (): void => {
    setTitle(document.title);
    setContent(document.content);
    setIsPreview(false);
    setFormError('');
    setIsEditing(true);
  };
  const saveDocument = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    if (!title.trim()) {
      setFormError('Document title is required.');
      return;
    }
    setFormError('');
    updateMutation.mutate(undefined, {
      onError: (error) => setFormError(getApiErrorMessage(error, 'Unable to save the document.')),
    });
  };

  return (
    <div className="resource-page">
      <header className="resource-header document-detail-header">
        <div>
          <Link className="back-link" to={`/app/${workspace.id}/documents`}>← Documents</Link>
          <p className="eyebrow">{isArchived ? 'Archived document' : 'Workspace document'}</p>
          <h1>{document.title}</h1>
          <p>By {document.creator.name} · Updated {new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(document.updatedAt))}</p>
        </div>
        {canManage && !isArchived ? <div className="resource-actions">
          {!isEditing ? <button className="secondary-button" onClick={beginEditing} type="button">Edit document</button> : null}
          <button className="secondary-button danger-button" disabled={archiveMutation.isPending} onClick={() => {
            if (window.confirm(`Archive “${document.title}”? It will remain readable but become read-only.`)) {
              archiveMutation.mutate(undefined, { onError: (error) => setFormError(getApiErrorMessage(error, 'Unable to archive the document.')) });
            }
          }} type="button">{archiveMutation.isPending ? 'Archiving…' : 'Archive'}</button>
        </div> : null}
      </header>

      {isArchived ? <p className="form-error-banner" role="status">This document is archived and read-only.</p> : null}

      {isEditing ? (
        <form className="resource-card resource-form document-editor" onSubmit={saveDocument} noValidate>
          <label className="field"><span>Document title</span><input maxLength={200} value={title} onChange={(event) => setTitle(event.target.value)} /></label>
          <div className="editor-toolbar" role="tablist" aria-label="Document content mode">
            <button aria-selected={!isPreview} className={isPreview ? 'secondary-button' : 'tab-button active'} onClick={() => setIsPreview(false)} role="tab" type="button">Edit</button>
            <button aria-selected={isPreview} className={isPreview ? 'tab-button active' : 'secondary-button'} onClick={() => setIsPreview(true)} role="tab" type="button">Preview</button>
          </div>
          {isPreview ? (
            <div className="markdown-preview" aria-label="Document preview"><Markdown skipHtml>{content}</Markdown></div>
          ) : (
            <label className="field"><span>Markdown content</span><textarea aria-label="Markdown content" maxLength={1_000_000} rows={18} value={content} onChange={(event) => setContent(event.target.value)} /></label>
          )}
          {formError ? <p className="form-error-banner" role="alert">{formError}</p> : null}
          <div className="resource-actions"><button className="primary-button" disabled={updateMutation.isPending} type="submit">{updateMutation.isPending ? 'Saving…' : 'Save document'}</button><button className="secondary-button" onClick={() => { setIsEditing(false); setFormError(''); }} type="button">Cancel</button></div>
        </form>
      ) : (
        <article className="resource-card document-content markdown-preview"><Markdown skipHtml>{document.content}</Markdown></article>
      )}
      {formError && !isEditing ? <p className="form-error-banner" role="alert">{formError}</p> : null}
    </div>
  );
}
