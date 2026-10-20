package com.whulri.geodata.common.constant;

public class GeoServerConstants {

    public enum RequestMethod {
        Delete,
        Get,
        Post,
        Put
    }

    public enum ContentType {
        Json("application/json"),
        Xml("text/xml"),
        Html("text/html"),
        Sld("application/vnd.ogc.sld+xml"),
        SldFile("application/vnd.ogc.sld+xml"),
        Zip("application/zip");

        private final String value;

        ContentType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public enum AcceptType {
        Json("application/json"),
        Xml("text/xml"),
        Html("text/html"),
        Byte("application/octet-stream");

        private final String value;

        AcceptType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public enum SrsType {
        EPSG_4326("EPSG:4326"),
        EPSG_3857("EPSG:3857"),
        EPSG_900913("EPSG:900913");

        private final String value;

        SrsType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public enum TaskState {
        Running("running"),
        Pending("pending"),
        All("all");

        private final String value;

        TaskState(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
} 