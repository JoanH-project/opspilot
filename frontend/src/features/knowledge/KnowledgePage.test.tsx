import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { knowledgeApi } from '../../api/knowledge';
import { KnowledgePage } from './KnowledgeRoute';
import type { KnowledgeAnswerResponse, KnowledgeReference } from '../../types/knowledge';
import type { WorkspaceSummary } from '../../types/workspace';

const authState = vi.hoisted(() => ({
  token: 'test-token',
  currentUser: { id: 10, email: 'member@example.com', name: 'Member', createdAt: '2026-01-01T00:00:00Z' },
  logout: vi.fn(),
}));

vi.mock('../auth/useAuth', () => ({ useAuth: () => authState }));
vi.mock('../../api/knowledge', () => ({
  knowledgeApi: {
    ask: vi.fn(),
    search: vi.fn(),
    indexStatus: vi.fn(),
    reindex: vi.fn(),
  },
}));

const api = vi.mocked(knowledgeApi);
const memberWorkspace: WorkspaceSummary = {
  id: 1,
  name: 'Operations',
  role: 'MEMBER',
  createdAt: '2026-01-01T00:00:00Z',
};
const ownerWorkspace: WorkspaceSummary = { ...memberWorkspace, role: 'OWNER' };

const source: KnowledgeReference = {
  documentId: 9,
  workspaceId: 1,
  revision: 2,
  title: '<img src=x onerror=alert(1)> Runbook',
  chunkId: 'chunk-9-2-0',
  chunkIndex: 0,
  excerpt: '<script>alert("unsafe")</script> Restart the API process.',
  score: 0.91,
  sourceStart: 0,
  sourceEnd: 52,
  href: 'javascript:alert(1)',
};

function renderKnowledge(workspace = memberWorkspace) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  });
  const view = render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <KnowledgePage workspace={workspace} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { ...view, queryClient };
}

describe('Knowledge page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.indexStatus.mockResolvedValue({ total: 2, pending: 1, indexing: 0, ready: 1, failed: 0 });
    api.ask.mockResolvedValue({ status: 'ANSWERED', answer: 'Restart the API process.', sources: [source] });
    api.search.mockResolvedValue({ results: [source] });
    api.reindex.mockResolvedValue({ queuedDocuments: 2, status: 'QUEUED' });
  });

  afterEach(cleanup);

  it('shows answers, safe source text, and a same-app document link', async () => {
    const user = userEvent.setup();
    renderKnowledge();

    await user.type(screen.getByRole('textbox', { name: 'Question' }), 'How do I restart it?');
    await user.click(screen.getByRole('button', { name: 'Ask' }));

    expect(await screen.findByText('Restart the API process.')).toBeInTheDocument();
    expect(screen.getByText('<script>alert("unsafe")</script> Restart the API process.')).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Open document/ })).toHaveAttribute(
      'href',
      '/app/1/documents/9',
    );
    expect(api.ask).toHaveBeenCalledWith(1, 'How do I restart it?', 'test-token', expect.any(AbortSignal));
  });

  it('shows an explicit empty evidence response', async () => {
    const user = userEvent.setup();
    const empty: KnowledgeAnswerResponse = {
      status: 'INSUFFICIENT_EVIDENCE',
      answer: 'The workspace does not contain enough evidence.',
      sources: [],
    };
    api.ask.mockResolvedValue(empty);
    renderKnowledge();

    await user.type(screen.getByRole('textbox', { name: 'Question' }), 'What is the launch date?');
    await user.click(screen.getByRole('button', { name: 'Ask' }));

    expect(await screen.findByText('Not enough evidence')).toBeInTheDocument();
    expect(screen.getByText(empty.answer)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Open document' })).not.toBeInTheDocument();
  });

  it('shows a useful search error and retries the query', async () => {
    const user = userEvent.setup();
    api.search
      .mockRejectedValueOnce({ status: 503, message: 'The local knowledge service is unavailable.' })
      .mockResolvedValueOnce({ results: [source] });
    renderKnowledge();

    await user.type(screen.getByRole('textbox', { name: 'Search query' }), 'restart process');
    await user.click(screen.getByRole('button', { name: 'Search' }));
    expect(await screen.findByText('The local knowledge service is unavailable.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByRole('link', { name: 'Open document' })).toHaveAttribute(
      'href',
      '/app/1/documents/9',
    );
  });

  it('does not expose reindex to a member and lets an owner queue it', async () => {
    const user = userEvent.setup();
    const memberView = renderKnowledge();
    await screen.findByText(/1 ready · 1 queued/);
    expect(screen.queryByRole('button', { name: 'Rebuild index' })).not.toBeInTheDocument();
    memberView.unmount();

    renderKnowledge(ownerWorkspace);
    await user.click(await screen.findByRole('button', { name: 'Rebuild index' }));
    expect(await screen.findByText('Reindex queued for 2 active document(s).')).toBeInTheDocument();
    expect(api.reindex).toHaveBeenCalledWith(1, 'test-token');
  });

  it('cancels and removes an in-flight query when switching workspaces', async () => {
    const user = userEvent.setup();
    const requestSignals: AbortSignal[] = [];
    api.ask.mockImplementation((_workspaceId, _question, _token, signal) => {
      if (signal) requestSignals.push(signal);
      return new Promise(() => undefined);
    });
    const view = renderKnowledge();
    await user.type(screen.getByRole('textbox', { name: 'Question' }), 'Sensitive workspace question');
    await user.click(screen.getByRole('button', { name: 'Ask' }));
    await waitFor(() => expect(requestSignals).toHaveLength(1));

    view.rerender(
      <QueryClientProvider client={view.queryClient}>
        <MemoryRouter>
          <KnowledgePage workspace={{ ...memberWorkspace, id: 2, name: 'Product' }} />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await waitFor(() => expect(requestSignals[0]?.aborted).toBe(true));
    expect(requestSignals).toHaveLength(1);
    expect(api.ask).toHaveBeenCalledTimes(1);
    expect(view.queryClient.getQueryData(['knowledge', 10, 1, 'answer', 'Sensitive workspace question']))
      .toBeUndefined();
  });
});
