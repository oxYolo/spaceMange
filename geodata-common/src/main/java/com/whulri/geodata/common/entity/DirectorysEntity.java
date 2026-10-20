package com.whulri.geodata.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 *
 * @TableName sdms_directorys
 */
@TableName(value ="sdms_directorys")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DirectorysEntity implements Serializable {
    /**
     *
     */
    @TableId
    private String id;

    /**
     *
     */
    private String datatype;

    /**
     *
     */
    private String dataid;

    /**
     *
     */
    private String dataname;

    /**
     *
     */
    private String dataformat;

    /**
     *
     */
    private String datatypeid;

    /**
     *
     */
    private String tag;

    /**
     *
     */
    private String createuser;

    /**
     *
     */
    private String attribute;

    /**
     *
     */
    private Integer codesort;

    /**
     *
     */
    private Date createdt;

    /**
     *
     */
    private String Public;

    /**
     *
     */
    private String othertype;

    /**
     *
     */
    private String layertype;

    /**
     *
     */
    private String picture;

    /**
     *
     */
    private Long filesize;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

}
