package dev.recipeeditor.integrations.api;
public interface ModIntegration {
    String modId();
    IntegrationCapabilities capabilities(IntegrationContext context);
}
