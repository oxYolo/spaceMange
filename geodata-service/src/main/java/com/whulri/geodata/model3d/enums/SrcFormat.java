package com.whulri.geodata.model3d.enums;

/**
 * 三维源格式（与 sdms_model3d.src_format 一致，小写后缀语义）
 */
public enum SrcFormat {

    /** 3D Tiles 成果包（zip 内含 tileset.json） */
    TILESET(null),

    /** 倾斜摄影 OSGB → C++ 工具链 3dtiles */
    OSGB("3dtiles"),

    /** 点云 LAS → Python 工具链 py3dtiles */
    LAS("py3dtiles"),

    /** 点云 LAZ → Python 工具链 py3dtiles */
    LAZ("py3dtiles"),

    /** 单体 OBJ → Node 工具链 obj2gltf */
    OBJ("obj2gltf"),

    /** 单体 FBX → Node 工具链 FBX2glTF */
    FBX("fbx2gltf"),

    /** BIM IFC → IfcConvert */
    IFC("ifcconvert");

    /** 对应的转换工具标识（toolId）；null 表示无需转换 */
    private final String toolId;

    SrcFormat(String toolId) {
        this.toolId = toolId;
    }

    public String getToolId() {
        return toolId;
    }

    /** 是否需要转换 */
    public boolean needConvert() {
        return toolId != null;
    }
}
