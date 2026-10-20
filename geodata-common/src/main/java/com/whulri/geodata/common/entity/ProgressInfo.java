package com.whulri.geodata.common.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author infoearth
 * @createTime 2022/9/23
 * @description
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProgressInfo implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * 当前步数
	 */
	private Integer currentStep;

	/**
	 * 当前进度
	 */
	private Integer currentProgress;

	/**
	 * 当前工作任务
	 */
	private String currentWork;

	/**
	 * 当前计算的用户ID
	 */
	private String currentUserId;

	/**
	 * 是否异常
	 */
	private Boolean isError;

	/**
	 * 是否完成
	 */
	private Boolean isFinish;

	/**
	 * 计算结果
	 */
	private String result;

	/**
	 * 业务主键（三维场景为 model3dId，供前端行级定位，替代整列表刷新）
	 */
	private String bizId;

	/**
	 * 业务状态机码（三维：2=QUEUED 3=CONVERTING 4=PUBLISHED 5=FAILED，对齐 Model3dStatus）
	 */
	private Integer status;

	/**
	 * 失败原因（status=5 时携带，替代前端二次查询）
	 */
	private String failReason;

	/**
	 * 发布对象键（status=4 时携带，前端可直接启用预览；与 result 双写保兼容）
	 */
	private String publishKey;

	public ProgressInfo(Integer currentStep, Integer currentProgress, String currentWork, String currentUserId) {
		this.currentStep = currentStep;
		this.currentProgress = currentProgress;
		this.currentWork = currentWork;
		this.currentUserId = currentUserId;
	}

}
