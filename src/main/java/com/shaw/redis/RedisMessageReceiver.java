package com.shaw.redis;

import com.alibaba.fastjson.JSONObject;
import com.shaw.constants.Constants;
import com.shaw.netty.SocketMessage;
import com.shaw.utils.DownloadUtils;
import com.shaw.utils.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/** Created by shaw on 2017/1/3 0003. */
public class RedisMessageReceiver {
  private static final Logger logger = LoggerFactory.getLogger(RedisMessageReceiver.class);
  //消息消费方法名称
  public static final String DEFAULT_LISTENEER_METHOD = "receiveMessage";

  /** 消息消费方法 */
  public void receiveMessage(String message) {
    JSONObject messageObject = JSONObject.parseObject(message);
    String topic = messageObject.getString("topic");
    if (Constants.TOPIC_DOWNLOAD.equals(topic)) {
      String url = messageObject.getString("url");
      if (!StringUtils.isEmpty(url)) {
        String path = messageObject.getString("path");
        //启动下载任务线程
        if (!StringUtils.isEmpty(path)) {
          ThreadPoolManager.INSTANCE.execute(DownloadUtils.getDownloadTask(url, path));
        } else {
          //默认保存路径为项目路径根路径
          ThreadPoolManager.INSTANCE.execute(
              DownloadUtils.getDownloadTask(url, "defaultSavePath/"));
        }
      } else {
        logger.info("Wrong Message:" + message);
      }
    } else if (Constants.TOPIC_TASK.equals(topic)) {
      SocketMessage socketMessage = null;
      try {
        socketMessage =
            JSONObject.parseObject(messageObject.getString("content"), SocketMessage.class);
      } catch (Exception e) {
        logger.error("parse msg fail:" + message);
      }
      SocketMsgSender.handlerRedisMsg(socketMessage);
    } else {

    }
  }
}
