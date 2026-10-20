package com.whulri.geodata.common.vo;

import com.whulri.geodata.common.entity.ProgressInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProcessVO {

	/**
	 * 类型
	 */
	private String moduleType;

	/**
	 * 进度内容
	 */
	private ProgressInfo content;

}
