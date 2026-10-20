package com.whulri.geodata.common.dto.dataStyle;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataStyleDeleteDto {

	/// <summary>
	/// 样式编号
	/// </summary>
	public String styleId;
	/// <summary>
	/// 创建人
	/// </summary>
	public String userAccount;
}
