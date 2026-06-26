package com.captcha.captcha.vo;

/**
 * 统一响应结果封装类。
 *
 * 所有 Controller 接口的返回值统一使用此类包装：
 * - code：状态码，200 表示成功，其他表示失败
 * - message：描述信息（成功时固定为 "success"，失败时为具体原因）
 * - data：泛型数据，接口返回的实际业务数据
 *
 * 提供静态工厂方法简化调用：
 * - ok(data)：成功响应
 * - fail(message)：失败响应（默认状态码 400）
 * - fail(code, message)：自定义状态码的失败响应
 */
public class ResultVO<T> {
    /** 响应状态码，200=成功，其他=失败 */
    private int code;
    /** 响应描述信息 */
    private String message;
    /** 泛型数据，接口返回的实际业务数据 */
    private T data;

    /** 成功响应（带数据） */
    public static <T> ResultVO<T> ok(T data) {
        ResultVO<T> vo = new ResultVO<>();
        vo.setCode(200);
        vo.setMessage("success");
        vo.setData(data);
        return vo;
    }

    /** 成功响应（无数据） */
    public static <T> ResultVO<T> ok() {
        return ok(null);
    }

    /** 失败响应（自定义状态码） */
    public static <T> ResultVO<T> fail(int code, String message) {
        ResultVO<T> vo = new ResultVO<>();
        vo.setCode(code);
        vo.setMessage(message);
        return vo;
    }

    /** 失败响应（默认状态码 400） */
    public static <T> ResultVO<T> fail(String message) {
        return fail(400, message);
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
