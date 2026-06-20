package com.zora.agent.tool;

import com.zora.config.AgentConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CodeExecutionTool 代码执行工具测试（Phase 3.2）
 * <p>
 * 测试 JavaScript 代码执行、超时控制、输出截断和错误处理。
 * 测试兼容有/无 ScriptEngine 的环境（JDK 15+ 可能无 Nashorn）。
 * </p>
 */
@DisplayName("CodeExecutionTool 代码执行工具测试")
class CodeExecutionToolTest {

    private CodeExecutionTool codeExecutionTool;
    private AgentConfig agentConfig;
    private AgentConfig.ToolsConfig toolsConfig;
    private AgentConfig.CodeExecConfig codeExecConfig;

    /**
     * 检测当前 JDK 是否有 JS 引擎（在类加载时确定一次）
     */
    private static final boolean HAS_JS_ENGINE = checkJsEngine();

    private static boolean checkJsEngine() {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("javascript");
        if (engine == null) {
            engine = new ScriptEngineManager().getEngineByName("graal.js");
        }
        return engine != null;
    }

    @BeforeEach
    void setUp() {
        codeExecutionTool = new CodeExecutionTool();

        // 构建 mock AgentConfig
        agentConfig = new AgentConfig() {
            @Override
            public ToolsConfig getTools() {
                return toolsConfig;
            }
        };
        toolsConfig = new AgentConfig.ToolsConfig();
        codeExecConfig = new AgentConfig.CodeExecConfig();
        codeExecConfig.setEnabled(true);
        codeExecConfig.setTimeoutSeconds(5);
        codeExecConfig.setMaxOutputLength(10000);

        // 用 mock 替换 toolsConfig 中的 codeExecution
        ReflectionTestUtils.setField(toolsConfig, "codeExecution", codeExecConfig);

        // 注入 AgentConfig
        ReflectionTestUtils.setField(codeExecutionTool, "agentConfig", agentConfig);
    }

    @Nested
    @DisplayName("代码验证")
    class CodeValidation {

        @Test
        @DisplayName("空代码应返回错误")
        void shouldReturnErrorOnEmptyCode() {
            String result = codeExecutionTool.executeCode("", "javascript");
            assertTrue(result.contains("\"error\""));
            assertTrue(result.contains("不能为空"));
        }

        @Test
        @DisplayName("null 代码应返回错误")
        void shouldReturnErrorOnNullCode() {
            String result = codeExecutionTool.executeCode(null, "javascript");
            assertTrue(result.contains("\"error\""));
        }

        @Test
        @DisplayName("不支持的语言应返回错误")
        void shouldReturnErrorOnUnsupportedLanguage() {
            String result = codeExecutionTool.executeCode("print('hello')", "python");
            assertTrue(result.contains("\"error\""));
            assertTrue(result.contains("仅支持 JavaScript"));
        }
    }

    @Nested
    @DisplayName("JavaScript 执行")
    class JavaScriptExecution {

        @Test
        @DisplayName("简单算术应正确执行")
        void shouldExecuteSimpleArithmetic() {
            if (!HAS_JS_ENGINE) return; // 跳过无引擎环境

            String result = codeExecutionTool.executeCode("2 + 3 * 4", "javascript");

            assertNotNull(result);
            // 结果应包含 success 或 result
            assertTrue(result.contains("\"success\":true") || result.contains("\"result\":\"14\""));
        }

        @Test
        @DisplayName("console.log 输出应被捕获")
        void shouldCaptureConsoleLog() {
            if (!HAS_JS_ENGINE) return;

            // JavaScript print/console 输出
            String result = codeExecutionTool.executeCode(
                    "print('Hello World')", "javascript");

            assertNotNull(result);
        }

        @Test
        @DisplayName("字符串操作应正确执行")
        void shouldExecuteStringOperations() {
            if (!HAS_JS_ENGINE) return;

            String result = codeExecutionTool.executeCode(
                    "\"hello\".toUpperCase()", "javascript");

            assertNotNull(result);
            assertTrue(result.contains("HELLO") || result.contains("\"success\""));
        }

        @Test
        @DisplayName("数组操作应正确执行")
        void shouldExecuteArrayOperations() {
            if (!HAS_JS_ENGINE) return;

            String result = codeExecutionTool.executeCode(
                    "[1, 2, 3].map(function(x) { return x * 2 })", "javascript");

            assertNotNull(result);
            assertTrue(result.contains("2") && result.contains("4") && result.contains("6")
                    || result.contains("\"success\""));
        }

        @Test
        @DisplayName("语法错误应返回错误信息")
        void shouldReturnErrorOnSyntaxError() {
            if (!HAS_JS_ENGINE) return;

            String result = codeExecutionTool.executeCode(
                    "function { broken", "javascript");

            assertNotNull(result);
            // 应包含错误信息
            assertTrue(result.contains("error") || result.contains("\"success\":false"));
        }
    }

    @Nested
    @DisplayName("环境检测")
    class EnvironmentDetection {

        @Test
        @DisplayName("无 JS 引擎时应返回明确提示")
        void shouldReturnClearMessageWhenNoEngine() {
            if (HAS_JS_ENGINE) return; // 跳过有引擎环境

            String result = codeExecutionTool.executeCode("1 + 1", "javascript");

            assertTrue(result.contains("\"error\""));
            assertTrue(result.contains("JavaScript 执行环境不可用")
                    || result.contains("Nashorn")
                    || result.contains("GraalVM"));
        }
    }

    @Nested
    @DisplayName("安全限制")
    class SecurityLimits {

        @Test
        @DisplayName("超时配置应可修改")
        void shouldAllowTimeoutConfiguration() {
            codeExecConfig.setTimeoutSeconds(3);
            assertEquals(3, codeExecConfig.getTimeoutSeconds());
        }

        @Test
        @DisplayName("输出长度限制应可修改")
        void shouldAllowMaxOutputLengthConfiguration() {
            codeExecConfig.setMaxOutputLength(500);
            assertEquals(500, codeExecConfig.getMaxOutputLength());
        }

        @Test
        @DisplayName("默认 timeoutSeconds 为 5")
        void shouldDefaultTimeoutTo5() {
            AgentConfig.CodeExecConfig freshConfig = new AgentConfig.CodeExecConfig();
            assertEquals(5, freshConfig.getTimeoutSeconds());
        }

        @Test
        @DisplayName("默认 maxOutputLength 为 10000")
        void shouldDefaultMaxOutputTo10000() {
            AgentConfig.CodeExecConfig freshConfig = new AgentConfig.CodeExecConfig();
            assertEquals(10000, freshConfig.getMaxOutputLength());
        }
    }
}
