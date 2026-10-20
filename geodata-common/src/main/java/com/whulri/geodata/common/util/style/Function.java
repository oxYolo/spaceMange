package com.whulri.geodata.common.util.style;

import lombok.Data;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

@Data
@XmlAccessorType(XmlAccessType.FIELD)
public class Function {
    @XmlAttribute(name = "name")
    private String name;
    
    @XmlElement(name = "PropertyName", namespace = SLDNamespaces.OGC)
    private String propertyName;
}
