package com.whulri.geodata.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author zzw
 * @date 2026/3/24 18:28
 */
public class JenksBreaksUtil {

    /**
     * 核心方法：自然断点法划分TIF数据等级范围
     * @param tifData 从TIF读取的原始double数组
     * @param noData TIF的无效值（需过滤）
     * @param numClasses 期望的等级数
     * @return 每个等级的数值范围列表（数量=等级数）
     * @throws IllegalArgumentException 等级数非法/无有效数据
     */
    public static List<Map<String,Double>> calculateJenksBreaks(double[] tifData, double noData, int numClasses) {
        // 1. 数据预处理：过滤nodata + 去重 + 排序
        List<Double> validData = Arrays.stream(tifData)
                .filter(num -> !Double.isNaN(num) && num != noData) // 过滤nodata和NaN
                .distinct() // 去重
                .boxed()
                .sorted() // 升序排序
                .collect(Collectors.toList());
        // 2. 边界校验
        if (validData.isEmpty()) {
            throw new IllegalArgumentException("无有效数据（全部为nodata或空数组）");
        }
        if (numClasses <= 0) {
            throw new IllegalArgumentException("等级数必须大于0");
        }
        // 若有效数据量 < 等级数，调整等级数为有效数据的唯一值数量
        int actualClasses = Math.min(numClasses, validData.size());
        if (actualClasses == 1) {
            // 只有1个等级：范围为全部数据的min-max
            HashMap<String, Double> map = new HashMap<>();
            double min = validData.get(0);
            double max = validData.get(validData.size() - 1);
            BigDecimal maxb = new BigDecimal(String.valueOf(max));
            double maxs = maxb.setScale(3, RoundingMode.UP).stripTrailingZeros().doubleValue();
            BigDecimal minb = new BigDecimal(String.valueOf(min));
            double mins = minb.setScale(3, RoundingMode.DOWN).stripTrailingZeros().doubleValue();
            map.put("min",mins);
            map.put("max",maxs);
            return Collections.singletonList(map);
        }
        // 3. 转换为数组供Jenks算法使用
        double[] dataArray = validData.stream().mapToDouble(Double::doubleValue).toArray();
        // 4. 执行Jenks算法计算断点
//        double[] breaks = jenks(dataArray, actualClasses);
        double[] breaks = jenksWithSampling(dataArray, actualClasses);
        // 5. 根据断点划分每个等级的范围
        List<Map<String,Double>> ranges = new ArrayList<>();
        for (int i = 0; i < actualClasses; i++) {
            HashMap<String, Double> map = new HashMap<>();
            double min = (i == 0) ? dataArray[0] : breaks[i]; // 第一级min为数据最小值
            double max = breaks[i + 1]; // 每级max为下一个断点
            BigDecimal maxb = new BigDecimal(String.valueOf(max));
            double maxs = maxb.setScale(3, RoundingMode.UP).stripTrailingZeros().doubleValue();
            BigDecimal minb = new BigDecimal(String.valueOf(min));
            double mins = minb.setScale(3, RoundingMode.DOWN).stripTrailingZeros().doubleValue();
            map.put("min",mins);
            map.put("max",maxs);
            ranges.add(map);
        }
        return ranges;
    }

    // 当数据量超过阈值时进行采样
    private static double[] jenksWithSampling(double[] data, int numClasses) {
        int n = data.length;
        int sampleSize = Math.min(n, 10000); // 最多采样1万个
        if (n <= sampleSize) {
            return jenksOptimized(data, numClasses);
        }
        // 排序后均匀采样
        double[] sorted = data.clone();
        Arrays.sort(sorted);

        double[] sampled = new double[sampleSize];
        int step = n / sampleSize;
        for (int i = 0; i < sampleSize; i++) {
            sampled[i] = sorted[i * step];
        }

        // 在采样数据上计算断点
        double[] sampleBreaks = jenksOptimized(sampled, numClasses);

        // 将断点映射回原数据（二分查找）
        double[] finalBreaks = new double[numClasses + 1];
        finalBreaks[0] = sorted[0];
        finalBreaks[numClasses] = sorted[n - 1];
        for (int i = 1; i < numClasses; i++) {
            int idx = Arrays.binarySearch(sorted, sampleBreaks[i]);
            if (idx < 0) idx = -idx - 1;
            finalBreaks[i] = sorted[Math.min(idx, n - 1)];
        }
        return finalBreaks;
    }

    private static double[] jenksOptimized(double[] data, int numClasses) {
        // 1. 先排序（Jenks算法要求数据有序）
        double[] sortedData = data.clone();
        Arrays.sort(sortedData);
        int n = sortedData.length;
        // 2. 预处理前缀和与前缀平方和
        double[] prefixSum = new double[n + 1];
        double[] prefixSumSq = new double[n + 1];
        for (int i = 0; i < n; i++) {
            prefixSum[i + 1] = prefixSum[i] + sortedData[i];
            prefixSumSq[i + 1] = prefixSumSq[i] + sortedData[i] * sortedData[i];
        }
        // 3. O(1)计算区间方差的辅助方法
        // 方差 = E(X²) - [E(X)]² = (sumSq/count) - (sum/count)²
        // 组内平方和 = sumSq - sum²/count
        // 4. 初始化矩阵
        double[][] mat1 = new double[n + 1][numClasses + 1];
        int[][] mat2 = new int[n + 1][numClasses + 1];
        // 5. 初始化
        for (int i = 1; i <= numClasses; i++) {
            mat1[1][i] = 0.0;
            mat2[1][i] = 1;
        }
        for (int i = 1; i <= n; i++) {
            mat1[i][1] = getVarianceO1(prefixSum, prefixSumSq, 0, i - 1);
            mat2[i][1] = 1;
        }
        // 6. 动态规划（现在getVariance是O(1)了）
        for (int j = 2; j <= numClasses; j++) {
            for (int i = j; i <= n; i++) {
                mat1[i][j] = Double.MAX_VALUE;
                for (int l = j - 1; l < i; l++) {
                    double currentVariance = mat1[l][j - 1] +
                            getVarianceO1(prefixSum, prefixSumSq, l, i - 1);
                    if (currentVariance < mat1[i][j]) {
                        mat1[i][j] = currentVariance;
                        mat2[i][j] = l + 1;
                    }
                }
            }
        }
        // 7. 回溯获取断点（同原代码）
        int k = n;
        int[] breakIndices = new int[numClasses + 1];
        breakIndices[numClasses] = n - 1;
        for (int j = numClasses; j > 1; j--) {
            k = mat2[k][j] - 1;
            breakIndices[j - 1] = k;
        }
        breakIndices[0] = 0;
        double[] breaks = new double[numClasses + 1];
        for (int i = 0; i < breakIndices.length; i++) {
            breaks[i] = sortedData[breakIndices[i]];
        }
        return breaks;
    }
    // O(1)计算区间组内平方和
    private static double getVarianceO1(double[] prefixSum, double[] prefixSumSq,
                                        int start, int end) {
        int count = end - start + 1;
        if (count <= 1) return 0.0;
        double sum = prefixSum[end + 1] - prefixSum[start];
        double sumSq = prefixSumSq[end + 1] - prefixSumSq[start];

        // 组内平方和 = Σ(xi - mean)² = Σxi² - (Σxi)²/count
        return sumSq - (sum * sum) / count;
    }

    /**
     * 核心算法：Jenks自然断点实现（最小组内平方和）
     * @param data 有序、去重、无无效值的数组
     * @param numClasses 等级数
     * @return 断点数组（长度=numClasses+1，包含最小值和最大值）
     */
    private static double[] jenks(double[] data, int numClasses) {
        int n = data.length;
        // 初始化矩阵：mat1[i][j] = 前i个数据分为j类的最小组内平方和
        // mat2[i][j] = 前i个数据分为j类时的最优断点位置
        double[][] mat1 = new double[n + 1][numClasses + 1];
        int[][] mat2 = new int[n + 1][numClasses + 1];
        // 初始化对角线（单个数据分为1类，平方和为0）
        for (int i = 1; i <= numClasses; i++) {
            mat1[1][i] = 0.0;
            mat2[1][i] = 1;
        }
        for (int i = 1; i <= n; i++) {
            mat1[i][1] = getVariance(data, 0, i - 1); // 前i个数据分为1类的方差
            mat2[i][1] = 1;
        }
        // 动态规划计算最小组内平方和
        for (int j = 2; j <= numClasses; j++) {
            for (int i = j; i <= n; i++) {
                mat1[i][j] = Double.MAX_VALUE;
                // 遍历所有可能的断点位置
                for (int l = j - 1; l < i; l++) {
                    double currentVariance = mat1[l][j - 1] + getVariance(data, l, i - 1);
                    // 保留最小平方和的断点
                    if (currentVariance < mat1[i][j]) {
                        mat1[i][j] = currentVariance;
                        mat2[i][j] = l + 1; // 转换为1-based索引
                    }
                }
            }
        }
        // 回溯获取断点
        int k = n;
        int[] breakIndices = new int[numClasses + 1];
        breakIndices[numClasses] = n - 1; // 最后一个断点为数据最后一位索引
        for (int j = numClasses; j > 1; j--) {
            k = mat2[k][j] - 1;
            breakIndices[j - 1] = k;
        }
        breakIndices[0] = 0; // 第一个断点为数据第一位索引
        // 转换为数值断点
        double[] breaks = new double[numClasses + 1];
        for (int i = 0; i < breakIndices.length; i++) {
            breaks[i] = data[breakIndices[i]];
        }
        return breaks;
    }

    /**
     * 辅助方法：计算数组指定区间[start, end]的方差（组内平方和）
     * @param data 有序数组
     * @param start 起始索引（包含）
     * @param end 结束索引（包含）
     * @return 区间方差
     */
    private static double getVariance(double[] data, int start, int end) {
        if (start == end) return 0.0;
        int count = end - start + 1;
        double sum = 0.0, sumSq = 0.0;
        for (int i = start; i <= end; i++) {
            sum += data[i];
            sumSq += data[i] * data[i];
        }
        // 方差公式：(平方和/数量) - (均值)^2，再乘以数量得到组内平方和
        double mean = sum / count;
        return sumSq - sum * mean;
    }

    public static List<Map<String,Double>> calculateJenksBreaksList(List<Double> valueList,int numClasses) {
        // 1. 数据预处理：过滤nodata + 去重 + 排序
        List<Double> validData = valueList.stream().distinct().sorted().collect(Collectors.toList());
        // 2. 边界校验
        if (validData.isEmpty()) {
            throw new IllegalArgumentException("无有效数据（全部为nodata或空数组）");
        }
        if (numClasses <= 0) {
            throw new IllegalArgumentException("等级数必须大于0");
        }
        // 若有效数据量 < 等级数，调整等级数为有效数据的唯一值数量
        int actualClasses = Math.min(numClasses, validData.size());
        if (actualClasses == 1) {
            // 只有1个等级：范围为全部数据的min-max
            HashMap<String, Double> map = new HashMap<>();
            double min = validData.get(0);
            double max = validData.get(validData.size() - 1);
            BigDecimal maxb = new BigDecimal(String.valueOf(max));
            double maxs = maxb.setScale(3, RoundingMode.UP).stripTrailingZeros().doubleValue();
            BigDecimal minb = new BigDecimal(String.valueOf(min));
            double mins = minb.setScale(3, RoundingMode.DOWN).stripTrailingZeros().doubleValue();
            map.put("min",mins);
            map.put("max",maxs);
            return Collections.singletonList(map);
        }
        // 3. 转换为数组供Jenks算法使用
        double[] dataArray = validData.stream().mapToDouble(Double::doubleValue).toArray();
        // 4. 执行Jenks算法计算断点
        double[] breaks = jenks(dataArray, actualClasses);
        // 5. 根据断点划分每个等级的范围
        List<Map<String,Double>> ranges = new ArrayList<>();
        for (int i = 0; i < actualClasses; i++) {
            HashMap<String, Double> map = new HashMap<>();
            double min = (i == 0) ? dataArray[0] : breaks[i]; // 第一级min为数据最小值
            double max = breaks[i + 1]; // 每级max为下一个断点
            BigDecimal maxb = new BigDecimal(String.valueOf(max));
            double maxs = maxb.setScale(3, RoundingMode.UP).stripTrailingZeros().doubleValue();
            BigDecimal minb = new BigDecimal(String.valueOf(min));
            double mins = minb.setScale(3, RoundingMode.DOWN).stripTrailingZeros().doubleValue();
            map.put("min",mins);
            map.put("max",maxs);
            ranges.add(map);
        }
        return ranges;
    }

}
