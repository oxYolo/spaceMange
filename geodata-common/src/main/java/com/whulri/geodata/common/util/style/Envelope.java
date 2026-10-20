package com.whulri.geodata.common.util.style;

import lombok.Data;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
@Data
public class Envelope {
    @XmlAttribute
    private String srsName;

    @XmlElement(name = "lowerCorner", namespace = SLDNamespaces.OGC)
    private String lowerCorner;

    @XmlElement(name = "upperCorner", namespace = SLDNamespaces.OGC)
    private String upperCorner;
}
