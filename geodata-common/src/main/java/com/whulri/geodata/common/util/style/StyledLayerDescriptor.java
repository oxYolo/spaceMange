package com.whulri.geodata.common.util.style;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.xml.bind.annotation.*;
import java.util.List;

/**
 * 样式化图层描述符
 */
@XmlRootElement(name = "StyledLayerDescriptor", namespace = SLDNamespaces.SLD)
@XmlAccessorType(XmlAccessType.FIELD)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StyledLayerDescriptor {
    @XmlAttribute
    private String version = "1.0.0";

    @XmlElement(name = "UserLayer", namespace = SLDNamespaces.SLD)
    private List<UserLayer> userLayers;

    @XmlElement(name = "NamedLayer", namespace = SLDNamespaces.SLD)
    private List<NamedLayer> namedLayers;

}
