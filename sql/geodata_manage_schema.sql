-- ============================================================
-- 空间数据管理库 geodata_manage 建库脚本（逆向生成）
-- 来源：dmsp-common entity 20 个实体 + dmsp-web/sql/update.sql 类型基准
-- 目标：PostgreSQL 12+ / PostGIS 3.x
-- 说明：行政区划不再由本库管理（base_area 表及实体已移除，
--       前端统一调用 whulri 基座 whulri-system 的 /region 接口）
-- ============================================================

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;

-- ============================================================
-- sdms_datainformation  数据类型(图层，模型，文档)  (from DataInformationEntity.java)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.sdms_datainformation
(
    id                           varchar(255)       NOT NULL,
    "datatype"                   varchar(255)      ,
    dataid                       varchar(255)      ,
    dataname                     varchar(255)      ,
    dataformat                   varchar(255)      ,
    datatypeid                   varchar(255)      ,
    tag                          varchar(255)      ,
    createuser                   varchar(255)      ,
    "attribute"                  text              ,
    codesort                     int4              ,
    createdt                     timestamp         ,
    "public"                     varchar(255)      ,
    othertype                    varchar(255)      ,
    layertype                    varchar(255)      ,
    picture                      varchar(255)      ,
    filesize                     int8              ,
    gcjz                         varchar(255)      ,
    ssxm                         varchar(255)      ,
    filenames                    text              ,
    "location"                   text              ,
    resourceclass                varchar(255)      ,
    "year"                       varchar(255)      ,
    institution                  varchar(255)      ,
    city                         varchar(255)      ,
    cityname                     varchar(255),
    CONSTRAINT sdms_datainformation_pkey PRIMARY KEY (id)
);

COMMENT ON COLUMN public.sdms_datainformation.dataid IS '各种数据的id';
COMMENT ON COLUMN public.sdms_datainformation.dataname IS '数据名称';
COMMENT ON COLUMN public.sdms_datainformation.datatypeid IS '资源目录的id';
COMMENT ON COLUMN public.sdms_datainformation.tag IS '标签';
COMMENT ON COLUMN public.sdms_datainformation.createuser IS '创建者';
COMMENT ON COLUMN public.sdms_datainformation."attribute" IS '实体类';
COMMENT ON COLUMN public.sdms_datainformation.codesort IS '创建者';
COMMENT ON COLUMN public.sdms_datainformation."public" IS '创建者';
COMMENT ON COLUMN public.sdms_datainformation.othertype IS '自编分类';
COMMENT ON COLUMN public.sdms_datainformation.layertype IS '上传图层样式（1-矢量图层；2-影像图层）';
COMMENT ON COLUMN public.sdms_datainformation.picture IS '图片请求';
COMMENT ON COLUMN public.sdms_datainformation.filesize IS '文件大小KB';
COMMENT ON COLUMN public.sdms_datainformation.gcjz IS '高程基准';
COMMENT ON COLUMN public.sdms_datainformation.ssxm IS '所属项目';
COMMENT ON COLUMN public.sdms_datainformation.filenames IS '文件名称';
COMMENT ON COLUMN public.sdms_datainformation."location" IS '位置信息';
COMMENT ON COLUMN public.sdms_datainformation."year" IS '年份';
COMMENT ON COLUMN public.sdms_datainformation.city IS '市';
COMMENT ON COLUMN public.sdms_datainformation.cityname IS '市名称';

-- ============================================================
-- sdms_datastyle  样式编号  (from DataStyleEntity.java)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.sdms_datastyle
(
    id                           varchar(255)      ,
    stylename                    varchar(255)      ,
    styletype                    varchar(255)      ,
    stylecontent                 text               ,
    styledesc                    varchar(255)      ,
    createdt                     timestamp         ,
    createby                     varchar(255)      ,
    styledatatype                varchar(255)      ,
    styleconfigtype              varchar(255)      ,
    styledefaultlayer            varchar(255)      ,
    stylerenderfield             varchar(255)      ,
    stylerenderfieldname         varchar(255)      ,
    stylerendercolorband         varchar(255)      ,
    stylerenderrule              text               ,
    symbol                       varchar(255),
    CONSTRAINT sdms_datastyle_pkey PRIMARY KEY (id)
);

COMMENT ON COLUMN public.sdms_datastyle.id IS '样式编号';
COMMENT ON COLUMN public.sdms_datastyle.stylename IS '样式名称';
COMMENT ON COLUMN public.sdms_datastyle.styletype IS '样式分类';
COMMENT ON COLUMN public.sdms_datastyle.stylecontent IS '样式内容';
COMMENT ON COLUMN public.sdms_datastyle.styledesc IS '样式描述';
COMMENT ON COLUMN public.sdms_datastyle.createdt IS '创建时间';
COMMENT ON COLUMN public.sdms_datastyle.createby IS '创建人';
COMMENT ON COLUMN public.sdms_datastyle.styledatatype IS '样式类型';
COMMENT ON COLUMN public.sdms_datastyle.styleconfigtype IS '样式配置类型';
COMMENT ON COLUMN public.sdms_datastyle.styledefaultlayer IS '样式默认图层';
COMMENT ON COLUMN public.sdms_datastyle.stylerenderfield IS '样式渲染字段';
COMMENT ON COLUMN public.sdms_datastyle.stylerenderfieldname IS '渲染字段名称';
COMMENT ON COLUMN public.sdms_datastyle.stylerendercolorband IS '样式渲染颜色带';
COMMENT ON COLUMN public.sdms_datastyle.stylerenderrule IS '样式渲染预设规则';
COMMENT ON COLUMN public.sdms_datastyle.symbol IS '符号';

-- ============================================================
-- sdms_datatag  数据标签ID  (from DataTagEntity.java)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.sdms_datatag
(
    id                           varchar(255)       NOT NULL,
    tagname                      varchar(255)      ,
    tagdesc                      varchar(255)      ,
    dictcodeid                   varchar(255)      ,
    createuser                   varchar(255)      ,
    "type"                       varchar(255),
    CONSTRAINT sdms_datatag_pkey PRIMARY KEY (id)
);

COMMENT ON COLUMN public.sdms_datatag.id IS '数据标签ID';
COMMENT ON COLUMN public.sdms_datatag.tagname IS '标签名称';
COMMENT ON COLUMN public.sdms_datatag.tagdesc IS '标签备注';
COMMENT ON COLUMN public.sdms_datatag.dictcodeid IS '标签代码类型';
COMMENT ON COLUMN public.sdms_datatag.createuser IS '创建人';
COMMENT ON COLUMN public.sdms_datatag."type" IS '类型';

-- ============================================================
-- sdms_datatype  数据类别ID  (from DataTypeEntity.java)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.sdms_datatype
(
    id                           varchar(255)       NOT NULL,
    typename                     varchar(255)      ,
    typedesc                     varchar(255)      ,
    dictcodeid                   varchar(255)      ,
    parentid                     varchar(255)      ,
    createuser                   varchar(255)      ,
    "type"                       varchar(255)      ,
    codesort                     int4,
    CONSTRAINT sdms_datatype_pkey PRIMARY KEY (id)
);

COMMENT ON COLUMN public.sdms_datatype.id IS '数据类别ID';
COMMENT ON COLUMN public.sdms_datatype.typename IS '类别名称';
COMMENT ON COLUMN public.sdms_datatype.typedesc IS '类别描述';
COMMENT ON COLUMN public.sdms_datatype.dictcodeid IS '字典代码';
COMMENT ON COLUMN public.sdms_datatype.parentid IS '父数据类别ID';
COMMENT ON COLUMN public.sdms_datatype.createuser IS '创建人';
COMMENT ON COLUMN public.sdms_datatype."type" IS '类型';
COMMENT ON COLUMN public.sdms_datatype.codesort IS '排序';

-- ============================================================
-- sdms_dictdatatype  数据类型ID  (from DicDataTypeEntity.java)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.sdms_dictdatatype
(
    id                           varchar(255)       NOT NULL,
    typename                     varchar(255)      ,
    typedesc                     varchar(255)      ,
    typecode                     varchar(255)      ,
    typesort                     int4              ,
    parentid                     varchar(255)      ,
    keywords                     varchar(255),
    CONSTRAINT sdms_dictdatatype_pkey PRIMARY KEY (id)
);

COMMENT ON COLUMN public.sdms_dictdatatype.id IS '数据类型ID';
COMMENT ON COLUMN public.sdms_dictdatatype.typename IS '类型名称';
COMMENT ON COLUMN public.sdms_dictdatatype.typedesc IS '类型描述';
COMMENT ON COLUMN public.sdms_dictdatatype.typecode IS '类型编码';
COMMENT ON COLUMN public.sdms_dictdatatype.typesort IS '类型排序';
COMMENT ON COLUMN public.sdms_dictdatatype.parentid IS '父数据类型ID';
COMMENT ON COLUMN public.sdms_dictdatatype.keywords IS '关键字';

-- ============================================================
-- sdms_dictdatacode  代码ID  (from DictDataCodeEntity.java)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.sdms_dictdatacode
(
    id                           varchar(255)       NOT NULL,
    codename                     varchar(255)      ,
    codevalue                    varchar(255)      ,
    codedesc                     varchar(255)      ,
    datatypeid                   varchar(255)      ,
    codesort                     int4              ,
    remark                       varchar(255)      ,
    keywords                     varchar(255),
    CONSTRAINT sdms_dictdatacode_pkey PRIMARY KEY (id)
);

COMMENT ON COLUMN public.sdms_dictdatacode.id IS '代码ID';
COMMENT ON COLUMN public.sdms_dictdatacode.codename IS '代码名称';
COMMENT ON COLUMN public.sdms_dictdatacode.codevalue IS '代码值';
COMMENT ON COLUMN public.sdms_dictdatacode.codedesc IS '代码描述';
COMMENT ON COLUMN public.sdms_dictdatacode.datatypeid IS '代码类型';
COMMENT ON COLUMN public.sdms_dictdatacode.codesort IS '代码排序';
COMMENT ON COLUMN public.sdms_dictdatacode.remark IS '备注';
COMMENT ON COLUMN public.sdms_dictdatacode.keywords IS '关键字';

-- ============================================================
-- sdms_directorys  sdms_directorys  (from DirectorysEntity.java)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.sdms_directorys
(
    id                           varchar(255)       NOT NULL,
    "datatype"                   varchar(255)      ,
    dataid                       varchar(255)      ,
    dataname                     varchar(255)      ,
    dataformat                   varchar(255)      ,
    datatypeid                   varchar(255)      ,
    tag                          varchar(255)      ,
    createuser                   varchar(255)      ,
    "attribute"                  varchar(255)      ,
    codesort                     int4              ,
    createdt                     timestamp         ,
    "public"                     varchar(255)      ,
    othertype                    varchar(255)      ,
    layertype                    varchar(255)      ,
    picture                      varchar(255)      ,
    filesize                     int8,
    CONSTRAINT sdms_directorys_pkey PRIMARY KEY (id)
);


-- ============================================================
-- sdms_fileinfo  guid  (from FileInfoEntity.java)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.sdms_fileinfo
(
    guid                         varchar(255)       NOT NULL,
    filename                     varchar(255)      ,
    filetype                     varchar(255)      ,
    fileext                      varchar(255)      ,
    filesize                     varchar(255)      ,
    datasrcource                 varchar(255)      ,
    providername                 varchar(255)      ,
    storagetype                  varchar(255)      ,
    filepath                     varchar(255)      ,
    createdate                   timestamp         ,
    creatorid                    varchar(255)      ,
    creator                      varchar(255)      ,
    catid                        varchar(255)      ,
    catname                      varchar(255)      ,
    relationid                   varchar(255)      ,
    dataname                     varchar(255)      ,
    dataexplain                  varchar(255)      ,
    datasource                   varchar(255)      ,
    dataconfidential             varchar(255)      ,
    serviceobject                varchar(255)      ,
    keywords                     varchar(255)      ,
    datadesc                     varchar(255)      ,
    datatypes                    varchar(255),
    CONSTRAINT sdms_fileinfo_pkey PRIMARY KEY (guid)
);

COMMENT ON COLUMN public.sdms_fileinfo.guid IS 'guid';
COMMENT ON COLUMN public.sdms_fileinfo.filename IS '文件名称';
COMMENT ON COLUMN public.sdms_fileinfo.filetype IS '文件类型';
COMMENT ON COLUMN public.sdms_fileinfo.fileext IS '文件后缀名';
COMMENT ON COLUMN public.sdms_fileinfo.filesize IS '文件大小';
COMMENT ON COLUMN public.sdms_fileinfo.datasrcource IS '数据来源';
COMMENT ON COLUMN public.sdms_fileinfo.providername IS '供应者名字';
COMMENT ON COLUMN public.sdms_fileinfo.storagetype IS '储存类型';
COMMENT ON COLUMN public.sdms_fileinfo.filepath IS '文件路径';
COMMENT ON COLUMN public.sdms_fileinfo.createdate IS '创建时间';
COMMENT ON COLUMN public.sdms_fileinfo.creatorid IS '创建ID';
COMMENT ON COLUMN public.sdms_fileinfo.creator IS '创建者';
COMMENT ON COLUMN public.sdms_fileinfo.catid IS '目录id';
COMMENT ON COLUMN public.sdms_fileinfo.catname IS '目录名字';
COMMENT ON COLUMN public.sdms_fileinfo.relationid IS '关联ID';
COMMENT ON COLUMN public.sdms_fileinfo.dataname IS '数据名称';
COMMENT ON COLUMN public.sdms_fileinfo.dataexplain IS '数据说明';
COMMENT ON COLUMN public.sdms_fileinfo.datasource IS '数据来源';
COMMENT ON COLUMN public.sdms_fileinfo.dataconfidential IS '数据机密';
COMMENT ON COLUMN public.sdms_fileinfo.serviceobject IS '服务对象';
COMMENT ON COLUMN public.sdms_fileinfo.keywords IS '关键词';
COMMENT ON COLUMN public.sdms_fileinfo.datadesc IS '数据描述';
COMMENT ON COLUMN public.sdms_fileinfo.datatypes IS '数据类型';

-- ============================================================
-- sdms_layer  sdms_layer  (from LayerContentEntity.java)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.sdms_layer
(
    id                           varchar(255)       NOT NULL,
    layername                    varchar(255)      ,
    "datatype"                   varchar(255)      ,
    layerbbox                    varchar(255)      ,
    layertype                    varchar(255)      ,
    layertag                     varchar(255)      ,
    layerdesc                    varchar(255)      ,
    layerattrtable               varchar(100)      ,
    layerspatialtable            varchar(100)      ,
    layerrefence                 varchar(255)      ,
    originlayerrefence           varchar(255)      ,
    createdt                     timestamp         ,
    maxy                         numeric(18, 6)    ,
    miny                         numeric(18, 6)    ,
    minx                         numeric(18, 6)    ,
    maxx                         numeric(18, 6)    ,
    uploadstatus                 varchar(255)      ,
    createby                     varchar(255)      ,
    layerdefaultstyle            varchar(255)      ,
    uploadfiletype               varchar(255)      ,
    uploadfilename               varchar(255)      ,
    filesize                     int4              ,
    isshow                       int4              ,
    businessid                   varchar(255)      ,
    publishStatus                varchar(255)      ,
    publishtype                  varchar(255)      ,
    layersort                    int4              ,
    fileencoding                 varchar(255)      ,
    rastercount                  int4              ,
    rasterdatatype               varchar(255)      ,
    tifvariety                   int4              ,
    originmaxx                   numeric(18, 6)    ,
    originminx                   numeric(18, 6)    ,
    originmaxy                   numeric(18, 6)    ,
    originminy                   numeric(18, 6),
    CONSTRAINT sdms_layer_pkey PRIMARY KEY (id)
);


-- ============================================================
-- sdms_layerfield  sdms_layerfield  (from LayerFieldEntity.java)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.sdms_layerfield
(
    id                           varchar(255)       NOT NULL,
    layerid                      varchar(255)      ,
    attributename                varchar(255)      ,
    attributedesc                varchar(255)      ,
    attributetype                varchar(255)      ,
    attributelength              varchar(255)      ,
    attributeprecision           varchar(255)      ,
    attributeinputctrl           varchar(255)      ,
    attributeinputmax            varchar(255)      ,
    attributeinputmin            varchar(255)      ,
    attributedefault             varchar(255)      ,
    attributeisnull              varchar(255)      ,
    attributeinputformat         varchar(255)      ,
    remark                       varchar(255)      ,
    createdt                     timestamp         ,
    attributeunit                varchar(255)      ,
    attributedatatype            varchar(255)      ,
    attributevaluelink           varchar(255)      ,
    attributedatasource          varchar(255)      ,
    attributecalcomp             varchar(255)      ,
    attributesort                int4              ,
    newfields                    varchar(255)      ,
    chinesename                  varchar(255),
    CONSTRAINT sdms_layerfield_pkey PRIMARY KEY (id)
);


-- ============================================================
-- sdms_layer_log  sdms_layer_log  (from LayerLogEntity.java)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.sdms_layer_log
(
    id                           varchar(255)       NOT NULL,
    layername                    varchar(255)      ,
    layerid                      varchar(255)      ,
    uploadfiletype               varchar(255)      ,
    createdt                     timestamp         ,
    createby                     varchar(255)      ,
    "status"                     varchar(255)      ,
    reason                       varchar(255),
    CONSTRAINT sdms_layer_log_pkey PRIMARY KEY (id)
);


-- ============================================================
-- sdms_map  地图编号  (from MapEntity.java)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.sdms_map
(
    id                           varchar(255)       NOT NULL,
    mapname                      varchar(255)      ,
    mapenname                    varchar(255)      ,
    mapbbox                      varchar(255)      ,
    mappublishaddress            varchar(255)      ,
    mapstatus                    varchar(255)      ,
    mapdesc                      varchar(255)      ,
    maptype                      varchar(255)      ,
    maptag                       varchar(255)      ,
    publishdt                    timestamp         ,
    sortcode                     int4              ,
    enabledmark                  int4              ,
    deletemark                   int4              ,
    createuserid                 varchar(255)      ,
    createusername               varchar(255)      ,
    createdt                     timestamp         ,
    modifyuserid                 varchar(255)      ,
    modifyusername               varchar(255)      ,
    modifydate                   timestamp         ,
    mapscale                     varchar(255)      ,
    spatialrefence               varchar(255)      ,
    maxy                         numeric(18, 6)    ,
    miny                         numeric(18, 6)    ,
    minx                         numeric(18, 6)    ,
    maxx                         numeric(18, 6)    ,
    maplegend                    varchar(255)      ,
    datasource                   varchar(255)      ,
    dataconfidential             varchar(255)      ,
    serviceobject                varchar(255)      ,
    keywords                     varchar(255)      ,
    datadesc                     varchar(255)      ,
    datatypes                    varchar(255)      ,
    filesize                     int4              ,
    dataaccuracy                 varchar(255),
    CONSTRAINT sdms_map_pkey PRIMARY KEY (id)
);

COMMENT ON COLUMN public.sdms_map.id IS '地图编号';
COMMENT ON COLUMN public.sdms_map.mapname IS '地图名称';
COMMENT ON COLUMN public.sdms_map.mapenname IS '地图英文名称';
COMMENT ON COLUMN public.sdms_map.mapbbox IS '边界范围';
COMMENT ON COLUMN public.sdms_map.mappublishaddress IS '地图发布地址';
COMMENT ON COLUMN public.sdms_map.mapstatus IS '地图状态';
COMMENT ON COLUMN public.sdms_map.mapdesc IS '地图描述';
COMMENT ON COLUMN public.sdms_map.maptype IS '地图分类';
COMMENT ON COLUMN public.sdms_map.maptag IS '地图标签';
COMMENT ON COLUMN public.sdms_map.publishdt IS '发布时间';
COMMENT ON COLUMN public.sdms_map.sortcode IS '排序编号';
COMMENT ON COLUMN public.sdms_map.enabledmark IS '是否可用';
COMMENT ON COLUMN public.sdms_map.deletemark IS '是否删除';
COMMENT ON COLUMN public.sdms_map.createuserid IS '创建人编号';
COMMENT ON COLUMN public.sdms_map.createusername IS '创建人名称';
COMMENT ON COLUMN public.sdms_map.createdt IS '创建日期';
COMMENT ON COLUMN public.sdms_map.modifyuserid IS '修改人编号';
COMMENT ON COLUMN public.sdms_map.modifyusername IS '修改人名称';
COMMENT ON COLUMN public.sdms_map.modifydate IS '修改日期';
COMMENT ON COLUMN public.sdms_map.mapscale IS '比例尺';
COMMENT ON COLUMN public.sdms_map.spatialrefence IS '空间参考';
COMMENT ON COLUMN public.sdms_map.maplegend IS '地图图例';
COMMENT ON COLUMN public.sdms_map.datasource IS '数据来源';
COMMENT ON COLUMN public.sdms_map.dataconfidential IS '数据机密';
COMMENT ON COLUMN public.sdms_map.serviceobject IS '服务对象';
COMMENT ON COLUMN public.sdms_map.keywords IS '关键词';
COMMENT ON COLUMN public.sdms_map.datadesc IS '数据描述';
COMMENT ON COLUMN public.sdms_map.datatypes IS '数据类型';
COMMENT ON COLUMN public.sdms_map.filesize IS '文件大小';
COMMENT ON COLUMN public.sdms_map.dataaccuracy IS '数据精度';

-- ============================================================
-- sdms_map_releation  地图关联ID  (from MapRelationEntity.java)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.sdms_map_releation
(
    id                           varchar(255)       NOT NULL,
    mapid                        varchar(255)      ,
    dataconfigid                 varchar(255)      ,
    datastyleid                  varchar(255)      ,
    datasort                     int4              ,
    configdt                     timestamp         ,
    modifydt                     timestamp         ,
    minscaledenominator          varchar(255)      ,
    maxscaledenominator          varchar(255)      ,
    stylename                    varchar(255),
    CONSTRAINT sdms_map_releation_pkey PRIMARY KEY (id)
);

COMMENT ON COLUMN public.sdms_map_releation.id IS '地图关联ID';
COMMENT ON COLUMN public.sdms_map_releation.mapid IS '地图ID';
COMMENT ON COLUMN public.sdms_map_releation.dataconfigid IS '图层目录ID';
COMMENT ON COLUMN public.sdms_map_releation.datastyleid IS '图层样式ID';
COMMENT ON COLUMN public.sdms_map_releation.datasort IS '图层排序';
COMMENT ON COLUMN public.sdms_map_releation.configdt IS '配置日期';
COMMENT ON COLUMN public.sdms_map_releation.modifydt IS '修改日期';
COMMENT ON COLUMN public.sdms_map_releation.minscaledenominator IS '最小比例尺';
COMMENT ON COLUMN public.sdms_map_releation.maxscaledenominator IS '最大比例尺';
COMMENT ON COLUMN public.sdms_map_releation.stylename IS '名称';

-- ============================================================
-- sdms_metadata  sdms_metadata  (from MetadataEntity.java)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.sdms_metadata
(
    "Id"                         varchar(255)       NOT NULL,
    dataid                       varchar(255)      ,
    "Version"                    varchar(255)      ,
    "Summary"                    varchar(255)      ,
    "Target"                     varchar(255)      ,
    "MaintenanceFre"             varchar(255)      ,
    "AdministrativeDivisions"    varchar(255)      ,
    "NomalLimit"                 varchar(255)      ,
    "OtherLimit"                 varchar(255)      ,
    "SpatialGeographical"        varchar(255)      ,
    "StartDT"                    timestamp         ,
    "EndDT"                      timestamp         ,
    "AdditionalInfo"             varchar(255)      ,
    "PublishDT"                  timestamp         ,
    "ModifyDT"                   timestamp         ,
    "MetaDataQualityDesc"        varchar(255)      ,
    "ThumbnalAddress"            varchar(255)      ,
    "MetaDataType"               varchar(255)      ,
    "MetaDataTag"                varchar(255)      ,
    "CreateBy"                   varchar(255)      ,
    "Owner"                      varchar(255)      ,
    "IsPublish"                  int4              ,
    "CreateDT"                   timestamp         ,
    "datatype"                   varchar(255)      ,
    metadataname                 varchar(255)      ,
    spatialreferencesystem       varchar(255)      ,
    datayear                     varchar(255)      ,
    dataunit                     varchar(255)      ,
    datacontentdec               varchar(255)      ,
    east                         numeric(18, 6)    ,
    west                         numeric(18, 6)    ,
    south                        numeric(18, 6)    ,
    north                        numeric(18, 6)    ,
    minpixel                     numeric(18, 6)    ,
    maxpixel                     numeric(18, 6)    ,
    spatialresolution            varchar(255)      ,
    nodata                       numeric(18, 6)    ,
    minvertical                  numeric(18, 6)    ,
    maxvertical                  numeric(18, 6)    ,
    raster_count                 int4,
    CONSTRAINT sdms_metadata_pkey PRIMARY KEY ("Id")
);


-- ============================================================
-- sdms_operatelog  sdms_operatelog  (from OperateLogEntity.java)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.sdms_operatelog
(
    "Id"                         varchar(255)       NOT NULL,
    "UserCode"                   varchar(255)      ,
    "SystemFunc"                 varchar(255)      ,
    "OperateType"                varchar(255)      ,
    "Result"                     varchar(255)      ,
    "ResultDescribe"             varchar(255)      ,
    "LayerID"                    varchar(255)      ,
    "UserName"                   varchar(255)      ,
    "OperateTime"                timestamp,
    CONSTRAINT sdms_operatelog_pkey PRIMARY KEY ("Id")
);


-- ============================================================
-- sdms_symbol  sdms_symbol  (from SymbolEntity.java)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.sdms_symbol
(
    id                           varchar(255)       NOT NULL,
    symbolname                   varchar(255)      ,
    symboltype                   varchar(255)      ,
    symbolclassify               varchar(255)      ,
    symbolcdesc                  varchar(255)      ,
    imageorfonttype              varchar(255)      ,
    path                         varchar(255)      ,
    imagelong                    varchar(255)      ,
    imagewide                    varchar(255)      ,
    imageangle                   varchar(255)      ,
    fontsize                     varchar(255)      ,
    fontcolor                    varchar(255)      ,
    fontangle                    varchar(255)      ,
    createby                     varchar(255)      ,
    createdt                     timestamp         ,
    fontfamily                   varchar(255)      ,
    charunicode                  varchar(255),
    CONSTRAINT sdms_symbol_pkey PRIMARY KEY (id)
);


-- ============================================================
-- sdms_user  sdms_user  (from SystemUserEntity.java)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.sdms_user
(
    id                           varchar(255)       NOT NULL,
    user_name                    varchar(255)      ,
    user_code                    varchar(255)      ,
    user_sex                     varchar(255)      ,
    password                     varchar(255)      ,
    tel_phone                    varchar(255)      ,
    phone                        varchar(255)      ,
    department                   varchar(255)      ,
    "position"                   varchar(255)      ,
    create_dt                    timestamp         ,
    remark                       varchar(255),
    CONSTRAINT sdms_user_pkey PRIMARY KEY (id)
);


-- ============================================================
-- sdms_tag_releation  数据标签id  (from TagRelationEntity.java)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.sdms_tag_releation
(
    id                           varchar(255)       NOT NULL,
    datatagid                    varchar(255)      ,
    mapid                        varchar(255),
    CONSTRAINT sdms_tag_releation_pkey PRIMARY KEY (id)
);

COMMENT ON COLUMN public.sdms_tag_releation.datatagid IS '数据标签id';
COMMENT ON COLUMN public.sdms_tag_releation.mapid IS '地图或图层id';

-- ============================================================
-- sdms_tool_his  sdms_tool_his  (from ToolHisEntity.java)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.sdms_tool_his
(
    id                           varchar(255)       NOT NULL,
    layerId                      varchar(255)      ,
    layername                    varchar(255)      ,
    layerattrtable               varchar(255)      ,
    createdt                     timestamp         ,
    createby                     varchar(255)      ,
    "status"                     varchar(255)      ,
    origin                       varchar(255)      ,
    storage                      varchar(255)      ,
    uploadfiletype               varchar(255)      ,
    detailjson                   text,
    CONSTRAINT sdms_tool_his_pkey PRIMARY KEY (id)
);


-- ============================================================
-- 常用业务索引
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_sdms_layer_layername ON public.sdms_layer (layername);
CREATE INDEX IF NOT EXISTS idx_sdms_layer_datatype ON public.sdms_layer (datatype);
CREATE INDEX IF NOT EXISTS idx_sdms_layer_publishstatus ON public.sdms_layer (publishstatus);
CREATE INDEX IF NOT EXISTS idx_sdms_metadata_dataid ON public.sdms_metadata (dataid);
CREATE INDEX IF NOT EXISTS idx_sdms_map_releation_mapid ON public.sdms_map_releation (mapid);
CREATE INDEX IF NOT EXISTS idx_sdms_map_releation_dataconfigid ON public.sdms_map_releation (dataconfigid);
CREATE INDEX IF NOT EXISTS idx_sdms_tag_releation_mapid ON public.sdms_tag_releation (mapid);
CREATE INDEX IF NOT EXISTS idx_sdms_layerfield_layerid ON public.sdms_layerfield (layerid);
CREATE INDEX IF NOT EXISTS idx_sdms_tool_his_layerid ON public.sdms_tool_his (layerid);
