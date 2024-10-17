package io.dataguardians.config.audting;

import io.dataguardians.config.security.zt.DefaultJITConfigProvider;

public interface AuditingConfigProvider {

    String getAuditorClass();

    String getRuleConfig(String property);

    public class AuditingConfigProviderFactory {

        private static final AuditingConfigProvider configProvider;

        static {
            // You can conditionally select the provider (e.g., based on environment, configuration, etc.)
            String providerType = System.getenv("AUDIT_CONFIG_CLASS");

            if (!providerType.isEmpty()) {
                try {
                    configProvider = (AuditingConfigProvider) Class.forName(providerType).newInstance();
                } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                    throw new RuntimeException("Failed to instantiate JITConfigProvider", e);
                }
            } else {
                // Default to in-memory configuration
                configProvider = new DefaultAuditConfigProvider();
            }
        }

        public static AuditingConfigProvider getConfigProvider() {
            return configProvider;
        }

    }
}
