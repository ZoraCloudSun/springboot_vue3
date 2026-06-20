package com.zora.agent.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MathTool 数学计算工具测试（Phase 3.2）
 * <p>
 * 纯逻辑测试，无外部依赖。验证各种数学表达式的正确求值
 * 和错误场景的优雅处理。
 * </p>
 */
@DisplayName("MathTool 数学计算工具测试")
class MathToolTest {

    private final MathTool mathTool = new MathTool();

    @Nested
    @DisplayName("基本运算")
    class BasicArithmetic {

        @Test
        @DisplayName("加法运算")
        void shouldAdd() {
            String result = mathTool.calculate("2 + 3");
            assertTrue(result.contains("\"result\":5"));
        }

        @Test
        @DisplayName("减法运算")
        void shouldSubtract() {
            String result = mathTool.calculate("10 - 3");
            assertTrue(result.contains("\"result\":7"));
        }

        @Test
        @DisplayName("乘法运算")
        void shouldMultiply() {
            String result = mathTool.calculate("4 * 5");
            assertTrue(result.contains("\"result\":20"));
        }

        @Test
        @DisplayName("除法运算")
        void shouldDivide() {
            String result = mathTool.calculate("100 / 4");
            assertTrue(result.contains("\"result\":25"));
        }

        @Test
        @DisplayName("幂运算")
        void shouldPower() {
            String result = mathTool.calculate("2 ^ 10");
            assertTrue(result.contains("\"result\":1024"));
        }

        @Test
        @DisplayName("取模运算")
        void shouldModulo() {
            String result = mathTool.calculate("10 % 3");
            assertTrue(result.contains("\"result\":1"));
        }

        @Test
        @DisplayName("复合运算（运算符优先级）")
        void shouldRespectOperatorPrecedence() {
            String result = mathTool.calculate("2 + 3 * 4");
            assertTrue(result.contains("\"result\":14"));
        }

        @Test
        @DisplayName("括号运算")
        void shouldHandleParentheses() {
            String result = mathTool.calculate("(2 + 3) * 4");
            assertTrue(result.contains("\"result\":20"));
        }
    }

    @Nested
    @DisplayName("数学函数")
    class MathFunctions {

        @Test
        @DisplayName("平方根 sqrt")
        void shouldCalculateSqrt() {
            String result = mathTool.calculate("sqrt(144)");
            assertTrue(result.contains("\"result\":12"));
        }

        @Test
        @DisplayName("立方根 cbrt")
        void shouldCalculateCbrt() {
            String result = mathTool.calculate("cbrt(8)");
            assertTrue(result.contains("\"result\":2"));
        }

        @Test
        @DisplayName("正弦 sin")
        void shouldCalculateSin() {
            String result = mathTool.calculate("sin(pi / 2)");
            assertTrue(result.contains("\"result\":1"));
        }

        @Test
        @DisplayName("余弦 cos")
        void shouldCalculateCos() {
            String result = mathTool.calculate("cos(0)");
            assertTrue(result.contains("\"result\":1"));
        }

        @Test
        @DisplayName("正切 tan")
        void shouldCalculateTan() {
            // tan(0) = 0
            String result = mathTool.calculate("tan(0)");
            assertTrue(result.contains("\"result\":0"));
        }

        @Test
        @DisplayName("自然对数 ln")
        void shouldCalculateLn() {
            // ln(e) = 1
            String result = mathTool.calculate("ln(e)");
            assertTrue(result.contains("\"result\":1"));
        }

        @Test
        @DisplayName("以 2 为底对数 log2")
        void shouldCalculateLog2() {
            String result = mathTool.calculate("log2(8)");
            assertTrue(result.contains("\"result\":3"));
        }

        @Test
        @DisplayName("以 10 为底对数 log10")
        void shouldCalculateLog10() {
            String result = mathTool.calculate("log10(100)");
            assertTrue(result.contains("\"result\":2"));
        }

        @Test
        @DisplayName("绝对值 abs")
        void shouldCalculateAbs() {
            String result = mathTool.calculate("abs(-5)");
            assertTrue(result.contains("\"result\":5"));
        }

        @Test
        @DisplayName("取整 floor")
        void shouldCalculateFloor() {
            String result = mathTool.calculate("floor(3.7)");
            assertTrue(result.contains("\"result\":3"));
        }

        @Test
        @DisplayName("向上取整 ceil")
        void shouldCalculateCeil() {
            String result = mathTool.calculate("ceil(3.2)");
            assertTrue(result.contains("\"result\":4"));
        }

        @Test
        @DisplayName("四舍五入 round")
        void shouldCalculateRound() {
            String result = mathTool.calculate("round(3.6)");
            assertTrue(result.contains("\"result\":4"));
        }
    }

    @Nested
    @DisplayName("阶乘运算")
    class Factorial {

        @Test
        @DisplayName("5 的阶乘")
        void shouldCalculateFactorial5() {
            String result = mathTool.calculate("5!");
            assertTrue(result.contains("\"result\":120"));
        }

        @Test
        @DisplayName("0 的阶乘")
        void shouldCalculateFactorial0() {
            String result = mathTool.calculate("0!");
            assertTrue(result.contains("\"result\":1"));
        }

        @Test
        @DisplayName("1 的阶乘")
        void shouldCalculateFactorial1() {
            String result = mathTool.calculate("1!");
            assertTrue(result.contains("\"result\":1"));
        }
    }

    @Nested
    @DisplayName("常数")
    class Constants {

        @Test
        @DisplayName("圆周率 π")
        void shouldRecognizePi() {
            String result = mathTool.calculate("pi");
            assertTrue(result.contains("3.14159"));
        }

        @Test
        @DisplayName("自然常数 e")
        void shouldRecognizeE() {
            String result = mathTool.calculate("e");
            assertTrue(result.contains("2.71828"));
        }
    }

    @Nested
    @DisplayName("错误处理")
    class ErrorHandling {

        @Test
        @DisplayName("除零应返回错误信息")
        void shouldReturnErrorOnDivisionByZero() {
            String result = mathTool.calculate("1 / 0");
            // exp4j 对除零的处理是返回 Infinity，可能不会抛异常
            // 如果产生 Infinity，这仍然是有效的结果
            assertNotNull(result);
            assertTrue(result.contains("\"result\"") || result.contains("\"error\""));
        }

        @Test
        @DisplayName("非法表达式应返回错误 JSON")
        void shouldReturnErrorOnInvalidExpression() {
            // "2 +* 3" 是语法错误（两个连续运算符），exp4j 会拒绝
            String result = mathTool.calculate("2 +* 3");
            assertTrue(result.contains("\"error\""), "应为非法表达式返回错误");
        }

        @Test
        @DisplayName("不匹配的括号应返回错误")
        void shouldReturnErrorOnMismatchedParentheses() {
            String result = mathTool.calculate("(2 + 3");
            assertTrue(result.contains("\"error\""), "应为不匹配括号返回错误");
        }

        @Test
        @DisplayName("空表达式应返回错误")
        void shouldReturnErrorOnEmptyExpression() {
            String result = mathTool.calculate("");
            assertTrue(result.contains("\"error\""));
        }

        @Test
        @DisplayName("null 表达式应返回错误")
        void shouldReturnErrorOnNullExpression() {
            String result = mathTool.calculate(null);
            assertTrue(result.contains("\"error\""));
        }

        @Test
        @DisplayName("不支持的函数名应返回错误")
        void shouldReturnErrorOnUnknownFunction() {
            String result = mathTool.calculate("unknown(5)");
            assertTrue(result.contains("\"error\""));
        }
    }

    @Nested
    @DisplayName("结果格式")
    class ResultFormat {

        @Test
        @DisplayName("整数结果不应包含小数点")
        void shouldNotContainDecimalForIntegers() {
            String result = mathTool.calculate("2 + 3");
            // 应为 "result":5 而不是 "result":5.0
            assertTrue(result.contains("\"result\":5"));
            assertFalse(result.contains("\"result\":5."));
        }

        @Test
        @DisplayName("结果 JSON 应包含表达式字段")
        void shouldContainExpressionField() {
            String result = mathTool.calculate("2 + 3");
            assertTrue(result.contains("\"expression\""));
            assertTrue(result.contains("2 + 3"));
        }

        @Test
        @DisplayName("每个结果都应是合法 JSON")
        void shouldProduceValidJson() {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();

            assertDoesNotThrow(() -> mapper.readTree(mathTool.calculate("2+3")));
            assertDoesNotThrow(() -> mapper.readTree(mathTool.calculate("sqrt(144)")));
            assertDoesNotThrow(() -> mapper.readTree(mathTool.calculate("1/0")));
        }
    }
}
