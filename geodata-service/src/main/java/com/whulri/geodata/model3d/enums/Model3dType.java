package com.whulri.geodata.model3d.enums;

/**
 * 三维数据类型
 */
public enum Model3dType {

    /** 现成 3D Tiles 成果（直传托管，无需转换） */
    TILESET,

    /** 倾斜摄影（OSGB，Smart3D/ContextCapture 产出） */
    OSGB,

    /** 点云（LAS/LAZ） */
    POINTCLOUD,

    /** 单体模型（OBJ/FBX → glb） */
    SINGLE,

    /** BIM（IFC → glTF） */
    BIM;

    /** 该类型允许的源格式（小写后缀） */
    public java.util.Set<String> allowedSrcFormats() {
        return switch (this) {
            case TILESET -> java.util.Set.of("tileset");
            case OSGB -> java.util.Set.of("osgb");
            case POINTCLOUD -> java.util.Set.of("las", "laz");
            case SINGLE -> java.util.Set.of("obj", "fbx");
            case BIM -> java.util.Set.of("ifc");
        };
    }
}
