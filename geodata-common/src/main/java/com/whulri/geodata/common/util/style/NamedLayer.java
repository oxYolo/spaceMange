package com.whulri.geodata.common.util.style;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.List;

/**
 * 命名图层
 */
@XmlAccessorType(XmlAccessType.FIELD)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NamedLayer {

    @XmlElement(namespace = SLDNamespaces.SLD)
    private String name = "";

    @XmlElement(namespace = SLDNamespaces.SLD)
    private String description;

    @XmlElement(name = "UserStyle", namespace = SLDNamespaces.SLD)
    private List<UserStyle> userStyles;

}
