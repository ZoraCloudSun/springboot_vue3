package com.zora.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * AI 模型配置属性
 * <p>绑定 application.yml 中 {@code ai.*} 配置段。</p>
 */
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private String defaultProvider = "deepseek";
    private String defaultModel = "deepseek-chat";
    private Double temperature = 0.7;
    private Integer maxTokens = 4096;
    private Integer timeoutSeconds = 120;
    private Map<String, ProviderConfig> providers;

    // getters/setters
    public String getDefaultProvider() { return defaultProvider; }
    public void setDefaultProvider(String v) { this.defaultProvider = v; }

    public String getDefaultModel() { return defaultModel; }
    public void setDefaultModel(String v) { this.defaultModel = v; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double v) { this.temperature = v; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer v) { this.maxTokens = v; }

    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer v) { this.timeoutSeconds = v; }

    public Map<String, ProviderConfig> getProviders() { return providers; }
    public void setProviders(Map<String, ProviderConfig> v) { this.providers = v; }

    /** 单个模型提供商配置 */
    public static class ProviderConfig {
        private String type;
        private String baseUrl;
        private String apiKey;
        private List<ModelConfig> models;

        public String getType() { return type; }
        public void setType(String v) { this.type = v; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { this.baseUrl = v; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String v) { this.apiKey = v; }
        public List<ModelConfig> getModels() { return models; }
        public void setModels(List<ModelConfig> v) { this.models = v; }
    }

    /** 单个模型配置 */
    public static class ModelConfig {
        private String id;
        private String name;
        private Boolean streaming = true;

        public String getId() { return id; }
        public void setId(String v) { this.id = v; }
        public String getName() { return name; }
        public void setName(String v) { this.name = v; }
        public Boolean getStreaming() { return streaming; }
        public void setStreaming(Boolean v) { this.streaming = v; }
    }
}
