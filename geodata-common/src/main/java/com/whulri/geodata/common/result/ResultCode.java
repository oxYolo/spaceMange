package com.whulri.geodata.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 常用结果码枚举（迁移自 BladeX org.springblade.core.tool.api.ResultCode）。
 */
@Getter
@AllArgsConstructor
public enum ResultCode implements IResultCode {

    SUCCESS(200, "操作成功"),
    FAILURE(400, "操作失败"),
    UNAUTHORIZED(401, "未经授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源未找到"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    INTERNAL_SERVER_ERROR(500, "服务器内部错误");

    private final int code;
    private final String msg;

    @Override
    public String toString() {
        return "[" + this.code + "] " + this.msg;
    }
}
