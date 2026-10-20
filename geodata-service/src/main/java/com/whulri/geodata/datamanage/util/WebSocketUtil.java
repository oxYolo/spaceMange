package com.whulri.geodata.datamanage.util;

import com.alibaba.fastjson.JSON;
import com.whulri.geodata.common.entity.ProgressInfo;
import com.whulri.geodata.common.vo.ProcessVO;
import com.whulri.geodata.datamanage.socket.SignalrClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebSocketUtil {

	/**
	 * 兼容旧签名：无业务主键/状态（前端走整列表刷新）。
	 */
	public static void sendMsg(SignalrClient connection, String userId, String type,
							   Integer step, Integer progress, String msg,
							   Boolean isError, Boolean isFinish, String result) {
		sendMsg(connection, userId, type, null, null, step, progress, msg, isError, isFinish, null, result);
	}

	/**
	 * 完整签名：携带 bizId（model3dId）/ status（状态机码）/ failReason / publishKey，
	 * 供前端行级局部更新，替代整列表刷新。老字段（step/progress/isFinish/result）语义不变。
	 */
	public static void sendMsg(SignalrClient connection, String userId, String type,
							   String bizId, Integer status, Integer step, Integer progress, String msg,
							   Boolean isError, Boolean isFinish, String failReason, String result) {
		log.info(msg);
		try {
			ProgressInfo info = new ProgressInfo();
			info.setCurrentStep(step);
			info.setCurrentProgress(progress);
			info.setCurrentWork(msg);
			info.setCurrentUserId(userId);
			info.setIsError(isError != null && isError);
			info.setIsFinish(isFinish != null && isFinish);
			info.setResult(result);
			info.setBizId(bizId);
			info.setStatus(status);
			info.setFailReason(failReason);
			info.setPublishKey(result);
			connection.sendPrivate(userId, JSON.toJSONString(new ProcessVO(type, info)));
		} catch (Exception rx) {
			log.warn("SignalR 消息发送失败: userId={}, type={}, bizId={}, err={}",
					userId, type, bizId, rx.getMessage());
		}
	}
}
