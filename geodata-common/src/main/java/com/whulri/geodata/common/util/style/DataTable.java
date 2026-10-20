package com.whulri.geodata.common.util.style;

import javax.swing.table.DefaultTableModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class DataTable extends DefaultTableModel {
	// 存储原始数据行（用于自定义操作）
	private List<Object[]> dataRows = new ArrayList<>();
	private List<String> columnNames;  // 列名数组
	private Class<?>[] columnTypes;    // 列类型数组（Java类型）
	private List<List<Object>> rows;   // 数据行（每行是Object数组）
	private int[] columnMaxLengths;        // 列最大长度数组（仅对字符串等类型有效）

	public DataTable() {
		super();
	}


	public DataTable(Object[][] data, String[] columnNames) {
		super(data, columnNames);
		// 初始化dataRows
		for (Object[] row : data) {
			dataRows.add(row.clone());
		}
	}

	/**
	 * 构造函数：初始化列名和行数（初始行数为0）
	 *
	 * @param columnNames     列名数组
	 * @param initialRowCount 初始行数（通常为0）
	 */
	public DataTable(String[] columnNames, int initialRowCount) {
		this.columnNames = new ArrayList<>();
		for (String name : columnNames) {
			this.columnNames.add(name);
		}
		this.rows = new ArrayList<>(initialRowCount);
		// 初始化行占位符（避免后续添加行时频繁扩容）
		for (int i = 0; i < initialRowCount; i++) {
			rows.add(new ArrayList<>());
		}
	}

	public DataTable(List<String> columnNames, Class<?>[] columnTypes) {
	}

	// 重写setRowCount以同步dataRows
	@Override
	public void setRowCount(int rowCount) {
		super.setRowCount(rowCount);
		dataRows.clear();
		for (int i = 0; i < rowCount; i++) {
			dataRows.add(new Object[getColumnCount()]);
		}
	}

	// 重写setValueAt以同步dataRows
	@Override
	public void setValueAt(Object aValue, int row, int column) {
		super.setValueAt(aValue, row, column);
		if (row < dataRows.size()) {
			dataRows.get(row)[column] = aValue;
		}
	}

	// 获取原始数据行（用于序列化等操作）
	public List<Object[]> getDataRows() {
		return new ArrayList<>(dataRows);
	}


	/**
	 * 设置列类型（需与列名数量一致）
	 *
	 * @param columnTypes 列类型数组（Java类型）
	 * @throws IllegalArgumentException 列名与列类型数量不匹配时抛出
	 */
	public void setColumnTypes(Class<?>[] columnTypes) {
		if (columnTypes.length != columnNames.size()) {
			throw new IllegalArgumentException("列类型数量必须与列名数量一致");
		}
		this.columnTypes = columnTypes.clone(); // 防御性拷贝
	}

	/**
	 * 设置列类型（需与列名数量一致）
	 *
	 * @param columnIndex 列索引（从0开始）
	 * @param type        列类型（Java类型）
	 * @throws IllegalArgumentException 列索引无效或类型为null时抛出
	 */
	public void setColumnType(int columnIndex, Class<?> type) {
		if (columnIndex < 0 || columnIndex >= columnTypes.length) {
			throw new IllegalArgumentException("列索引超出范围");
		}
		if (type == null) {
			throw new IllegalArgumentException("列类型不能为null");
		}
		columnTypes[columnIndex] = type;
	}

	/**
	 * 设置列最大长度（仅对字符串等类型有效）
	 *
	 * @param columnIndex 列索引（从0开始）
	 * @param maxLength   最大长度（≥0）
	 * @throws IllegalArgumentException 列索引无效或maxLength<0时抛出
	 */
	public void setColumnMaxLength(int columnIndex, int maxLength) {
		if (columnIndex < 0 || columnIndex >= columnMaxLengths.length) {
			throw new IllegalArgumentException("列索引超出范围");
		}
		if (maxLength < 0) {
			throw new IllegalArgumentException("最大长度不能为负数");
		}
		columnMaxLengths[columnIndex] = maxLength;
	}

	// 分页核心方法：跳过指定行数后截取指定行数
	public DataTable skipTake(int skip, int take) {
		// 校验分页参数有效性
		if (skip < 0 || take <= 0) {
			throw new IllegalArgumentException("分页参数无效");
		}

		// 使用Java Stream完成分页
		List<List<Object>> paginatedRows = rows.stream()
			.skip(skip)       // 跳过前skip行（从0开始计数）
			.limit(take)      // 截取最多take行
			.collect(Collectors.toList());

		// 创建新的DataTable并填充分页数据
		DataTable newDt = new DataTable(columnNames, columnTypes);
		paginatedRows.forEach(newDt::addRow);
		return newDt;
	}

	// 添加行数据
	public void addRow(List<Object> row) {
		if (row.size() != getColumnCount()) {
			throw new IllegalArgumentException("行长度与列数不匹配");
		}
		rows.add(new ArrayList<>(row));
	}

	/**
	 * 分页核心方法（对应C#的Skip+Take+CopyToDataTable）
	 *
	 * @param skip 跳过的行数（从0开始）
	 * @param take 每页大小
	 * @return 分页后的新DataTable
	 */
	public DataTable paginate(int skip, int take) {
		// 参数校验
		if (skip < 0 || take <= 0) {
			throw new IllegalArgumentException("分页参数无效：skip≥0，take>0");
		}

		// 使用Stream分页
		List<List<Object>> paginatedRows = rows.stream()
			.skip(skip)    // 跳过前skip行（与C#的Skip一致）
			.limit(take)   // 截取最多take行（与C#的Take一致）
			.collect(Collectors.toList());

		// 创建新DataTable并复制分页数据
		DataTable newDt = new DataTable(columnNames, columnTypes);
		paginatedRows.forEach(newDt::addRow);
		return newDt;
	}

	/**
	 * 按指定列排序（升序）
	 *
	 * @param columnName 列名（如"值"）
	 */
	public void sortByColumn(String columnName) {
		int colIndex = columnNames.indexOf(columnName);
		if (colIndex == -1) return;

		rows.sort(Comparator.comparing(row -> (Integer) row.get(colIndex)));
	}
}
