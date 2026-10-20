package com.whulri.geodata.common.util.style;


import lombok.Data;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
@Data
public class BBox extends Condition {
    @XmlElement(namespace = SLDNamespaces.OGC)
    private Envelope envelope;
}
