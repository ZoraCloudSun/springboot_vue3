package com.zora.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * AI 配置类（Phase 5.3 重构 — 向后兼容版本）
 * <p>
 * {@link ModelRegistry} 管理所有模型实例（多 Provider + 多 Model）。
 * 此处保留旧 Bean 名称并委托到默认模型，已有 Service 无需改动。
 * </p>
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    @Bean
    public StreamingChatModel streamingChatLanguageModel(ModelRegistry registry) {
        return registry.getStreamingModel(registry.getDefaultProvider(), registry.getDefaultModel());
    }

    @Bean
    @Primary
    public ChatModel chatLanguageModel(ModelRegistry registry) {
        return registry.getChatModel(registry.getDefaultProvider(), registry.getDefaultModel());
    }
}
