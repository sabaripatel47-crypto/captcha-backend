package com.captcha.captcha.vo;

/**
 * 统一响应结果封装类。
 *
 * 所有 Controller 接口的返回值统一使用此类包装：
 * - code：状态码，200 表示成功，其他表示失败
 * - message：描述信息(成功为success,失败为具体的原因)
 * - data：泛型,具体返回的响应数据
 *
 * 提供相应的静态方法来使用
 * - ok(data)：成功响应
 * - fail(message)：失败响应（默认状态码 400）
 * - fail(code, message)：自定义状态码的失败响应
 */
public class ResultVO<T> {
    private int code;
    private String message;
    private T data;

    /** 成功响应（带数据）,<T>传参支持任意类型,ResultVO<T>:返回数据支持任意类型 */
    public static <T> ResultVO<T> ok(T data) {
        ResultVO<T> vo = new ResultVO<>();
        vo.setCode(200);//设置状态码
        vo.setMessage("success");//设置描述信息
        vo.setData(data);//设置数据
        return vo;
    }

    /** 成功响应（无数据） */
    public static <T> ResultVO<T> ok() {
        return ok(null);//此时的data为空
    }

    /** 失败响应（自定义状态码） */
    public static <T> ResultVO<T> fail(int code, String message) {
        ResultVO<T> vo = new ResultVO<>();
        vo.setCode(code);//设置状态码
        vo.setMessage(message);//设置报错信息
        return vo;
    }

    /** 默认失败响应（默认状态码 400） */
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
