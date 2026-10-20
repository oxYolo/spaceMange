package com.whulri.geodata.common.util.style;

import jakarta.xml.bind.annotation.*;
import java.util.List;

@XmlRootElement(name = "StyledLayerDescriptor")
@XmlAccessorType(XmlAccessType.FIELD)
public class StyledLayerDescriptorTif {
	@XmlAttribute(name = "version")
	private String version = "1.0.0";

	@XmlElement(name = "NamedLayer")
	private List<NamedLayer> namedLayers;

	// Getters and Setters
	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public List<NamedLayer> getNamedLayers() {
		return namedLayers;
	}

	public void setNamedLayers(List<NamedLayer> namedLayers) {
		this.namedLayers = namedLayers;
	}
}
