package com.captcha.captcha.vo;

public class ResultVO<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ResultVO<T> ok(T data) {
        ResultVO<T> vo = new ResultVO<>();
        vo.setCode(200);
        vo.setMessage("success");
        vo.setData(data);
        return vo;
    }

    public static <T> ResultVO<T> ok() {
        return ok(null);
    }

    public static <T> ResultVO<T> fail(int code, String message) {
        ResultVO<T> vo = new ResultVO<>();
        vo.setCode(code);
        vo.setMessage(message);
        return vo;
    }

    public static <T> ResultVO<T> fail(String message) {
        return fail(400, message);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
