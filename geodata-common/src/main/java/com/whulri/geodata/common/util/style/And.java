package com.whulri.geodata.common.util.style;

import lombok.Data;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
@Data
public class And extends Compare {

    @XmlElement(name = "Condition", namespace = SLDNamespaces.OGC)
    private List<Condition> conditions;
//    @XmlElement(name = "PropertyIsGreaterThan", namespace = SLDNamespaces.OGC)
//    private PropertyIsGreaterThan propertyIsGreaterThan;
//
//    @XmlElement(name = "PropertyIsLessThanOrEqualTo", namespace = SLDNamespaces.OGC)
//    private PropertyIsLessThanOrEq propertyIsLessThanOrEq;
}
