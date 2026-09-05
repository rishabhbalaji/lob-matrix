package com.lobmatrix.source;

import com.lobmatrix.persistence.entity.MarketDataSourceEntity;
import com.lobmatrix.persistence.repository.MarketDataSourceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MarketDataSourceService {

    private static final long DEFAULT_MOCK_TOKEN = 1001L;

    private final MarketDataSourceRepository repository;
    private final Map<String, String> environment;

    @Autowired
    public MarketDataSourceService(MarketDataSourceRepository repository) {
        this(repository, System.getenv());
    }

    MarketDataSourceService(MarketDataSourceRepository repository, Map<String, String> environment) {
        this.repository = repository;
        this.environment = environment;
    }

    @Transactional
    public List<SourceStatusResponse> listSources() {
        ensureDefaults();
        return Arrays.stream(MarketDataSource.values())
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SourceStatusResponse selectAndUpdate(SourceSelectionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Source configuration must be provided.");
        }

        MarketDataSource source = MarketDataSource.from(request.source());
        ensureDefaults();

        MarketDataSourceEntity entity = repository.findBySourceCode(source.name())
                .orElseThrow(() -> new IllegalStateException("Source record was not initialized: " + source));

        if (request.displayName() != null && !request.displayName().isBlank()) {
            entity.setDisplayName(request.displayName().trim());
        }

        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }

        if (request.subscriptionTokens() != null) {
            entity.setSubscriptionTokens(serializeTokens(request.subscriptionTokens()));
        }

        if (source.isImplemented() && entity.isEnabled()) {
            repository.findAll().forEach(other -> other.setSelected(other.getSourceCode().equals(source.name())));
        } else {
            entity.setSelected(false);
        }

        repository.save(entity);
        return toResponse(source);
    }

    private void ensureDefaults() {
        for (MarketDataSource source : MarketDataSource.values()) {
            repository.findBySourceCode(source.name()).orElseGet(() -> repository.save(
                    new MarketDataSourceEntity(
                            source.name(),
                            displayName(source),
                            source == MarketDataSource.MOCK,
                            source == MarketDataSource.MOCK,
                            source == MarketDataSource.MOCK ? String.valueOf(DEFAULT_MOCK_TOKEN) : ""
                    )
            ));
        }
    }

    private SourceStatusResponse toResponse(MarketDataSource source) {
        MarketDataSourceEntity entity = repository.findBySourceCode(source.name())
                .orElseThrow(() -> new IllegalStateException("Source record was not initialized: " + source));

        List<String> requiredVariables = requiredEnvironmentVariables(source);
        boolean credentialsConfigured = source == MarketDataSource.MOCK
                || requiredVariables.stream().allMatch(this::hasEnvironmentValue);

        String status = statusFor(source, entity, credentialsConfigured);
        return new SourceStatusResponse(
                source.name(),
                entity.getDisplayName(),
                entity.isEnabled(),
                entity.isSelected(),
                source.isImplemented(),
                credentialsConfigured,
                status,
                parseTokens(entity.getSubscriptionTokens()),
                requiredVariables,
                setupInstructions(source, requiredVariables)
        );
    }

    private String statusFor(
            MarketDataSource source,
            MarketDataSourceEntity entity,
            boolean credentialsConfigured
    ) {
        if (source == MarketDataSource.MOCK) {
            return entity.isSelected() ? "ACTIVE_MOCK" : "AVAILABLE_MOCK";
        }
        if (!credentialsConfigured) {
            return "CREDENTIALS_PENDING";
        }
        return "ADAPTER_PENDING";
    }

    private boolean hasEnvironmentValue(String name) {
        String value = environment.get(name);
        return value != null && !value.isBlank();
    }

    private static List<String> requiredEnvironmentVariables(MarketDataSource source) {
        return switch (source) {
            case MOCK -> List.of();
            case ZERODHA -> List.of(
                    "LOBMATRIX_ZERODHA_API_KEY",
                    "LOBMATRIX_ZERODHA_ACCESS_TOKEN"
            );
            case DHAN -> List.of(
                    "LOBMATRIX_DHAN_CLIENT_ID",
                    "LOBMATRIX_DHAN_ACCESS_TOKEN"
            );
            case UPSTOX -> List.of("LOBMATRIX_UPSTOX_ACCESS_TOKEN");
        };
    }

    private static String setupInstructions(MarketDataSource source, List<String> variables) {
        if (source == MarketDataSource.MOCK) {
            return "Mock data is available immediately and requires no credentials.";
        }

        String assignments = variables.stream()
                .map(variable -> variable + "=")
                .collect(Collectors.joining("\\n"));

        return "On the server only, add the following names to the ignored .env file, "
                + "then restart Lob Matrix. Never paste credentials into this dashboard, Git, chat, or messaging apps.\\n\\n"
                + assignments;
    }

    private static String serializeTokens(List<Long> tokens) {
        if (tokens.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Subscription tokens must not contain null.");
        }

        return tokens.stream()
                .distinct()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private static List<Long> parseTokens(String serialized) {
        if (serialized == null || serialized.isBlank()) {
            return List.of();
        }

        return Arrays.stream(serialized.split(","))
                .filter(value -> !value.isBlank())
                .map(Long::parseLong)
                .toList();
    }

    private static String displayName(MarketDataSource source) {
        return switch (source) {
            case MOCK -> "Mock feed";
            case ZERODHA -> "Zerodha Kite Connect";
            case DHAN -> "DhanHQ";
            case UPSTOX -> "Upstox";
        };
    }
}
