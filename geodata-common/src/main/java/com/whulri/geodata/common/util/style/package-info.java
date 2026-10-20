@XmlSchema(
        namespace = "http://www.opengis.net/sld",
        elementFormDefault = XmlNsForm.QUALIFIED,
        xmlns = {
                @XmlNs(prefix = "", namespaceURI = "http://www.opengis.net/sld"),
                @XmlNs(prefix = "ogc", namespaceURI = "http://www.opengis.net/ogc"),
                @XmlNs(prefix = "xlink", namespaceURI = "http://www.w3.org/1999/xlink")
        }
)
package com.whulri.geodata.common.util.style;

import jakarta.xml.bind.annotation.XmlNs;
import jakarta.xml.bind.annotation.XmlNsForm;
import jakarta.xml.bind.annotation.XmlSchema;
