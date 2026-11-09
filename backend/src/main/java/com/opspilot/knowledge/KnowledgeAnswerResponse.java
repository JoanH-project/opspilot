package com.opspilot.knowledge;

import java.util.List;

public record KnowledgeAnswerResponse(String status, String answer, List<KnowledgeReference> sources) { }
