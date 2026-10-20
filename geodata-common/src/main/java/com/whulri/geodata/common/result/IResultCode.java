package com.whulri.geodata.common.result;

import java.io.Serializable;

/**
 * 结果码接口（迁移自 BladeX org.springblade.core.tool.api.IResultCode）。
 */
public interface IResultCode extends Serializable {

    int getCode();

    String getMsg();
}
