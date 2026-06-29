package com.zora.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

/**
 * 多模型注册中心（Phase 5.3）
 * <p>
 * 从 application.yml 的 {@code ai.providers} 配置读取所有模型提供商和模型列表，
 * 为每个模型创建独立的 ChatModel 和 StreamingChatModel 实例。
 * </p>
 *
 * <p><b>路由键:</b> {@code "provider:modelId"}（如 {@code "deepseek:deepseek-chat"}）</p>
 * <p><b>降级:</b> 找不到指定模型时返回默认模型（{@code ai.default-provider:ai.default-model}）</p>
 * <p>ponytail: 所有模型实例启动时创建，不动态增删。</p>
 */
@Component
public class ModelRegistry {

    private static final Logger log = LoggerFactory.getLogger(ModelRegistry.class);

    private final Map<String, ChatModel> chatModels = new LinkedHashMap<>();
    private final Map<String, StreamingChatModel> streamingModels = new LinkedHashMap<>();
    private final List<ModelInfo> modelInfos = new ArrayList<>();

    private final String defaultProvider;
    private final String defaultModel;

    public ModelRegistry(AiProperties properties) {
        this.defaultProvider = properties.getDefaultProvider();
        this.defaultModel = properties.getDefaultModel();

        Double temperature = properties.getTemperature() != null ? properties.getTemperature() : 0.7;
        Integer maxTokens = properties.getMaxTokens() != null ? properties.getMaxTokens() : 4096;
        Integer timeout = properties.getTimeoutSeconds() != null ? properties.getTimeoutSeconds() : 120;

        Map<String, AiProperties.ProviderConfig> providers = properties.getProviders();
        if (providers == null || providers.isEmpty()) {
            log.warn("未配置任何 AI 模型提供商");
            return;
        }

        for (Map.Entry<String, AiProperties.ProviderConfig> entry : providers.entrySet()) {
            String provider = entry.getKey();
            AiProperties.ProviderConfig config = entry.getValue();
            if (config.getModels() == null) continue;

            for (AiProperties.ModelConfig mc : config.getModels()) {
                String modelId = mc.getId();
                String key = provider + ":" + modelId;

                ChatModel chatModel = OpenAiChatModel.builder()
                        .apiKey(config.getApiKey()).baseUrl(config.getBaseUrl())
                        .modelName(modelId).temperature(temperature)
                        .maxTokens(maxTokens).timeout(Duration.ofSeconds(timeout))
                        .logRequests(true).logResponses(true).build();
                chatModels.put(key, chatModel);

                StreamingChatModel streamingModel = OpenAiStreamingChatModel.builder()
                        .apiKey(config.getApiKey()).baseUrl(config.getBaseUrl())
                        .modelName(modelId).temperature(temperature)
                        .maxTokens(maxTokens).timeout(Duration.ofSeconds(timeout))
                        .logRequests(true).logResponses(true).build();
                streamingModels.put(key, streamingModel);

                modelInfos.add(new ModelInfo(provider, modelId, mc.getName()));
                log.info("已注册模型: {}:{} ({})", provider, modelId, mc.getName());
            }
        }
    }

    public ChatModel getChatModel(String provider, String modelId) {
        String key = resolveKey(provider, modelId);
        ChatModel m = chatModels.get(key);
        if (m == null) m = chatModels.get(defaultProvider + ":" + defaultModel);
        if (m == null) throw new IllegalStateException("模型不可用: " + key);
        return m;
    }

    public StreamingChatModel getStreamingModel(String provider, String modelId) {
        String key = resolveKey(provider, modelId);
        StreamingChatModel m = streamingModels.get(key);
        if (m == null) m = streamingModels.get(defaultProvider + ":" + defaultModel);
        if (m == null) throw new IllegalStateException("流式模型不可用: " + key);
        return m;
    }

    public List<ModelInfo> listModels() { return Collections.unmodifiableList(modelInfos); }
    public String getDefaultProvider() { return defaultProvider; }
    public String getDefaultModel() { return defaultModel; }

    private String resolveKey(String provider, String modelId) {
        return (provider != null ? provider : defaultProvider) + ":" + (modelId != null ? modelId : defaultModel);
    }

    /** 模型元数据（用于前端展示） */
    public record ModelInfo(String provider, String modelId, String name) {}
}
