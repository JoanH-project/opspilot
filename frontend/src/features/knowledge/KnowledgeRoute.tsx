import { useEffect, useState, type FormEvent, type ReactElement } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';

import { getApiErrorMessage, isApiError } from '../../api/errors';
import { knowledgeApi } from '../../api/knowledge';
import { useAuth } from '../auth/useAuth';
import { useLogoutOnUnauthorized } from '../auth/useLogoutOnUnauthorized';
import { WorkspaceAreaRoute } from '../workspaces/WorkspaceAreaRoute';
import { knowledgeQueryKeys } from '../../queryClient';
import type { KnowledgeReference } from '../../types/knowledge';
import type { WorkspaceSummary } from '../../types/workspace';

export function KnowledgeRoute(): ReactElement {
  return (
    <WorkspaceAreaRoute>
      {(workspace) => <KnowledgePage workspace={workspace} />}
    </WorkspaceAreaRoute>
  );
}

export function KnowledgePage({ workspace }: { workspace: WorkspaceSummary }): ReactElement {
  const { currentUser } = useAuth();
  const scopeKey = `${currentUser?.id ?? 0}:${workspace.id}`;
  return <KnowledgePageContent key={scopeKey} workspace={workspace} />;
}

function KnowledgePageContent({ workspace }: { workspace: WorkspaceSummary }): ReactElement {
  const { token, currentUser, logout } = useAuth();
  const queryClient = useQueryClient();
  const userId = currentUser?.id ?? 0;
  const workspaceId = workspace.id;
  const [question, setQuestion] = useState('');
  const [submittedQuestion, setSubmittedQuestion] = useState('');
  const [searchText, setSearchText] = useState('');
  const [submittedSearch, setSubmittedSearch] = useState('');
  const [reindexMessage, setReindexMessage] = useState('');
  const [isReindexing, setIsReindexing] = useState(false);
  const canReindex = workspace.role === 'OWNER' || workspace.role === 'ADMIN';

  useEffect(() => {
    const key = knowledgeQueryKeys.workspace(userId, workspaceId);
    return () => {
      void queryClient.cancelQueries({ queryKey: key });
      queryClient.removeQueries({ queryKey: key });
    };
  }, [queryClient, userId, workspaceId]);

  const statusQuery = useQuery({
    queryKey: knowledgeQueryKeys.status(userId, workspaceId),
    queryFn: ({ signal }) => knowledgeApi.indexStatus(workspaceId, token!, signal),
    enabled: Boolean(token && currentUser),
    staleTime: 15_000,
  });
  const answerQuery = useQuery({
    queryKey: knowledgeQueryKeys.ask(userId, workspaceId, submittedQuestion),
    queryFn: ({ signal }) => knowledgeApi.ask(workspaceId, submittedQuestion, token!, signal),
    enabled: Boolean(token && currentUser && submittedQuestion),
  });
  const searchQuery = useQuery({
    queryKey: knowledgeQueryKeys.search(userId, workspaceId, submittedSearch),
    queryFn: ({ signal }) => knowledgeApi.search(workspaceId, submittedSearch, token!, signal),
    enabled: Boolean(token && currentUser && submittedSearch),
  });
  useLogoutOnUnauthorized(statusQuery.error);
  useLogoutOnUnauthorized(answerQuery.error);
  useLogoutOnUnauthorized(searchQuery.error);

  const submitQuestion = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    const value = question.trim();
    if (!value) return;
    setReindexMessage('');
    setSubmittedQuestion(value);
  };

  const submitSearch = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    const value = searchText.trim();
    if (value) setSubmittedSearch(value);
  };

  const retryAnswer = (): void => {
    if (submittedQuestion) void answerQuery.refetch();
  };

  const retrySearch = (): void => {
    if (submittedSearch) void searchQuery.refetch();
  };

  const triggerReindex = async (): Promise<void> => {
    setReindexMessage('');
    setIsReindexing(true);
    try {
      const result = await knowledgeApi.reindex(workspaceId, token!);
      setReindexMessage(`Reindex queued for ${result.queuedDocuments} active document(s).`);
      await queryClient.invalidateQueries({ queryKey: knowledgeQueryKeys.status(userId, workspaceId) });
    } catch (error) {
      if (isApiError(error) && error.status === 401) logout();
      setReindexMessage(getApiErrorMessage(error, 'Unable to start a reindex.'));
    } finally {
      setIsReindexing(false);
    }
  };

  return (
    <div className="resource-page knowledge-page">
      <header className="resource-header">
        <div>
          <p className="eyebrow">Workspace knowledge</p>
          <h1>Knowledge</h1>
          <p>Ask questions using active documents from {workspace.name}.</p>
        </div>
      </header>

      <section className="resource-card knowledge-status" aria-labelledby="knowledge-status-heading">
        <div className="section-heading">
          <div><p className="eyebrow">Index</p><h2 id="knowledge-status-heading">Document status</h2></div>
          {canReindex ? (
            <button className="secondary-button" disabled={statusQuery.isPending || isReindexing} onClick={() => void triggerReindex()} type="button">
              {isReindexing ? 'Queueing…' : 'Rebuild index'}
            </button>
          ) : null}
        </div>
        {statusQuery.isPending ? <p role="status">Loading index status…</p> : null}
        {statusQuery.isError ? (
          <div className="inline-error" role="alert">
            <p>{getApiErrorMessage(statusQuery.error, 'Unable to load index status.')}</p>
            <button className="secondary-button" onClick={() => void statusQuery.refetch()} type="button">Retry</button>
          </div>
        ) : null}
        {statusQuery.data ? (
          <p className="knowledge-status-counts" aria-live="polite">
            {statusQuery.data.ready} ready · {statusQuery.data.pending} queued · {statusQuery.data.indexing} processing · {statusQuery.data.failed} failed
          </p>
        ) : null}
        {reindexMessage ? <p role="status" className="knowledge-notice">{reindexMessage}</p> : null}
      </section>

      <section className="resource-card knowledge-ask" aria-labelledby="knowledge-ask-heading">
        <h2 id="knowledge-ask-heading">Ask your workspace</h2>
        <form className="resource-form" onSubmit={submitQuestion}>
          <label className="field" htmlFor="knowledge-question">
            <span>Question</span>
            <textarea
              id="knowledge-question"
              maxLength={1500}
              onChange={(event) => setQuestion(event.target.value)}
              placeholder="Ask about a runbook, process, or decision…"
              rows={3}
              value={question}
            />
          </label>
          <button className="primary-button" disabled={!question.trim()} type="submit">
            Ask
          </button>
        </form>

        {answerQuery.isFetching ? <p role="status">Checking workspace documents…</p> : null}
        {answerQuery.isError ? (
          <div className="inline-error" role="alert">
            <p>{getApiErrorMessage(answerQuery.error, 'Unable to get an answer.')}</p>
            <button className="secondary-button" onClick={retryAnswer} type="button">Retry</button>
          </div>
        ) : null}
        {answerQuery.data ? (
          <article className="knowledge-answer" aria-live="polite">
            <p className="eyebrow">{answerQuery.data.status === 'ANSWERED' ? 'Answer' : 'Not enough evidence'}</p>
            <p className="knowledge-answer-text">{answerQuery.data.answer}</p>
            {answerQuery.data.sources.length ? <SourceList sources={answerQuery.data.sources} /> : null}
          </article>
        ) : null}
      </section>

      <section className="resource-card knowledge-search" aria-labelledby="knowledge-search-heading">
        <h2 id="knowledge-search-heading">Search passages</h2>
        <form className="resource-form knowledge-search-form" onSubmit={submitSearch}>
          <label className="field" htmlFor="knowledge-search-query">
            <span>Search query</span>
            <input
              id="knowledge-search-query"
              maxLength={2000}
              onChange={(event) => setSearchText(event.target.value)}
              value={searchText}
            />
          </label>
          <button className="secondary-button" disabled={!searchText.trim()} type="submit">Search</button>
        </form>
        {searchQuery.isFetching ? <p role="status">Searching…</p> : null}
        {searchQuery.isError ? (
          <div className="inline-error" role="alert">
            <p>{getApiErrorMessage(searchQuery.error, 'Unable to search workspace documents.')}</p>
            <button className="secondary-button" onClick={retrySearch} type="button">Retry</button>
          </div>
        ) : null}
        {searchQuery.data?.results.length === 0 ? <p className="empty-state">No matching active document passages.</p> : null}
        {searchQuery.data?.results.length ? <SourceList sources={searchQuery.data.results} showScore /> : null}
      </section>
    </div>
  );
}

function SourceList({ sources, showScore = false }: { sources: KnowledgeReference[]; showScore?: boolean }): ReactElement {
  return (
    <ol className="knowledge-source-list">
      {sources.map((source) => (
        <li className="knowledge-source" key={source.chunkId}>
          <div className="knowledge-source-heading">
            <strong>{source.title}</strong>
            {showScore ? <span className="activity-limit">Score {source.score.toFixed(3)}</span> : null}
          </div>
          <p>{source.excerpt}</p>
          <Link className="back-link" to={`/app/${source.workspaceId}/documents/${source.documentId}`}>Open document</Link>
        </li>
      ))}
    </ol>
  );
}
