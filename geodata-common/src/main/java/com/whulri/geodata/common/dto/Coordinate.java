package com.whulri.geodata.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Coordinate implements Serializable {

	private float lon;
	private float lat;

}
