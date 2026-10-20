package com.whulri.geodata.common.util.style;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "PropertyIsGreaterThanOrEqualTo", namespace = SLDNamespaces.OGC)
@XmlAccessorType(XmlAccessType.FIELD)
public class PropertyIsGreaterThanOrEqualTo extends Condition {
    public PropertyIsGreaterThanOrEqualTo(String propertyName, String literal) {
        this.propertyName = propertyName;
        this.literal = literal;
    }
    public PropertyIsGreaterThanOrEqualTo() {}
}