export type DocumentStatus = 'ACTIVE' | 'ARCHIVED';

export type DocumentSummary = {
  id: number;
  title: string;
  status: DocumentStatus;
  creator: { id: number; name: string };
  createdAt: string;
  updatedAt: string;
};

export type Document = DocumentSummary & {
  workspaceId: number;
  content: string;
};

export type CreateDocumentRequest = { title: string; content: string };
export type UpdateDocumentRequest = { title?: string; content?: string };
