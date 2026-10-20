package com.whulri.geodata.convert.core;

/**
 * 三维转换通用异常
 */
public class ConvertException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConvertException(String message) {
        super(message);
    }

    public ConvertException(String message, Throwable cause) {
        super(message, cause);
    }
}
