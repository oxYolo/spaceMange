package com.whulri.geodata.common.util.style;

import lombok.Data;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlSeeAlso;

@Data
@XmlAccessorType(XmlAccessType.FIELD)
@XmlSeeAlso({
        PropertyIsEqualTo.class,
        PropertyIsNotEqualTo.class,
        PropertyIsLessThan.class,
        PropertyIsGreaterThan.class,
        PropertyIsLessThanOrEqualTo.class,
        PropertyIsGreaterThanOrEqualTo.class,
        PropertyIsLike.class,
        PropertyIsNull.class,
        PropertyIsBetween.class,
        BBox.class
})
public class Condition {

    @XmlElement(name = "Function", namespace = SLDNamespaces.OGC)
    protected Function function;

    @XmlElement(name = "PropertyName", namespace = SLDNamespaces.OGC)
    protected String propertyName;

    @XmlElement(name = "Literal", namespace = SLDNamespaces.OGC)
    protected String literal;
}
