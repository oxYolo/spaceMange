package com.whulri.geodata.common.util;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

public class ChineseConvert {

    /**
     * 获取中文的拼音表示
     *
     * @param text 中文字符串
     * @return 拼音字符串（小写，无音调）
     */
    public String getPinyin(String text) {
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);

        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            try {
                // 处理中文字符
                if (isChineseCharacter(c)) {
                    String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, format);
                    if (pinyinArray != null && pinyinArray.length > 0) {
                        sb.append(pinyinArray[0]);
                    }
                }
                // 保留非中文字符
                else {
                    sb.append(c);
                }
            } catch (BadHanyuPinyinOutputFormatCombination e) {
                // 异常处理
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 获取中文拼音首字母
     *
     * @param text 中文字符串
     * @return 首字母字符串（小写，已过滤非法字符）
     */
    public String getPinyinInitials(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            try {
                if (isChineseCharacter(c)) {
                    String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c);
                    if (pinyinArray != null && pinyinArray.length > 0) {
                        sb.append(pinyinArray[0].charAt(0));
                    }
                } else {
                    sb.append(c);
                }
            } catch (Exception e) {
                sb.append(c);
            }
        }
        return checkTableName(sb.toString().toLowerCase());
    }

    /**
     * 过滤非法表名字符（保留字母、数字和下划线）
     *
     * @param tableName 原始字符串
     * @return 合法表名字符串
     */
    public String checkTableName(String tableName) {
        StringBuilder validName = new StringBuilder();
        for (char c : tableName.toCharArray()) {
            if (isValidTableNameCharacter(c)) {
                validName.append(c);
            }
        }
        return validName.toString();
    }

    // 辅助方法：判断是否为中文字符
    private boolean isChineseCharacter(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION;
    }

    // 辅助方法：验证表名字符合法性
    private boolean isValidTableNameCharacter(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

}
