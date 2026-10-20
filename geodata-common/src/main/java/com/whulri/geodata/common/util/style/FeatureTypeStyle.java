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
public class FeatureTypeStyle {

    @XmlElement(name = "Name",namespace = SLDNamespaces.SLD)
    private String name;

    @XmlElement(name = "Title",namespace = SLDNamespaces.SLD)
    private String title;

    @XmlElement(name = "Abstract", namespace = SLDNamespaces.SLD)
    private String abstractDesc;

	@XmlElement(name = "FeatureTypeName", namespace = SLDNamespaces.SLD)
    private String featureTypeName;

    @XmlElement(name = "SemanticTypeIdentifier", namespace = SLDNamespaces.SLD)
    private List<String> semanticTypeIdentifiers;

    @XmlElement(name = "Rule", namespace = SLDNamespaces.SLD)
    private List<Rule> rules;


}
