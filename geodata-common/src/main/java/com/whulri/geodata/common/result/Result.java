package com.whulri.geodata.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.whulri.geodata.common.result.IResultCode;
import com.whulri.geodata.common.result.ResultCode;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 状态码
     */
    private int code;
    /**
     * 是否成功
     */
    private boolean success;
    /**
     * 承载数据
     */
    private T result;

    /**
     * 返回消息
     */
    private String msg;

    public static <T> Result<T> data(T data) {
        return data(data, "操作成功");
    }

    public static <T> Result<T> data(T data, String msg) {
        return data(200, data, msg);
    }

    public static <T> Result<T> data(int code, T data, String msg) {
        return new Result<>(code, data, data == null ? "暂无承载数据" : msg);
    }

    private Result(int code, T data, String msg) {
        this.code = code;
        this.result = data;
        this.msg = msg;
        this.success = ResultCode.SUCCESS.getCode() == code;
    }

    public static <T> Result<T> status(boolean flag) {
        return flag ? success("操作成功") : fail("操作失败");
    }


    public static <T> Result<T> success(String msg) {
        return new Result<>(ResultCode.SUCCESS, msg);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg);
    }

    public static <T> Result<T> fail(String msg) {
        return new Result<>(ResultCode.FAILURE, msg);
    }

    private Result(int resultCode, String msg) {
        this(resultCode, null, msg);
    }

    private Result(IResultCode resultCode, String msg) {
        this(resultCode, null, msg);
    }

    private Result(IResultCode resultCode, T data, String msg) {
        this(resultCode.getCode(), data, msg);
    }

}
