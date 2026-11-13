package com.opspilot.knowledge;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KnowledgeAiClient {
    private static final String TOKEN_HEADER = "X-Opspilot-Service-Token";
    private final RestClient client;
    private final String internalToken;

    public KnowledgeAiClient(RestClient.Builder builder,
                             @Value("${app.knowledge.ai-service-url:http://localhost:8001}") String baseUrl,
                             @Value("${app.knowledge.internal-token:}") String internalToken) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(300));
        this.client = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.internalToken = internalToken;
    }

    public IndexResponse index(Long workspaceId, Long documentId, long revision, String title, String content) {
        return post("/internal/v1/documents/index", new IndexRequest(
                workspaceId, documentId, revision, title, content), IndexResponse.class);
    }

    public void archive(Long workspaceId, Long documentId, long revision) {
        post("/internal/v1/documents/" + workspaceId + "/" + documentId + "/" + revision + "/archive",
                null, ArchiveResponse.class);
    }

    public SearchResponse search(Long workspaceId, String query, int limit) {
        return post("/internal/v1/search", new AiSearchRequest(workspaceId, query, limit), SearchResponse.class);
    }

    public AnswerResponse answer(String question, List<AiEvidence> evidence) {
        return post("/internal/v1/answer", new AiAnswerRequest(question, evidence), AnswerResponse.class);
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        if (internalToken == null || internalToken.length() < 32) {
            throw new KnowledgeAiUnavailableException();
        }
        try {
            RestClient.RequestBodySpec request = client.post().uri(path)
                    .header(TOKEN_HEADER, internalToken)
                    .header("X-Request-Id", safeRequestId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON);
            RestClient.ResponseSpec response = body == null
                    ? request.retrieve()
                    : request.body(body).retrieve();
            return response.body(responseType);
        } catch (RestClientException exception) {
            throw new KnowledgeAiUnavailableException();
        }
    }

    private static String safeRequestId() {
        String requestId = MDC.get("requestId");
        return requestId != null && requestId.matches("[A-Za-z0-9._-]{1,64}")
                ? requestId : "knowledge-internal";
    }

    public record IndexRequest(
            @JsonProperty("workspace_id") Long workspaceId,
            @JsonProperty("document_id") Long documentId,
            long revision,
            String title,
            String content) { }

    public record IndexResponse(long revision, @JsonProperty("chunks_indexed") int chunksIndexed) { }
    public record ArchiveResponse(long revision) { }

    public record AiSearchRequest(
            @JsonProperty("workspace_id") Long workspaceId,
            String query,
            int limit) { }

    public record SearchResponse(List<AiSearchHit> results) { }

    public record AiSearchHit(
            @JsonProperty("workspace_id") Long workspaceId,
            @JsonProperty("document_id") Long documentId,
            long revision,
            String title,
            @JsonProperty("chunk_id") String chunkId,
            @JsonProperty("chunk_index") int chunkIndex,
            String text,
            double score,
            @JsonProperty("source_start") int sourceStart,
            @JsonProperty("source_end") int sourceEnd) { }

    public record AiEvidence(
            @JsonProperty("workspace_id") Long workspaceId,
            @JsonProperty("document_id") Long documentId,
            long revision,
            String title,
            @JsonProperty("chunk_id") String chunkId,
            String text) { }

    public record AiAnswerRequest(String question, List<AiEvidence> evidence) { }

    public record AnswerResponse(
            String status,
            String answer,
            @JsonProperty("cited_chunk_ids") List<String> citedChunkIds) { }
}
