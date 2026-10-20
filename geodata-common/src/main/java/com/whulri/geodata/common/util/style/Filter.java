package com.whulri.geodata.common.util.style;

import lombok.Data;
import lombok.ToString;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "Filter", namespace = SLDNamespaces.OGC)
@XmlAccessorType(XmlAccessType.FIELD)
@ToString(callSuper = true)
@Data
public class Filter extends Compare {
    @XmlElement(name = "And", namespace = SLDNamespaces.OGC)
    private And and;

    @XmlElement(name = "Or",namespace = SLDNamespaces.OGC)
    private Or or;

    @XmlElement(name = "Not",namespace = SLDNamespaces.OGC)
    private Not not;
}