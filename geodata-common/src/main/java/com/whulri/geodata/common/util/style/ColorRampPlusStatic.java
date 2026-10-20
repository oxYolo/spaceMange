package com.whulri.geodata.common.util.style;

public class ColorRampPlusStatic {
	// 静态实例（volatile保证多线程可见性）
	private static volatile ColorRampPlus _ColorRampPlus = null;

	// 私有构造函数（防止外部实例化）
	private ColorRampPlusStatic() {
	}

	/**
	 * 获取ColorRampPlus实例（线程安全的懒汉式单例）
	 *
	 * @return ColorRampPlus实例
	 */
	public static ColorRampPlus getColorRampPlus() {
		// 第一次检查（无锁快速路径）
		if (_ColorRampPlus == null) {
			synchronized (ColorRampPlusStatic.class) {
				// 第二次检查（防止多线程同时创建实例）
				if (_ColorRampPlus == null) {
					_ColorRampPlus = new ColorRampPlus();
				}
			}
		}
		return _ColorRampPlus;
	}

	/**
	 * 设置ColorRampPlus实例（允许替换，默认用于测试或扩展）
	 *
	 * @param instance 新的ColorRampPlus实例
	 */
	public static void setColorRampPlus(ColorRampPlus instance) {
		synchronized (ColorRampPlusStatic.class) {
			_ColorRampPlus = instance;
		}
	}
}
