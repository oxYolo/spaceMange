package com.whulri.geodata.common.util.style;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "PropertyIsGreaterThanO", namespace = SLDNamespaces.OGC)
@XmlAccessorType(XmlAccessType.FIELD)
public class PropertyIsGreaterThanO extends Condition {
    public PropertyIsGreaterThanO(String propertyName, String literal) {
        this.propertyName = propertyName;
        this.literal = literal;
    }
    public PropertyIsGreaterThanO() {}
}