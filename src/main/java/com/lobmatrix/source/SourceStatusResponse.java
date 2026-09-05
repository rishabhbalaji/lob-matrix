package com.lobmatrix.source;

import java.util.List;

public record SourceStatusResponse(
        String source,
        String displayName,
        boolean enabled,
        boolean selected,
        boolean implemented,
        boolean credentialsConfigured,
        String status,
        List<Long> subscriptionTokens,
        List<String> requiredEnvironmentVariables,
        String setupInstructions
) {
}
