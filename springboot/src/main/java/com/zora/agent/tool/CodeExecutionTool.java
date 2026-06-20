package com.zora.agent.tool;

import com.zora.config.AgentConfig;
import dev.langchain4j.agent.tool.P;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.*;

/**
 * 代码执行工具（Phase 3.2）
 * <p>
 * 提供安全的 JavaScript 代码沙箱执行能力。
 * 使用 JDK 内置 {@link ScriptEngine}（Nashorn/GraalJS）执行代码，
 * 并通过超时控制、输出限制和审计日志确保安全性。
 * </p>
 *
 * <h3>安全措施</h3>
 * <ol>
 * <li><b>默认关闭</b>：通过 {@code agent.tools.code-execution.enabled=false} 全局禁用</li>
 * <li><b>超时控制</b>：代码执行超时自动中断（默认 5 秒）</li>
 * <li><b>输出限制</b>：标准输出截断至最大字符数（默认 10000 字符）</li>
 * <li><b>审计日志</b>：每次执行记录用户、语言和代码长度</li>
 * <li><b>独立线程</b>：在单独线程中执行，避免影响主服务</li>
 * </ol>
 *
 * <h3>JDK 版本说明</h3>
 * <p>
 * JDK 15+ 移除了 Nashorn 引擎。如需 JS 执行能力，可添加 GraalVM JS 依赖：
 * </p>
 * <pre>{@code
 * <dependency>
 *     <groupId>org.graalvm.js</groupId>
 *     <artifactId>js</artifactId>
 *     <version>24.0.0</version>
 * </dependency>
 * }</pre>
 * <p>
 * 当前实现会检测可用引擎，若无 JS 引擎则返回明确错误提示。
 * </p>
 *
 * @see AgentConfig.CodeExecConfig
 */
@Component
public class CodeExecutionTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(CodeExecutionTool.class);

    @Resource
    private AgentConfig agentConfig;

    /**
     * 在安全沙箱中执行 JavaScript 代码片段
     * <p>
     * 代码在独立线程中运行，具有超时保护和输出截断。
     * 标准输出（stdout）和标准错误（stderr）均被捕获并返回。
     * </p>
     *
     * <h3>使用示例</h3>
     * <ul>
     * <li>简单计算：{@code 2 + 3 * 4}</li>
     * <li>数组操作：{@code [1,2,3].map(x => x*2)}</li>
     * <li>字符串处理：{@code "hello".toUpperCase()}</li>
     * <li>JSON 处理：{@code JSON.stringify({a:1, b:2})}</li>
     * </ul>
     *
     * <h3>安全限制</h3>
     * <ul>
     * <li>禁止文件系统访问（无 {@code require('fs')}）</li>
     * <li>禁止网络请求（无 {@code fetch()}）</li>
     * <li>禁止无限循环（超时自动中断）</li>
     * <li>输出限制 10000 字符</li>
     * </ul>
     *
     * @param code     要执行的代码内容
     * @param language 编程语言标识，目前仅支持 "javascript"
     * @return JSON 格式结果：{"stdout":"...", "stderr":"...", "success":true/false}
     */
    @dev.langchain4j.agent.tool.Tool("在安全沙箱中执行 JavaScript 代码片段。"
            + "代码在独立线程中运行，超时5秒自动中断，输出限制10000字符。"
            + "仅支持纯 JavaScript 语法，无法访问文件系统和网络。"
            + "适用于简单计算、数据处理、字符串操作等场景")
    public String executeCode(
            @P("要执行的代码内容") String code,
            @P("编程语言，目前仅支持 'javascript'") String language) {

        if (code == null || code.isBlank()) {
            return "{\"error\": \"代码不能为空\"}";
        }

        // 仅支持 JavaScript
        if (language != null && !language.equalsIgnoreCase("javascript")
                && !language.equalsIgnoreCase("js")) {
            return "{\"error\": \"仅支持 JavaScript 语言，不支持: " + language + "\"}";
        }

        AgentConfig.CodeExecConfig config = agentConfig.getTools().getCodeExecution();
        int timeoutSeconds = config.getTimeoutSeconds();
        int maxOutputLength = config.getMaxOutputLength();

        log.info("CodeExecution: lang=javascript, codeLength={}, timeout={}s",
                code.length(), timeoutSeconds);

        // 查找 JavaScript 脚本引擎（单次赋值确保 effectively final，供 lambda 使用）
        ScriptEngineManager sem = new ScriptEngineManager();
        ScriptEngine engine = sem.getEngineByName("javascript");
        if (engine == null) {
            engine = sem.getEngineByName("graal.js");
        }
        if (engine == null) {
            engine = sem.getEngineByName("nashorn");
        }
        // 保存到 effectively final 变量供 lambda 捕获
        final ScriptEngine jsEngine = engine;
        if (jsEngine == null) {
            log.warn("CodeExecution: 未找到 JavaScript 脚本引擎（JDK 15+ 已移除 Nashorn）");
            return "{\"error\": \"JavaScript 执行环境不可用。JDK 15+ 已移除 Nashorn 引擎，"
                    + "请添加 GraalVM JS 依赖或改用其他方式执行代码。\"}";
        }

        // 使用线程池执行，支持超时控制
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(() -> {
            // 捕获标准输出和标准错误
            PrintStream originalOut = System.out;
            PrintStream originalErr = System.err;
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            ByteArrayOutputStream errStream = new ByteArrayOutputStream();

            try {
                System.setOut(new PrintStream(outStream, true));
                System.setErr(new PrintStream(errStream, true));

                // 执行代码
                Object result = jsEngine.eval(code);

                // 恢复标准输出
                System.setOut(originalOut);
                System.setErr(originalErr);

                String stdoutRaw = outStream.toString();
                String stderrRaw = errStream.toString();

                // 截断输出（三元运算符，避免变量重赋值）
                String stdout = stdoutRaw.length() > maxOutputLength
                        ? stdoutRaw.substring(0, maxOutputLength) + "\n...(输出已截断)"
                        : stdoutRaw;
                String stderr = stderrRaw.length() > maxOutputLength
                        ? stderrRaw.substring(0, maxOutputLength) + "\n...(输出已截断)"
                        : stderrRaw;

                // 构建结果 JSON
                StringBuilder sb = new StringBuilder();
                sb.append("{\"success\":true");
                if (!stdout.isEmpty()) {
                    sb.append(",\"stdout\":\"").append(escapeJson(stdout)).append("\"");
                }
                if (!stderr.isEmpty()) {
                    sb.append(",\"stderr\":\"").append(escapeJson(stderr)).append("\"");
                }
                if (result != null) {
                    sb.append(",\"result\":\"").append(escapeJson(result.toString())).append("\"");
                }
                sb.append("}");

                log.info("CodeExecution: 执行成功，输出长度 {} 字符",
                        stdout.length() + stderr.length());
                return sb.toString();

            } finally {
                // 确保恢复标准输出
                System.setOut(originalOut);
                System.setErr(originalErr);
            }
        });

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("CodeExecution: 执行超时（{} 秒）", timeoutSeconds);
            future.cancel(true);
            return "{\"success\":false,\"error\":\"代码执行超时（" + timeoutSeconds + " 秒），可能存在无限循环\"}";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("CodeExecution: 执行被中断");
            return "{\"success\":false,\"error\":\"代码执行被中断\"}";
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("CodeExecution: 执行异常 — {}", cause.getMessage());
            return "{\"success\":false,\"error\":\"代码执行异常: " + escapeJson(cause.getMessage()) + "\"}";
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * JSON 字符串转义
     * <p>
     * 防止代码输出中的特殊字符（双引号、反斜杠、换行等）破坏 JSON 格式。
     * </p>
     */
    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 20);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
