package com.zora.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import com.zora.config.ModelRegistry;
import com.zora.service.AiChatService;
import com.zora.exception.BadRequestException;
import com.zora.utils.ResponseUtil;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * AI 对话控制器
 * 提供 SSE 流式对话和对话管理 API
 */
@RestController
@RequestMapping("/ai")
@Tag(name = "AI 对话", description = "基于 DeepSeek 的 AI 对话系统：SSE 流式对话、会话管理、回收站")
public class AiChatController {

    /** P1-2: 用户消息最大长度（约 2000 中文字） */
    private static final int MAX_MESSAGE_LENGTH = 4000;

    @Resource
    private AiChatService chatService;

    @Resource
    private ModelRegistry modelRegistry;

    @Operation(summary = "SSE 流式对话", description = "发送消息并以 SSE (text/event-stream) 流式返回 AI 回复。" +
            "每个 token 通过 SSE data 事件逐字推送，支持传入 conversationId 继续已有对话，不传则自动创建新对话。" +
            "消息长度限制 4000 字符（约 2000 中文字）")
    @com.zora.config.tracking.TrackAction("message_send")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            @RequestBody Map<String, Object> body,
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true) HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        String message = (String) body.get("message");
        Long conversationId = body.get("conversationId") != null
                ? Long.valueOf(body.get("conversationId").toString())
                : null;

        // P1-2: 消息非空 + 长度校验
        if (message == null || message.isBlank()) {
            return Flux.error(new IllegalArgumentException("消息不能为空"));
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            return Flux.error(new IllegalArgumentException(
                    "消息长度不能超过 " + MAX_MESSAGE_LENGTH + " 个字符"));
        }

        return chatService.streamChat(email, message, conversationId);
    }

    @Operation(summary = "获取对话列表", description = "获取当前用户所有未删除的对话，按最近更新时间倒序排列")
    @GetMapping("/conversations")
    public ResponseUtil listConversations(
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true) HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        List<Map<String, Object>> conversations = chatService.listConversations(email);
        return ResponseUtil.success(conversations);
    }

    @Operation(summary = "新建对话", description = "创建一个新的 AI 对话会话，可选传入自定义标题，不传则使用默认标题「新对话」")
    @com.zora.config.tracking.TrackAction("conv_create")
    @PostMapping("/conversations")
    public ResponseUtil createConversation(
            @RequestBody Map<String, String> body,
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true) HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        String title = body.get("title");
        Map<String, Object> result = chatService.createNewConversation(email, title);
        return ResponseUtil.success(result);
    }

    @Operation(summary = "获取对话消息历史", description = "获取指定对话的所有消息列表，包含用户消息和 AI 回复，按时间正序排列")
    @GetMapping("/conversations/{id}")
    public ResponseUtil getMessages(
            @Parameter(description = "对话 ID", required = true, example = "1") @PathVariable Long id,
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true) HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        List<Map<String, Object>> messages = chatService.getMessages(email, id);
        return ResponseUtil.success(messages);
    }

    @Operation(summary = "删除对话（移至回收站）", description = "软删除指定对话，对话及其消息移至回收站，30 天后可永久删除")
    @DeleteMapping("/conversations/{id}")
    public ResponseUtil deleteConversation(
            @Parameter(description = "对话 ID", required = true, example = "1") @PathVariable Long id,
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true) HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        chatService.deleteConversation(email, id);
        return ResponseUtil.success(null, "已移至回收站");
    }

    @Operation(summary = "获取回收站列表", description = "获取当前用户已删除（30 天内）的对话列表，可恢复或永久删除")
    @GetMapping("/conversations/trash")
    public ResponseUtil listDeletedConversations(
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true) HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        List<Map<String, Object>> conversations = chatService.listDeletedConversations(email);
        return ResponseUtil.success(conversations);
    }

    @Operation(summary = "恢复已删除对话", description = "将回收站中的对话恢复到正常状态，可继续对话")
    @PostMapping("/conversations/{id}/restore")
    public ResponseUtil restoreConversation(
            @Parameter(description = "对话 ID", required = true, example = "1") @PathVariable Long id,
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true) HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        chatService.restoreConversation(email, id);
        return ResponseUtil.success(null, "恢复成功");
    }

    @Operation(summary = "永久删除对话", description = "从回收站永久删除对话及其所有消息，此操作不可撤销")
    @DeleteMapping("/conversations/{id}/permanent")
    public ResponseUtil permanentDeleteConversation(
            @Parameter(description = "对话 ID", required = true, example = "1") @PathVariable Long id,
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true) HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        chatService.permanentDeleteConversation(email, id);
        return ResponseUtil.success(null, "已永久删除");
    }

    // ==================== 批量操作 ====================

    /** 批量操作最大 ID 数量（防止一次性操作过多） */
    private static final int MAX_BATCH_SIZE = 50;

    @Operation(summary = "批量删除对话（移至回收站）", description = "将多个对话及其消息一次性移至回收站。请求体传入 IDs 数组，单个失败不影响其他。最多 50 个。")
    @PostMapping("/conversations/batch-delete")
    public ResponseUtil batchDeleteConversations(
            @RequestBody Map<String, Object> body,
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true) HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        List<Long> ids = parseIdList(body);
        int count = chatService.batchDeleteConversations(email, ids);
        return ResponseUtil.success("成功删除 " + count + " 个对话", count);
    }

    @Operation(summary = "批量恢复已删除对话", description = "将回收站中的多个对话一次性恢复到正常状态。请求体传入 IDs 数组。最多 50 个。")
    @PostMapping("/conversations/batch-restore")
    public ResponseUtil batchRestoreConversations(
            @RequestBody Map<String, Object> body,
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true) HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        List<Long> ids = parseIdList(body);
        int count = chatService.batchRestoreConversations(email, ids);
        return ResponseUtil.success("成功恢复 " + count + " 个对话", count);
    }

    @Operation(summary = "批量永久删除对话", description = "从回收站中批量永久删除多个对话及其所有消息，此操作不可撤销。请求体传入 IDs 数组。最多 50 个。")
    @PostMapping("/conversations/batch-permanent-delete")
    public ResponseUtil batchPermanentDeleteConversations(
            @RequestBody Map<String, Object> body,
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true) HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        List<Long> ids = parseIdList(body);
        int count = chatService.batchPermanentDeleteConversations(email, ids);
        return ResponseUtil.success("成功永久删除 " + count + " 个对话", count);
    }

    /**
     * 从请求体中解析 ID 列表
     * <p>
     * 校验 ids 字段非空且不超过 {@link #MAX_BATCH_SIZE} 个。
     * 支持 JSON 中传入整数或字符串形式的 ID（统一转为 Long）。
     * </p>
     *
     * @param body 请求体 Map
     * @return 解析后的 Long ID 列表
     * @throws BadRequestException ids 为空或超过上限
     */
    @SuppressWarnings("unchecked")
    private List<Long> parseIdList(Map<String, Object> body) {
        Object idsObj = body.get("ids");
        if (!(idsObj instanceof List)) {
            throw new BadRequestException("ids 不能为空");
        }
        List<?> rawList = (List<?>) idsObj;
        if (rawList.isEmpty()) {
            throw new BadRequestException("ids 不能为空");
        }
        if (rawList.size() > MAX_BATCH_SIZE) {
            throw new BadRequestException("单次批量操作最多 " + MAX_BATCH_SIZE + " 个");
        }
        return rawList.stream()
                .map(o -> Long.valueOf(o.toString()))
                .collect(java.util.stream.Collectors.toList());
    }

    // ==================== Phase 2: RAG 增强对话 ====================

    @Operation(summary = "SSE 流式对话（RAG 增强）", description = "与 /ai/chat/stream 类似，但可指定知识库 ID。" +
            "当 knowledgeBaseId 不为 null 时，先从知识库检索相关上下文注入 System Prompt，" +
            "使 AI 回答基于知识库内容生成。knowledgeBaseId 为 null 时退化为普通对话。")
    @PostMapping(value = "/chat/rag-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamRagChat(
            @RequestBody Map<String, Object> body,
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true) HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        String message = (String) body.get("message");
        Long conversationId = body.get("conversationId") != null
                ? Long.valueOf(body.get("conversationId").toString())
                : null;
        Long knowledgeBaseId = body.get("knowledgeBaseId") != null
                ? Long.valueOf(body.get("knowledgeBaseId").toString())
                : null;

        // 消息非空 + 长度校验
        if (message == null || message.isBlank()) {
            return Flux.error(new IllegalArgumentException("消息不能为空"));
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            return Flux.error(new IllegalArgumentException(
                    "消息长度不能超过 " + MAX_MESSAGE_LENGTH + " 个字符"));
        }

        return chatService.streamChatWithRag(email, message, conversationId, knowledgeBaseId);
    }

    // ==================== 模型管理（Phase 5.3） ====================

    @Operation(summary = "获取可用模型列表", description = "返回所有已配置的 AI 模型元数据（provider + modelId + name），用于前端模型选择器")
    @GetMapping("/models")
    public ResponseUtil listModels() {
        List<Map<String, String>> models = modelRegistry.listModels().stream()
                .map(m -> Map.of("provider", m.provider(), "modelId", m.modelId(), "name", m.name()))
                .toList();
        return ResponseUtil.success(models);
    }
}
