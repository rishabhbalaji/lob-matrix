package com.lobmatrix.source;

import com.lobmatrix.persistence.entity.MarketDataSourceEntity;
import com.lobmatrix.persistence.repository.MarketDataSourceRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketDataSourceServiceTest {

    @Test
    void initializesAllSourcesWithoutExposingCredentials() {
        InMemorySourceStore store = new InMemorySourceStore();
        MarketDataSourceService service = new MarketDataSourceService(store.repository(), Map.of());

        List<SourceStatusResponse> sources = service.listSources();

        assertThat(sources).hasSize(4);
        assertThat(sources).anySatisfy(source -> {
            assertThat(source.source()).isEqualTo("MOCK");
            assertThat(source.selected()).isTrue();
            assertThat(source.status()).isEqualTo("ACTIVE_MOCK");
            assertThat(source.subscriptionTokens()).containsExactly(1001L);
        });
        assertThat(sources).anySatisfy(source -> {
            assertThat(source.source()).isEqualTo("DHAN");
            assertThat(source.credentialsConfigured()).isFalse();
            assertThat(source.status()).isEqualTo("CREDENTIALS_PENDING");
            assertThat(source.setupInstructions()).contains("LOBMATRIX_DHAN_CLIENT_ID=");
            assertThat(source.setupInstructions()).doesNotContain("secret-value");
        });
    }

    @Test
    void reportsCredentialsAsConfiguredWithoutReturningTheirValues() {
        InMemorySourceStore store = new InMemorySourceStore();
        Map<String, String> environment = Map.of(
                "LOBMATRIX_DHAN_CLIENT_ID", "configured-client-marker",
                "LOBMATRIX_DHAN_ACCESS_TOKEN", "configured-token-marker"
        );
        MarketDataSourceService service = new MarketDataSourceService(store.repository(), environment);

        SourceStatusResponse dhan = service.listSources().stream()
                .filter(source -> source.source().equals("DHAN"))
                .findFirst()
                .orElseThrow();

        assertThat(dhan.credentialsConfigured()).isTrue();
        assertThat(dhan.status()).isEqualTo("ADAPTER_PENDING");
        assertThat(dhan.setupInstructions())
                .doesNotContain("configured-client-marker")
                .doesNotContain("configured-token-marker");
    }

    @Test
    void rejectsUnknownSource() {
        InMemorySourceStore store = new InMemorySourceStore();
        MarketDataSourceService service = new MarketDataSourceService(store.repository(), Map.of());

        assertThatThrownBy(() -> service.selectAndUpdate(
                new SourceSelectionRequest("UNKNOWN", null, null, null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported source");
    }

    private static final class InMemorySourceStore {
        private final Map<String, MarketDataSourceEntity> entities = new HashMap<>();
        private final MarketDataSourceRepository repository = mock(MarketDataSourceRepository.class);

        private InMemorySourceStore() {
            when(repository.findBySourceCode(any(String.class)))
                    .thenAnswer(invocation -> Optional.ofNullable(entities.get(invocation.getArgument(0))));

            when(repository.save(any(MarketDataSourceEntity.class)))
                    .thenAnswer(invocation -> {
                        MarketDataSourceEntity entity = invocation.getArgument(0);
                        entities.put(entity.getSourceCode(), entity);
                        return entity;
                    });

            when(repository.findAll())
                    .thenAnswer(invocation -> List.copyOf(entities.values()));
        }

        private MarketDataSourceRepository repository() {
            return repository;
        }
    }
}
