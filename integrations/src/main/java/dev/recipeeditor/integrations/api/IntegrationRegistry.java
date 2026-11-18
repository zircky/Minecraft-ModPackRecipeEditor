package dev.recipeeditor.integrations.api;
import java.util.*;
/** Explicit registration: duplicate owners fail fast rather than depending on classpath order. */
public final class IntegrationRegistry {
    private final Map<String, ModIntegration> integrations = new LinkedHashMap<>();
    private final Set<String> disabled = new HashSet<>();
    public synchronized void register(final ModIntegration integration) {
        Objects.requireNonNull(integration);
        if (integrations.putIfAbsent(integration.modId(), integration) != null)
            throw new IllegalArgumentException("Integration already registered: " + integration.modId());
    }
    public synchronized void enabled(final String modId, final boolean enabled) {
        if (!integrations.containsKey(modId)) throw new IllegalArgumentException("Unknown integration: " + modId);
        if (enabled) disabled.remove(modId); else disabled.add(modId);
    }
    public synchronized List<String> modIds() { return List.copyOf(integrations.keySet()); }
    public synchronized IntegrationCapabilities capabilities(final String modId, final IntegrationContext context) {
        final var integration = Optional.ofNullable(integrations.get(modId)).orElseThrow();
        if (disabled.contains(modId)) return new IntegrationCapabilities(SupportLevel.UNSUPPORTED, "disabled", Map.of(), List.of("Disabled for this application session"));
        return integration.capabilities(context);
    }
}
