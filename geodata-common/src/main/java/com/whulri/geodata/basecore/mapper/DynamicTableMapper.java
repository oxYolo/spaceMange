package com.whulri.geodata.basecore.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface DynamicTableMapper {

	//  MaxMinDto getMaxMin(@Param("lat") String lat, @Param("lon") String lon, @Param("tableName") String tableName);

	Integer getMaxSidByTable(@Param("tableName") String tableName);

	List<Map<String, String>> getByTable(@Param("tableName") String tableName);


	Integer getMaxSIDFromTable(@Param("tableName") String tableNam);

	int insertByTable(@Param("tableName") String tabName,
					  @Param("filedKey") String filedKey,
					  @Param("filedValue") String filedValue,
					  @Param("guid") String guid,
					  @Param("sid") int sid,
					  @Param("geom") String geom);

//    int insertByBatch(@Param("tableName") String tabName,
//                      @Param("filedKey")String filedKey,
//                      @Param("list")List<BizBatchAdd> list);
//
//    int intoMonitorStation(@Param("tableName") String tabName,
//                           @Param("filedKey")String filedKey,
//                           @Param("list")List<BizBatchAdd> list);

	int getCountByGuid(@Param("guid") String guid, @Param("tableName") String tabName);

	int delByGuid(@Param("guid") String guid, @Param("tableName") String tabName);

	int existTable(@Param("tableName") String tableName);

	List<String> getColumnFromTable(@Param("tableName") String tableName);

	int updateByGuid(@Param("tableName") String tableName, @Param("guid") String guid, @Param("fieldMap") Map<String, Object> fieldMap);

	int updateByFileInfoGuid(@Param("tableName") String tableName, @Param("guid") String guid, @Param("fieldMap") Map<String, Object> fieldMap);

	List<Map<String, Object>> dynamicExecute(@Param("sql") String sql);

	int dynamicInsertExecute(@Param("sql") String sql);

	int dynamicUpdateExecute(@Param("sql") String sql);

	void batchInsertWithGeom(
			@Param("tableName") String tableName,
			@Param("srid") int srid,
			@Param("attrNames") List<String> attrNames,
			@Param("records") List<Map<String, Object>> records
	);

}
