package com.whulri.geodata.common.enums;

/**
 * 操作系统枚举（反编译自 dev-common-1.1.8）
 */
public enum OSEnum {

    WINDOWS(0, "Windows"),

    LINUX(1, "Linux");

    private final int code;
    private final String des;

    OSEnum(int code, String des) {
        this.code = code;
        this.des = des;
    }

    public int getCode() {
        return this.code;
    }

    public String getDes() {
        return this.des;
    }

    public static boolean isLinux(int code) {
        return code == OSEnum.LINUX.code;
    }

    public static boolean isWindows(int code) {
        return code == OSEnum.WINDOWS.code;
    }
}
