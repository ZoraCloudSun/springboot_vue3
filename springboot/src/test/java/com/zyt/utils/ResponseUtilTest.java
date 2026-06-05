package com.zyt.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ResponseUtil 工具类测试")
class ResponseUtilTest {

    // ==================== 构造函数 ====================

    @Test
    @DisplayName("无参构造：所有字段为 null")
    void shouldHaveNullFields_whenDefaultConstructor() {
        ResponseUtil r = new ResponseUtil();
        assertNull(r.getCode());
        assertNull(r.getMsg());
        assertNull(r.getData());
    }

    @Test
    @DisplayName("三参构造：正确设置 code、msg、data")
    void shouldSetAllFields_whenThreeArgConstructor() {
        ResponseUtil r = new ResponseUtil(200, "success", "data");
        assertEquals(200, r.getCode());
        assertEquals("success", r.getMsg());
        assertEquals("data", r.getData());
    }

    // ==================== getter / setter ====================

    @Test
    @DisplayName("setter/getter：正确读写")
    void shouldGetAndSetCorrectly() {
        ResponseUtil r = new ResponseUtil();
        r.setCode(404);
        r.setMsg("not found");
        r.setData("missing");
        assertEquals(404, r.getCode());
        assertEquals("not found", r.getMsg());
        assertEquals("missing", r.getData());
    }

    // ==================== success() 工厂方法 ====================

    @Test
    @DisplayName("success()：code=200, msg='success', data=null")
    void shouldReturnSuccessWithDefaults_whenNoArgs() {
        ResponseUtil r = ResponseUtil.success();
        assertEquals(200, r.getCode());
        assertEquals("success", r.getMsg());
        assertNull(r.getData());
    }

    @Test
    @DisplayName("success(data)：code=200, msg='success', data=传入数据")
    void shouldReturnSuccessWithData_whenDataArg() {
        ResponseUtil r = ResponseUtil.success("hello");
        assertEquals(200, r.getCode());
        assertEquals("success", r.getMsg());
        assertEquals("hello", r.getData());
    }

    @Test
    @DisplayName("success(msg, data)：code=200, msg 和 data 为传入值")
    void shouldReturnSuccessWithCustomMsgAndData() {
        ResponseUtil r = ResponseUtil.success("操作完成", 42);
        assertEquals(200, r.getCode());
        assertEquals("操作完成", r.getMsg());
        assertEquals(42, r.getData());
    }

    // ==================== error() 工厂方法 ====================

    @Test
    @DisplayName("error(code, msg)：code 和 msg 为传入值，data=null")
    void shouldReturnErrorWithCodeAndMsg() {
        ResponseUtil r = ResponseUtil.error(400, "参数错误");
        assertEquals(400, r.getCode());
        assertEquals("参数错误", r.getMsg());
        assertNull(r.getData());
    }

    @Test
    @DisplayName("error(code, msg, data)：三个字段均为传入值")
    void shouldReturnErrorWithCodeMsgAndData() {
        ResponseUtil r = ResponseUtil.error(422, "校验失败", "field: email");
        assertEquals(422, r.getCode());
        assertEquals("校验失败", r.getMsg());
        assertEquals("field: email", r.getData());
    }

    // ==================== toJsonError() ====================

    @Test
    @DisplayName("toJsonError：生成合法 JSON 字符串")
    void shouldProduceValidJson() {
        String json = ResponseUtil.toJsonError(401, "未携带 Token");
        assertNotNull(json);
        assertTrue(json.contains("\"code\""));
        assertTrue(json.contains("\"msg\""));
        assertTrue(json.contains("\"data\""));
        assertTrue(json.contains("401"));
        assertTrue(json.contains("未携带 Token"));
        // 验证 data 为 null
        assertTrue(json.contains("\"data\":null") || json.contains("\"data\": null"));
    }

    @Test
    @DisplayName("toJsonError：中文特殊字符正确转义")
    void shouldEscapeSpecialCharacters() {
        String json = ResponseUtil.toJsonError(400, "包含\"双引号\"和\\反斜杠");
        assertNotNull(json);
        // Jackson 应正确转义双引号
        assertFalse(json.contains("\"包含\"双引号\""));
    }

    @Test
    @DisplayName("toJsonError：5xx 错误码正确序列化")
    void shouldSerializeServerError() {
        String json = ResponseUtil.toJsonError(500, "服务器内部错误");
        assertNotNull(json);
        assertTrue(json.contains("500"));
    }
}
