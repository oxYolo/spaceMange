package com.whulri.geodata.common.util.style;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.List;

/**
 * 用户图层
 */
@Data
@XmlAccessorType(XmlAccessType.FIELD)
@NoArgsConstructor
@AllArgsConstructor
public class UserLayer {
//    @XmlElement(namespace = SLDNamespaces.SLD)
//    private String name;
//
//    @XmlElement(namespace = SLDNamespaces.SLD)
//    private String description;
//
//    @XmlElement(namespace = SLDNamespaces.SLD)
//    private FeatureTypeConstraints layerFeatureConstraints;
//
//    @XmlElement(name = "UserStyle", namespace = SLDNamespaces.SLD)
//    private List<UserStyle> userStyles;
//
//    public UserLayer() {}

    @XmlElement(namespace = SLDNamespaces.SLD)
    private String name;

    @XmlElement(namespace = SLDNamespaces.SLD)
    private String description;

    @XmlElement(name = "LayerFeatureConstraints", namespace = SLDNamespaces.SLD)
    private FeatureTypeConstraints layerFeatureConstraints;

    @XmlElement(name = "UserStyle", namespace = SLDNamespaces.SLD)
    private List<UserStyle> userStyles;

}
