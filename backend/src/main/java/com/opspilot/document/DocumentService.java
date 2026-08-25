package com.opspilot.document;

import java.util.List;
import java.util.Objects;

import com.opspilot.activity.ActivityLogService;
import com.opspilot.activity.ActivityType;
import com.opspilot.document.dto.*;
import com.opspilot.user.*;
import com.opspilot.workspace.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {
    private final DocumentRepository documents;
    private final UserRepository users;
    private final WorkspaceAccessService access;
    private final ActivityLogService activityLogService;

    public DocumentService(DocumentRepository documents, UserRepository users,
                           WorkspaceAccessService access, ActivityLogService activityLogService) {
        this.documents = documents;
        this.users = users;
        this.access = access;
        this.activityLogService = activityLogService;
    }

    @Transactional
    public DocumentResponse create(Long workspaceId, Long userId, CreateDocumentRequest request) {
        WorkspaceMember membership = access.requireMembership(workspaceId, userId);
        User creator = users.findById(userId).orElseThrow(DocumentNotFoundException::new);
        Document saved = documents.saveAndFlush(
                new Document(membership.getWorkspace(), request.title().trim(), request.content(), creator));
        activityLogService.record(saved.getWorkspace(), creator, ActivityType.DOCUMENT_CREATED, "DOCUMENT",
                saved.getId(), creator.getName() + " created document " + saved.getTitle());
        return DocumentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<DocumentSummaryResponse> list(Long workspaceId, Long userId, DocumentStatus status) {
        access.requireMembership(workspaceId, userId);
        return documents.findByWorkspace_IdAndStatusOrderByCreatedAtDesc(workspaceId, status).stream()
                .map(DocumentSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(Long id, Long userId) {
        return DocumentResponse.from(document(id, userId));
    }

    @Transactional
    public DocumentResponse update(Long id, Long userId, UpdateDocumentRequest request) {
        Document document = findDocument(id);
        WorkspaceMember actorMembership = canModify(document, userId);
        if (document.getStatus() == DocumentStatus.ARCHIVED) {
            throw new ArchivedDocumentException();
        }
        if (request.title() != null && request.title().isBlank()) {
            throw new InvalidDocumentUpdateException("Document title must not be blank");
        }

        String title = request.title() == null ? null : request.title().trim();
        boolean changed = (title != null && !Objects.equals(title, document.getTitle()))
                || (request.content() != null && !Objects.equals(request.content(), document.getContent()));
        if (!changed) {
            return DocumentResponse.from(document);
        }

        document.update(title, request.content());
        Document saved = documents.saveAndFlush(document);
        User actor = actorMembership.getUser();
        activityLogService.record(saved.getWorkspace(), actor, ActivityType.DOCUMENT_UPDATED, "DOCUMENT",
                saved.getId(), actor.getName() + " updated document " + saved.getTitle());
        return DocumentResponse.from(saved);
    }

    @Transactional
    public DocumentResponse archive(Long id, Long userId) {
        Document document = findDocument(id);
        WorkspaceMember actorMembership = canModify(document, userId);
        if (document.getStatus() == DocumentStatus.ARCHIVED) {
            return DocumentResponse.from(document);
        }

        document.archive();
        Document saved = documents.saveAndFlush(document);
        User actor = actorMembership.getUser();
        activityLogService.record(saved.getWorkspace(), actor, ActivityType.DOCUMENT_ARCHIVED, "DOCUMENT",
                saved.getId(), actor.getName() + " archived document " + saved.getTitle());
        return DocumentResponse.from(saved);
    }

    private Document document(Long id, Long userId) {
        Document document = findDocument(id);
        access.requireMembership(document.getWorkspace().getId(), userId);
        return document;
    }

    private Document findDocument(Long id) {
        return documents.findById(id).orElseThrow(DocumentNotFoundException::new);
    }

    private WorkspaceMember canModify(Document document, Long userId) {
        WorkspaceMember member = access.requireMembership(document.getWorkspace().getId(), userId);
        if (member.getRole() == WorkspaceRole.OWNER || member.getRole() == WorkspaceRole.ADMIN
                || document.getCreatedBy().getId().equals(userId)) {
            return member;
        }
        throw new DocumentAccessDeniedException();
    }
}
