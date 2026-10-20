package com.whulri.geodata.common.util.style;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "PropertyIsLessThanOrEqualTo", namespace = SLDNamespaces.OGC)
@XmlAccessorType(XmlAccessType.FIELD)
public class PropertyIsLessThanOrEqualTo extends Condition {
    public PropertyIsLessThanOrEqualTo(String propertyName, String literal) {
        this.propertyName = propertyName;
        this.literal = literal;
    }
    public PropertyIsLessThanOrEqualTo() {}
}