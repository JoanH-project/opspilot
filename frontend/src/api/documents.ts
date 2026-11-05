import { apiRequest } from './client';
import type { CreateDocumentRequest, Document, DocumentStatus, DocumentSummary, UpdateDocumentRequest } from '../types/document';

export const documentsApi = {
  list: (workspaceId: number, status: DocumentStatus, token: string) =>
    apiRequest<DocumentSummary[]>(`/api/workspaces/${workspaceId}/documents?status=${status}`, {}, token),

  create: (workspaceId: number, request: CreateDocumentRequest, token: string) =>
    apiRequest<Document>(`/api/workspaces/${workspaceId}/documents`, {
      method: 'POST',
      body: JSON.stringify(request),
    }, token),

  getById: (documentId: number, token: string) =>
    apiRequest<Document>(`/api/documents/${documentId}`, {}, token),

  update: (documentId: number, request: UpdateDocumentRequest, token: string) =>
    apiRequest<Document>(`/api/documents/${documentId}`, {
      method: 'PATCH',
      body: JSON.stringify(request),
    }, token),

  archive: (documentId: number, token: string) =>
    apiRequest<Document>(`/api/documents/${documentId}/archive`, { method: 'POST' }, token),
};
