package com.whulri.geodata.common.callback;

import java.io.InputStream;

/**
 * 外部进程输出回调（反编译自 dev-common-1.1.8）
 */
public interface ExeOutputCallback {

    void infoOutput(InputStream is);

    void errorOutput(InputStream es);
}
