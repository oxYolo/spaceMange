package com.whulri.geodata.common.dto.serverInterface;

import lombok.Data;

import java.util.List;

/**
 * 切片进度输出
 */
@Data
public class TileProgressOutputDto {
    /**
     * 数据编号（图层ID 或 地图ID）
     */
    private String dataId;

    /**
     * GeoServer 图层/图层组名称（含工作区前缀）
     */
    private String layerName;

    /**
     * 切片状态：IDLE-无任务记录 RUNNING-切片中 DONE-已完成（无活动任务） FAILED-查询失败
     */
    private String status;

    /**
     * 已处理瓦片数
     */
    private Long tilesProcessed;

    /**
     * 总瓦片数
     */
    private Long tilesTotal;

    /**
     * 进度百分比 0-100，无法计算时为 null
     */
    private Integer percent;

    /**
     * GWC 估算的剩余秒数
     */
    private Long remainSeconds;

    /**
     * 由 GWC seed 任务行构建进度对象。
     * 行含义: [tiles processed, total tiles, est. remaining seconds, Task ID, status]
     * status: -1=ABORTED, 0=PENDING, 1=RUNNING, 2=DONE；无活动任务（空列表）即切片完成
     *
     * @param dataId    数据编号
     * @param layerName 图层名（工作区:图层）
     * @param tasks     GWC 任务行列表（来自 GeoServer#getTileTasks）
     * @return 进度对象
     */
    public static TileProgressOutputDto build(String dataId, String layerName, List<long[]> tasks) {
        TileProgressOutputDto vo = new TileProgressOutputDto();
        vo.setDataId(dataId);
        vo.setLayerName(layerName);
        if (tasks == null || tasks.isEmpty()) {
            vo.setStatus("DONE");
            return vo;
        }
        long processed = 0;
        long total = 0;
        long remainSeconds = 0;
        for (long[] row : tasks) {
            processed += row.length > 0 ? Math.max(0, row[0]) : 0;
            total += row.length > 1 ? Math.max(0, row[1]) : 0;
            remainSeconds = Math.max(remainSeconds, row.length > 2 ? Math.max(0, row[2]) : 0);
        }
        vo.setTilesProcessed(processed);
        vo.setTilesTotal(total);
        vo.setRemainSeconds(remainSeconds);
        if (total > 0) {
            vo.setPercent((int) Math.min(100, processed * 100 / total));
        }
        vo.setStatus(total > processed ? "RUNNING" : "DONE");
        return vo;
    }
}
