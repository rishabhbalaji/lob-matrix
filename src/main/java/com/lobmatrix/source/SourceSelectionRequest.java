package com.lobmatrix.source;

import java.util.List;

public record SourceSelectionRequest(
        String source,
        String displayName,
        Boolean enabled,
        List<Long> subscriptionTokens
) {
}
