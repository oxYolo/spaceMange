package com.whulri.geodata.common.dto.operateLog;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TranslateOutput {

	private String Code;

	private String Chinese;

	private String English;
}
