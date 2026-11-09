export type KnowledgeReference = {
  documentId: number;
  workspaceId: number;
  revision: number;
  title: string;
  chunkId: string;
  chunkIndex: number;
  excerpt: string;
  score: number;
  sourceStart: number;
  sourceEnd: number;
  href: string;
};

export type KnowledgeSearchResponse = { results: KnowledgeReference[] };

export type KnowledgeAnswerResponse = {
  status: 'ANSWERED' | 'INSUFFICIENT_EVIDENCE';
  answer: string;
  sources: KnowledgeReference[];
};

export type KnowledgeIndexStatus = {
  total: number;
  pending: number;
  indexing: number;
  ready: number;
  failed: number;
};

export type KnowledgeReindexResponse = { queuedDocuments: number; status: 'QUEUED' };
