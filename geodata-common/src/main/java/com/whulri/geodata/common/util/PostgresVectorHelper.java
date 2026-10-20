package com.whulri.geodata.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PostgresVectorHelper {

    @Autowired
    private JdbcTemplate jdbcTemplate;


    private String connStr;
    private int batchSize = 500;

    public PostgresVectorHelper() {
        // Default constructor
    }

    public PostgresVectorHelper(String connStr) {
        this.connStr = connStr;
        this.jdbcTemplate = createJdbcTemplate(connStr);
    }

    private JdbcTemplate createJdbcTemplate(String connStr) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(connStr);
        return new JdbcTemplate(dataSource);
    }

    /**
     * Execute SQL query and return results as a List of Maps
     */
    public List<Map<String, Object>> getData(String sql) {
        try {
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.error("执行SQL查询失败: {}", sql, e);
            throw e;
        }
    }

    /**
     * Execute SQL query with timeout
     */
    public List<Map<String, Object>> getData(String sql, int timeout) {
        try {
            jdbcTemplate.setQueryTimeout(timeout);
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.error("执行SQL查询失败: {}", sql, e);
            throw e;
        }
    }

    /**
     * Execute SQL updateByEntity operation
     */
    public boolean executeSQL(String sql) {
        try {
            jdbcTemplate.execute(sql);
            return true;
        } catch (Exception e) {
            log.error("执行SQL更新失败: {}", sql, e);
            return false;
        }
    }

    /**
     * Execute SQL updateByEntity operation with table name
     */
    public boolean executeSQL(String sql, String tableName) {
        log.info("Executing SQL on table {}: {}", tableName, sql);
        return executeSQL(sql);
    }

    /**
     * Get row count from table
     */
    public int getDataRowCount(String tableName) {
        try {
            String sql = String.format("SELECT COUNT(*) FROM %s", tableName);
            return jdbcTemplate.queryForObject(sql, Integer.class);
        } catch (Exception e) {
            log.error("获取表行数失败: {}", tableName, e);
            return 0;
        }
    }

    /**
     * Get max SID from table
     */
    public int getMaxSID(String tableName) {
        try {
            String sql = String.format("SELECT MAX(SID) FROM %s", tableName);
            Integer result = jdbcTemplate.queryForObject(sql, Integer.class);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("获取最大SID失败: {}", tableName, e);
            return 0;
        }
    }

    /**
     * Check if table exists
     */
    public boolean isTableExists(String tableName) {
        try {
            String sql = "SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename = ?";
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, tableName.toLowerCase());
            return !result.isEmpty();
        } catch (Exception e) {
            log.error("检查表是否存在失败: {}", tableName, e);
            return false;
        }
    }

    /**
     * Create table with specified columns
     */
    public void createTable(String tableName, List<String> columns) {
        if (!isTableExists(tableName)) {
            StringBuilder sql = new StringBuilder()
                .append("CREATE TABLE ").append(tableName)
                .append("(SID SERIAL PRIMARY KEY,");

            for (String column : columns) {
                sql.append(column).append(" TEXT,");
            }
            sql.append("geom GEOMETRY)");

            executeSQL(sql.toString());
        }
    }

    /**
     * Import shapefile data into table
     */
    public boolean importShapefileData(String tableName, String filePath, List<String> attributes) {
        // Note: This is a placeholder. The actual implementation would require the ShpReader class
        // and proper GDAL/OGR integration, which should be implemented separately
        log.info("Shapefile import functionality requires ShpReader implementation");
        return false;
    }

    /**
     * Execute scalar query
     */
    public Object executeScalar(String sql) {
        try {
            return jdbcTemplate.queryForObject(sql, Object.class);
        } catch (Exception e) {
            log.error("执行标量查询失败: {}", sql, e);
            return null;
        }
    }

    /**
     * Copy shapefile-related files
     */
    protected boolean copyShapefileFiles(String sourceFile, String targetDir) {
        try {
            String baseName = new File(sourceFile).getName();
            baseName = baseName.substring(0, baseName.lastIndexOf('.'));

            String sourceDir = new File(sourceFile).getParent();
            String[] extensions = {".shp", ".shx", ".dbf", ".prj"};

            for (String ext : extensions) {
                File source = new File(sourceDir, baseName + ext);
                if (source.exists()) {
                    File target = new File(targetDir, baseName + ext);
                    try (FileInputStream fis = new FileInputStream(source);
                         FileOutputStream fos = new FileOutputStream(target)) {
                        byte[] buffer = new byte[8192];
                        int length;
                        while ((length = fis.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                    }
                }
            }
            return true;
        } catch (Exception e) {
            log.error("复制Shapefile文件失败", e);
            return false;
        }
    }
}
