-- ============================================================
-- GeoDataManage 补丁脚本（2026-10-14）
-- 内容：
--   一、补齐 3 张运行时表（代码在用但建库脚本缺失）：
--       sdms_layer_readlog / sdms_layerfielddict / sdms_attributeparameter
--   二、最小字典种子数据（代码硬编码引用的字典组与字典项）：
--       - sdms_dictdatatype：6 个字典组
--       - sdms_dictdatacode：几何类型(点/线/面/影像) / SHP 家族文件 /
--         属性字段类型(6 种) / 数据密级(公开)
--       - sdms_user：System（超管）+ admin（前端默认操作人）
--
-- 说明：
--   * 带「代码硬编码引用」注释的 id 被后端 Java 常量精确匹配，切勿修改；
--   * 无注释的 id 为补丁自建（原系统数据不可得），可在业务侧自行调整；
--   * 本脚本幂等（IF NOT EXISTS / ON CONFLICT DO NOTHING），可重复执行；
--   * 完整业务字典（空间参考、目录分类等）需从原系统导出后另行导入。
-- 目标：PostgreSQL 12+ / PostGIS 3.x
-- ============================================================

-- ============================================================
-- 一、缺失表 DDL
-- ============================================================

-- 1.1 矢量导入解析日志（待解析队列 + 状态回写）
--     代码引用：ExcuteShpJob:246,501,534；LayerContentServiceImpl:1500
CREATE TABLE IF NOT EXISTS public.sdms_layer_readlog
(
    id          varchar(64)  NOT NULL,
    layerid     varchar(64),
    shpfilename varchar(500),
    foldername  varchar(500),
    readstatus  varchar(8),
    readstartdt timestamp,
    readenddt   timestamp,
    message     varchar(2000),
    msgstatus   varchar(8),
    createdt    timestamp,
    createby    varchar(64),
    CONSTRAINT sdms_layer_readlog_pkey PRIMARY KEY (id)
);
COMMENT ON TABLE  public.sdms_layer_readlog IS '矢量文件解析日志（readstatus: 0 待解析 / 1 成功 / 2 失败 / 3 解析中）';
COMMENT ON COLUMN public.sdms_layer_readlog.layerid IS '图层 id（sdms_layer.id）';

-- 1.2 图层属性字典（代码当前为全表读取，列为按原系统结构推断的最小集）
--     代码引用：ExcuteShpJob:512
CREATE TABLE IF NOT EXISTS public.sdms_layerfielddict
(
    id                 varchar(64)  NOT NULL,
    attributename      varchar(255),
    attributetype      varchar(255),
    attributedesc      varchar(255),
    attributelength    varchar(64),
    attributeprecision varchar(64),
    datatypeid         varchar(64),
    creatdt            timestamp,
    remark             varchar(255),
    CONSTRAINT sdms_layerfielddict_pkey PRIMARY KEY (id)
);
COMMENT ON TABLE public.sdms_layerfielddict IS '图层属性字段字典（旧系统迁移表，列结构按原实现推断）';

-- 1.3 旧系统属性参数兜底表（图层字段为空时按 datainfoid 回查）
--     代码引用：LayerContentServiceImpl:3488
CREATE TABLE IF NOT EXISTS public.sdms_attributeparameter
(
    id               varchar(64)  NOT NULL,
    datainfoid       varchar(64),
    tablename        varchar(128),
    attributename    varchar(255),
    attributetypes   varchar(128),
    attributelength  varchar(64),
    attributedecimal varchar(64),
    attributesort    int4,
    createdt         timestamp,
    CONSTRAINT sdms_attributeparameter_pkey PRIMARY KEY (id)
);
COMMENT ON TABLE public.sdms_attributeparameter IS '属性参数兜底表（datainfoid=图层 id，tablename=Business 等业务表标识）';

-- ============================================================
-- 二、字典种子数据（最小可用集）
-- ============================================================

-- 2.1 字典组（sdms_dictdatatype）
INSERT INTO public.sdms_dictdatatype (id, typename, typedesc, typecode, typesort) VALUES
('1cfe51dd-67a3-11e7-8eb2-005056bb1c7e', '图层几何类型', '点/线/面/影像（sdms_layer.datatype 取值）', 'layerGeomType', 1),
('b9ef9c7c-67b3-11e7-8eb2-005056bb1c7e', '矢量文件构成', 'SHP 家族必要文件扩展名（isFileExist 校验用）', 'shpFileParts', 2),
('73160096-67a5-11e7-8eb2-005056bb1c7e', '属性字段类型', '图层属性字段数据类型', 'fieldType', 3),
('a2faae61-6acd-11e7-87f3-005056bb1c7e', '数据标签分类', '标签归属分类（默认打标 dictCodeId）', 'tagClassify', 4),
('ba5ab799-6acd-11e7-87f3-005056bb1c7e', '数据分类', '资源目录体系（sdms_datatype.dictCodeID 绑定值）', 'data_type', 5),
('4e5d1b30-6b16-4a70-9d1f-1f0a2c9c0d01', '数据密级', '数据保密级别（补丁自建组）', 'dataConfidential', 6)
ON CONFLICT (id) DO NOTHING;

-- 2.2 字典项（sdms_dictdatacode）
INSERT INTO public.sdms_dictdatacode (id, codename, codevalue, datatypeid, codesort, remark) VALUES
-- 几何类型组：id 被 Java 硬编码（ExcuteShpJob 样式/回填、LayerContentServiceImpl 栅格分支、树构建 codename 精确匹配）
('6b6941f1-67a3-11e7-8eb2-005056bb1c7e', '点', 'point', '1cfe51dd-67a3-11e7-8eb2-005056bb1c7e', 1, '代码硬编码引用'),
('7776934c-67a3-11e7-8eb2-005056bb1c7e', '线', 'line', '1cfe51dd-67a3-11e7-8eb2-005056bb1c7e', 2, '代码硬编码引用'),
('a2758dc0-67a3-11e7-8eb2-005056bb1c7e', '面', 'polygon', '1cfe51dd-67a3-11e7-8eb2-005056bb1c7e', 3, '代码硬编码引用'),
('acf11b57-0626-4e49-b385-2e9a4195221c', '影像', 'image', '1cfe51dd-67a3-11e7-8eb2-005056bb1c7e', 4, '代码硬编码引用（栅格）'),
-- SHP 家族文件组：codevalue = 扩展名（isFileExist 按此逐一校验文件存在）
('b1a00001-67b3-11e7-8eb2-005056bb1c7e', '主文件', 'shp', 'b9ef9c7c-67b3-11e7-8eb2-005056bb1c7e', 1, '必要文件'),
('b1a00002-67b3-11e7-8eb2-005056bb1c7e', '索引文件', 'shx', 'b9ef9c7c-67b3-11e7-8eb2-005056bb1c7e', 2, '必要文件'),
('b1a00003-67b3-11e7-8eb2-005056bb1c7e', '属性文件', 'dbf', 'b9ef9c7c-67b3-11e7-8eb2-005056bb1c7e', 3, '必要文件'),
-- 属性字段类型组：codename 与解析类型名（语言包中文键）精确匹配，codevalue 供前端展示
('afd042e0-67a8-11e7-8eb2-005056bb1c7e', '字符型', 'Text', '73160096-67a5-11e7-8eb2-005056bb1c7e', 1, '代码硬编码引用'),
('8ef0aaaa-67a8-11e7-8eb2-005056bb1c7e', '短整型', 'Integer', '73160096-67a5-11e7-8eb2-005056bb1c7e', 2, NULL),
('8ef0bbbb-67a8-11e7-8eb2-005056bb1c7e', '长整型', 'Integer', '73160096-67a5-11e7-8eb2-005056bb1c7e', 3, NULL),
('8ef0cccc-67a8-11e7-8eb2-005056bb1c7e', '单浮点型', 'Double', '73160096-67a5-11e7-8eb2-005056bb1c7e', 4, NULL),
('9ffd11ea-67a8-11e7-8eb2-005056bb1c7e', '双浮点型', 'Double', '73160096-67a5-11e7-8eb2-005056bb1c7e', 5, '代码硬编码引用（推断为双浮点）'),
('8ef0dddd-67a8-11e7-8eb2-005056bb1c7e', '时间型', 'Date', '73160096-67a5-11e7-8eb2-005056bb1c7e', 6, NULL),
-- 数据密级组：47c52dbe 被 MapServiceImpl 硬编码（公开密级数据人人可见）
('47c52dbe-455e-4ab8-812a-b14bddc5ae38', '公开', 'public', '4e5d1b30-6b16-4a70-9d1f-1f0a2c9c0d01', 1, '代码硬编码引用（公开密级）')
ON CONFLICT (id) DO NOTHING;

-- 2.3 用户（System 超管 + admin 默认操作人）
INSERT INTO public.sdms_user (id, user_name, user_code, user_sex, password, department, create_dt, remark) VALUES
('f0000001-0000-4000-8000-000000000001', '超级管理员', 'System', '1', '', '', now(), '超管：常量 ConstHelper.CONST_SYSTEMNAME，数据全量可见'),
('f0000002-0000-4000-8000-000000000002', '管理员', 'admin', '1', '', '', now(), '前端 userStore 默认操作人')
ON CONFLICT (id) DO NOTHING;
