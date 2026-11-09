package com.opspilot.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeAskRequest(@NotBlank @Size(max = 1500) String question) { }
