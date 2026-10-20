package com.whulri.geodata.model3d.exception;

/**
 * 三维数据业务异常
 */
public class Model3dException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public Model3dException(String message) {
        super(message);
    }

    public Model3dException(String message, Throwable cause) {
        super(message, cause);
    }
}
