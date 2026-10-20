package com.whulri.geodata.model3d.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whulri.geodata.convert.core.ConvertTask;
import com.whulri.geodata.model3d.dto.Model3dPageQuery;
import com.whulri.geodata.model3d.dto.Model3dUploadInitDTO;
import com.whulri.geodata.model3d.entity.Model3dEntity;
import org.springframework.web.multipart.MultipartFile;

/**
 * 三维数据管理服务
 */
public interface IModel3dService {

    /** 初始化上传，返回三维数据 id */
    String initUpload(Model3dUploadInitDTO dto);

    /** 上传分片（partNo 从 1 开始；非最后分片须 ≥5MiB，受对象存储 compose 限制） */
    void uploadPart(String id, int partNo, MultipartFile file);

    /** 完成上传：合并分片；现成 3D Tiles 立即发布，其余等待转换 */
    Model3dEntity completeUpload(String id);

    /** 已上传分片号列表（升序，从 1 开始），用于断点续传：前端据此跳过已传分片 */
    java.util.List<Integer> listUploadedParts(String id);

    /** 下发转换任务（异步，经 MQ） */
    void requestConvert(String id);

    /**
     * 执行转换（由 MQ 消费者调用）：
     * 下载源数据 → 解压 → 构建命令 → 子进程转换 → 产物上传 → 解析登记 → 状态回写。
     */
    void executeConvert(ConvertTask task);

    Model3dEntity getById(String id);

    Page<Model3dEntity> page(Model3dPageQuery query);

    /** 删除（含对象存储前缀清理） */
    void delete(String id);

    /** 签发成果加载地址（预签名 URL，限已发布状态） */
    String getPublishUrl(String id);
}
