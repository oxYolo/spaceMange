package com.whulri.geodata.common.util.style;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

// PropertyIsEqualTo
@Data
@XmlRootElement(name = "PropertyIsEqualTo", namespace = SLDNamespaces.OGC)
@XmlAccessorType(XmlAccessType.FIELD)
@NoArgsConstructor
public class PropertyIsEqualTo extends Condition {

    public PropertyIsEqualTo(String propertyName, String literal) {
        this.propertyName = propertyName;
        this.literal = literal;
    }
}

// PropertyIsLike
@XmlRootElement(name = "PropertyIsLike", namespace = SLDNamespaces.OGC)
@XmlAccessorType(XmlAccessType.FIELD)
class PropertyIsLike extends Condition {
}

// PropertyIsNull
@XmlRootElement(name = "PropertyIsNull", namespace = SLDNamespaces.OGC)
@XmlAccessorType(XmlAccessType.FIELD)
class PropertyIsNull extends Condition {
}