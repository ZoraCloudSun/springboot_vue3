package com.zora.agent.tool;

import dev.langchain4j.agent.tool.P;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.tokenizer.UnknownFunctionOrVariableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 数学计算工具（Phase 3.2）
 * <p>
 * 使用 exp4j 库安全地对数学表达式求值，避免使用 JavaScript {@code eval()} 的安全风险。
 * exp4j 是一个轻量级数学表达式解析器，仅支持纯数学运算，无法执行任意代码。
 * </p>
 *
 * <h3>支持的运算和函数</h3>
 * <table>
 * <tr><th>类别</th><th>支持项</th></tr>
 * <tr><td>基本运算</td><td>+, -, *, /, ^, %</td></tr>
 * <tr><td>三角函数</td><td>sin(x), cos(x), tan(x)</td></tr>
 * <tr><td>反三角函数</td><td>asin(x), acos(x), atan(x)</td></tr>
 * <tr><td>双曲函数</td><td>sinh(x), cosh(x), tanh(x)</td></tr>
 * <tr><td>对数函数</td><td>log(x), log2(x), log10(x), ln(x)</td></tr>
 * <tr><td>幂/根</td><td>sqrt(x), cbrt(x), x^y</td></tr>
 * <tr><td>取整</td><td>abs(x), ceil(x), floor(x), round(x)</td></tr>
 * <tr><td>常数</td><td>pi, e</td></tr>
 * <tr><td>阶乘</td><td>x!（仅非负整数）</td></tr>
 * </table>
 *
 * <h3>安全特性</h3>
 * <ul>
 * <li>仅支持数学表达式，无法执行代码</li>
 * <li>变量和函数在白名单内</li>
 * <li>除零错误被捕获并返回友好提示</li>
 * </ul>
 *
 * <h3>启用/禁用</h3>
 * <p>
 * 通过 {@code agent.tools.math.enabled} 配置控制工具开关。
 * </p>
 */
@Component
public class MathTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(MathTool.class);

    /**
     * 安全地对数学表达式求值
     * <p>
     * 当 AI 需要进行数学计算时调用此工具，支持基本运算、三角函数、对数等。
     * 如果表达式非法或发生除零等错误，返回包含错误信息的 JSON。
     * </p>
     *
     * <h3>示例</h3>
     * <ul>
     * <li>{@code 2 + 3 * 4} → 14</li>
     * <li>{@code sqrt(144)} → 12</li>
     * <li>{@code sin(pi / 2)} → 1.0</li>
     * <li>{@code log2(8)} → 3.0</li>
     * <li>{@code 5!} → 120</li>
     * </ul>
     *
     * @param expression 数学表达式字符串，例如 "2+3*4"、"sqrt(144)"、"sin(pi/2)"
     * @return JSON 格式结果：{"expression":"...", "result": 数值} 或 {"error":"错误描述"}
     */
    @dev.langchain4j.agent.tool.Tool("执行数学计算。支持基本运算(+,-,*,/,^,%)、三角函数(sin,cos,tan)、"
            + "反三角函数(asin,acos,atan)、双曲函数(sinh,cosh,tanh)、对数(log,log2,log10,ln)、"
            + "平方根(sqrt)、立方根(cbrt)、绝对值(abs)、取整(ceil,floor,round)、阶乘(!)、常数(pi,e)。"
            + "输入为数学表达式字符串，例如'2+3*4'或'sqrt(144)'")
    public String calculate(
            @P("数学表达式字符串，例如 '2+3*4'、'sqrt(144)'、'sin(pi/2)'") String expression) {

        if (expression == null || expression.isBlank()) {
            return "{\"error\": \"表达式不能为空\"}";
        }

        log.info("MathTool: 计算表达式 \"{}\"", expression);

        try {
            // 使用 exp4j ExpressionBuilder 安全解析和计算
            Expression exp = new ExpressionBuilder(expression)
                    .functions(
                            // 三角函数
                            new net.objecthunter.exp4j.function.Function("sin", 1) {
                                @Override
                                public double apply(double... args) {
                                    return Math.sin(args[0]);
                                }
                            },
                            new net.objecthunter.exp4j.function.Function("cos", 1) {
                                @Override
                                public double apply(double... args) {
                                    return Math.cos(args[0]);
                                }
                            },
                            new net.objecthunter.exp4j.function.Function("tan", 1) {
                                @Override
                                public double apply(double... args) {
                                    return Math.tan(args[0]);
                                }
                            },
                            // 反三角函数
                            new net.objecthunter.exp4j.function.Function("asin", 1) {
                                @Override
                                public double apply(double... args) {
                                    return Math.asin(args[0]);
                                }
                            },
                            new net.objecthunter.exp4j.function.Function("acos", 1) {
                                @Override
                                public double apply(double... args) {
                                    return Math.acos(args[0]);
                                }
                            },
                            new net.objecthunter.exp4j.function.Function("atan", 1) {
                                @Override
                                public double apply(double... args) {
                                    return Math.atan(args[0]);
                                }
                            },
                            // 双曲函数
                            new net.objecthunter.exp4j.function.Function("sinh", 1) {
                                @Override
                                public double apply(double... args) {
                                    return Math.sinh(args[0]);
                                }
                            },
                            new net.objecthunter.exp4j.function.Function("cosh", 1) {
                                @Override
                                public double apply(double... args) {
                                    return Math.cosh(args[0]);
                                }
                            },
                            new net.objecthunter.exp4j.function.Function("tanh", 1) {
                                @Override
                                public double apply(double... args) {
                                    return Math.tanh(args[0]);
                                }
                            },
                            // 对数函数（exp4j 内置 log 是自然对数，这里添加自定义 log 为 log10）
                            new net.objecthunter.exp4j.function.Function("log2", 1) {
                                @Override
                                public double apply(double... args) {
                                    return Math.log(args[0]) / Math.log(2);
                                }
                            },
                            new net.objecthunter.exp4j.function.Function("log10", 1) {
                                @Override
                                public double apply(double... args) {
                                    return Math.log10(args[0]);
                                }
                            },
                            new net.objecthunter.exp4j.function.Function("ln", 1) {
                                @Override
                                public double apply(double... args) {
                                    return Math.log(args[0]);
                                }
                            },
                            // 立方根
                            new net.objecthunter.exp4j.function.Function("cbrt", 1) {
                                @Override
                                public double apply(double... args) {
                                    return Math.cbrt(args[0]);
                                }
                            },
                            // 取整函数
                            new net.objecthunter.exp4j.function.Function("round", 1) {
                                @Override
                                public double apply(double... args) {
                                    return Math.round(args[0]);
                                }
                            },
                            new net.objecthunter.exp4j.function.Function("floor", 1) {
                                @Override
                                public double apply(double... args) {
                                    return Math.floor(args[0]);
                                }
                            },
                            new net.objecthunter.exp4j.function.Function("ceil", 1) {
                                @Override
                                public double apply(double... args) {
                                    return Math.ceil(args[0]);
                                }
                            }
                    )
                    .operator(
                            // 阶乘运算符（后缀）
                            new net.objecthunter.exp4j.operator.Operator("!", 1, true,
                                    net.objecthunter.exp4j.operator.Operator.PRECEDENCE_POWER + 1) {
                                @Override
                                public double apply(double... args) {
                                    double n = args[0];
                                    if (n < 0 || n != Math.floor(n)) {
                                        throw new IllegalArgumentException("阶乘仅支持非负整数，输入: " + n);
                                    }
                                    double result = 1;
                                    for (int i = 2; i <= (int) n; i++) {
                                        result *= i;
                                    }
                                    return result;
                                }
                            }
                    )
                    .variables("pi", "e", "π")
                    .build();

            // 设置常数
            exp.setVariable("pi", Math.PI);
            exp.setVariable("e", Math.E);
            exp.setVariable("π", Math.PI);  // Unicode π 别名

            double result = exp.evaluate();
            log.info("MathTool: \"{}\" = {}", expression, result);

            // 返回 JSON 结果
            return String.format("{\"expression\":\"%s\",\"result\":%s}",
                    escapeJson(expression),
                    formatResult(result));

        } catch (UnknownFunctionOrVariableException e) {
            log.warn("MathTool: 不支持的函数或变量 — {}", e.getMessage());
            return "{\"error\": \"不支持的函数或变量: " + escapeJson(e.getMessage()) + "\"}";
        } catch (ArithmeticException e) {
            log.warn("MathTool: 数学错误 — {}", e.getMessage());
            return "{\"error\": \"数学计算错误: " + escapeJson(e.getMessage()) + "\"}";
        } catch (IllegalArgumentException e) {
            log.warn("MathTool: 参数非法 — {}", e.getMessage());
            return "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}";
        } catch (Exception e) {
            log.error("MathTool: 表达式计算失败 \"{}\" — {}", expression, e.getMessage());
            return "{\"error\": \"表达式解析失败: " + escapeJson(e.getMessage()) + "\"}";
        }
    }

    /**
     * 格式化计算结果
     * <p>
     * 整数不显示小数部分，浮点数保留 10 位有效数字。
     * </p>
     */
    private String formatResult(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        // 使用科学计数法的边界情况
        if (Math.abs(value) < 1e-10 || Math.abs(value) > 1e10) {
            return String.format("%.10e", value);
        }
        return String.format("%.10f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    /**
     * JSON 字符串转义
     * <p>
     * 防止表达式中的特殊字符（双引号、反斜杠等）破坏 JSON 格式。
     * </p>
     */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
