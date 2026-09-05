package com.lobmatrix.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "market_data_sources")
public class MarketDataSourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_code", nullable = false, unique = true, updatable = false)
    private String sourceCode;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "selected", nullable = false)
    private boolean selected;

    @Column(name = "subscription_tokens", nullable = false)
    private String subscriptionTokens;

    public MarketDataSourceEntity() {
    }

    public MarketDataSourceEntity(
            String sourceCode,
            String displayName,
            boolean enabled,
            boolean selected,
            String subscriptionTokens
    ) {
        this.sourceCode = sourceCode;
        this.displayName = displayName;
        this.enabled = enabled;
        this.selected = selected;
        this.subscriptionTokens = subscriptionTokens;
    }

    public Long getId() {
        return id;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public String getSubscriptionTokens() {
        return subscriptionTokens;
    }

    public void setSubscriptionTokens(String subscriptionTokens) {
        this.subscriptionTokens = subscriptionTokens;
    }
}
