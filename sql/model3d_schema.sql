-- ============================================================
-- 三维数据管理 sdms_model3d（新增，不改动既有 20 张表）
-- 目标：PostgreSQL 12+ / PostGIS 3.x
-- 状态码：0 上传中 / 1 已上传(待转换或已可直接发布) / 2 已排队 / 3 转换中 / 4 已发布 / 5 失败
-- 类型码：TILESET(现成3D Tiles) / OSGB(倾斜摄影) / POINTCLOUD(点云) / SINGLE(单体模型) / BIM
-- 源格式：TILESET / OSGB / LAS / LAZ / OBJ / FBX / IFC
-- ============================================================

CREATE TABLE IF NOT EXISTS public.sdms_model3d
(
    id                  varchar(64)    NOT NULL,
    name                varchar(255)   ,
    directory_id        varchar(64)    ,
    model_type          varchar(32)    ,
    src_format          varchar(32)    ,
    file_name           varchar(500)   ,
    total_size          int8           ,
    part_count          int4           ,
    srs                 varchar(64)    ,
    origin_x            numeric(20, 8) ,
    origin_y            numeric(20, 8) ,
    origin_z            numeric(20, 8) ,
    bbox                text           ,
    transformation      text           ,
    tile_count          int8           ,
    lod_depth           int4           ,
    geometric_error     numeric(20, 6) ,
    src_object_key      varchar(500)   ,
    publish_object_key  varchar(500)   ,
    progress            int4           DEFAULT 0,
    status              varchar(16)    ,
    fail_reason         varchar(2000)  ,
    task_id             varchar(64)    ,
    convert_owner       varchar(128)   ,
    heartbeat_time      timestamp      ,
    create_user         varchar(64)    ,
    create_time         timestamp      ,
    update_user         varchar(64)    ,
    update_time         timestamp      ,
    CONSTRAINT sdms_model3d_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE  public.sdms_model3d                IS '三维数据管理（3D Tiles / OSGB / 点云 / 单体 / BIM）';
COMMENT ON COLUMN public.sdms_model3d.id                 IS '主键 UUID';
COMMENT ON COLUMN public.sdms_model3d.name               IS '数据名称';
COMMENT ON COLUMN public.sdms_model3d.directory_id       IS '目录 id（sdms_directorys）';
COMMENT ON COLUMN public.sdms_model3d.model_type         IS '数据类型 TILESET/OSGB/POINTCLOUD/SINGLE/BIM';
COMMENT ON COLUMN public.sdms_model3d.src_format         IS '源格式 TILESET/OSGB/LAS/LAZ/OBJ/FBX/IFC';
COMMENT ON COLUMN public.sdms_model3d.file_name          IS '上传源文件名（zip 或单文件）';
COMMENT ON COLUMN public.sdms_model3d.total_size         IS '源文件总字节数';
COMMENT ON COLUMN public.sdms_model3d.part_count         IS '分片总数';
COMMENT ON COLUMN public.sdms_model3d.srs                IS '空间参考（如 EPSG:4326 / CGCS2000，来自上传登记或 OSGB metadata.xml）';
COMMENT ON COLUMN public.sdms_model3d.origin_x           IS 'OSGB 局部原点 X（SRSOrigin）';
COMMENT ON COLUMN public.sdms_model3d.origin_y           IS 'OSGB 局部原点 Y（SRSOrigin）';
COMMENT ON COLUMN public.sdms_model3d.origin_z           IS 'OSGB 局部原点 Z（SRSOrigin）';
COMMENT ON COLUMN public.sdms_model3d.bbox               IS '地理包围盒 JSON [xmin,ymin,zmin,xmax,ymax,zmax]（解析自 tileset.json）';
COMMENT ON COLUMN public.sdms_model3d.transformation     IS '3D Tiles root.transform 16 元组 JSON（前端定位依赖）';
COMMENT ON COLUMN public.sdms_model3d.tile_count         IS '瓦片节点总数';
COMMENT ON COLUMN public.sdms_model3d.lod_depth          IS 'LOD 层级深度';
COMMENT ON COLUMN public.sdms_model3d.geometric_error    IS '根节点几何误差';
COMMENT ON COLUMN public.sdms_model3d.src_object_key     IS '原始上传对象键（MinIO，zip 或单文件）';
COMMENT ON COLUMN public.sdms_model3d.publish_object_key IS '发布成果对象键（tileset.json 或单体 glb/gltf 的 MinIO key）';
COMMENT ON COLUMN public.sdms_model3d.progress           IS '处理进度 0-100';
COMMENT ON COLUMN public.sdms_model3d.status             IS '状态 0上传中/1已上传/2已排队/3转换中/4已发布/5失败';
COMMENT ON COLUMN public.sdms_model3d.fail_reason        IS '失败原因';
COMMENT ON COLUMN public.sdms_model3d.task_id            IS '转换任务 id（sdms_tool_his.id）';
COMMENT ON COLUMN public.sdms_model3d.convert_owner      IS '转换执行实例标识（host 或 geodata.convert.instance-id），服务重启后据此回收本实例残留任务';
COMMENT ON COLUMN public.sdms_model3d.heartbeat_time     IS '转换心跳时间（转换期间周期刷新，看门狗据此回收僵尸任务）';
COMMENT ON COLUMN public.sdms_model3d.create_user        IS '创建人';
COMMENT ON COLUMN public.sdms_model3d.create_time        IS '创建时间';
COMMENT ON COLUMN public.sdms_model3d.update_user        IS '更新人';
COMMENT ON COLUMN public.sdms_model3d.update_time        IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_sdms_model3d_directory  ON public.sdms_model3d (directory_id);
CREATE INDEX IF NOT EXISTS idx_sdms_model3d_type       ON public.sdms_model3d (model_type);
CREATE INDEX IF NOT EXISTS idx_sdms_model3d_status     ON public.sdms_model3d (status);
