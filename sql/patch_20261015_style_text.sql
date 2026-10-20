-- sdms_datastyle 内容列扩容：SLD 原文 / 渲染规则 JSON 远超 varchar(255)
ALTER TABLE public.sdms_datastyle ALTER COLUMN stylecontent TYPE text;
ALTER TABLE public.sdms_datastyle ALTER COLUMN stylerenderrule TYPE text;
