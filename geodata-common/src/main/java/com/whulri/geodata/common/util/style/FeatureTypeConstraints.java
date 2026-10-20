package com.whulri.geodata.common.util.style;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@XmlAccessorType(XmlAccessType.FIELD)
public class FeatureTypeConstraints {

    @XmlElement(name = "FeatureTypeConstraint", namespace = SLDNamespaces.SLD)
    private List<FeatureTypeConstraint> featureTypeConstraint;

}
