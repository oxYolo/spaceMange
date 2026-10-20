package com.whulri.geodata.common.util.style;

import lombok.Data;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@Data
@XmlAccessorType(XmlAccessType.FIELD)
public class Compare {
    @XmlElement(name = "PropertyIsEqualTo", namespace = SLDNamespaces.OGC)
    private PropertyIsEqualTo propertyIsEqualTo;//等于

    @XmlElement(name = "PropertyIsNotEqualTo",namespace = SLDNamespaces.OGC)
    private PropertyIsNotEqualTo propertyIsNotEqualTo;

    @XmlElement(name = "PropertyIsLessThan",namespace = SLDNamespaces.OGC)
    private PropertyIsLessThan propertyIsLessThan;//小于

    @XmlElement(name = "PropertyIsGreaterThan",namespace = SLDNamespaces.OGC)
    private PropertyIsGreaterThan propertyIsGreaterThan;//大于

    @XmlElement(name = "PropertyIsLessThanOrEqualTo",namespace = SLDNamespaces.OGC)
    private PropertyIsLessThanOrEqualTo propertyIsLessThanOrEqualTo;//小于等于

    @XmlElement(name = "PropertyIsGreaterThanOrEqualTo",namespace = SLDNamespaces.OGC)
    private PropertyIsGreaterThanOrEqualTo propertyIsGreaterThanOrEqualTo;//大于等于

    @XmlElement(name = "PropertyIsLike",namespace = SLDNamespaces.OGC)
    private PropertyIsLike propertyIsLike;

    @XmlElement(name = "PropertyIsNull",namespace = SLDNamespaces.OGC)
    private PropertyIsNull propertyIsNull;

    @XmlElement(name = "PropertyIsBetween",namespace = SLDNamespaces.OGC)
    private PropertyIsBetween propertyIsBetween;

    @XmlElement(name = "BBox",namespace = SLDNamespaces.OGC)
    private BBox bbox;

//    public void setPropertyIsLessThan(PropertyIsLessThan propertyIsLessThan) {
//        this.propertyIsLessThan = propertyIsLessThan;
//    }
//
//    public void setPropertyIsGreaterThan(PropertyIsGreaterThan propertyIsGreaterThan) {
//        this.propertyIsGreaterThan = propertyIsGreaterThan;
//    }
//
//    public void setPropertyIsLessThanOrEq(PropertyIsLessThanOrEq propertyIsLessThanOrEq) {
//        this.propertyIsLessThanOrEq = propertyIsLessThanOrEq;
//    }
//    // 由于使用了 @Data 注解，Lombok 会自动生成 getter/setter 方法
//    // 但为了确保方法存在，这里显式添加 setPropertyIsGreaterThanOrEqualTo 方法
//    public void setPropertyIsGreaterThanOrEqualTo(PropertyIsGreaterThanOrEqualTo propertyIsGreaterThanOrEqualTo) {
//        this.propertyIsGreaterThanOrEqualTo = propertyIsGreaterThanOrEqualTo;
//    }
}