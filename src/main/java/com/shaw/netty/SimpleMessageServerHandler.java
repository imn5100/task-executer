package com.shaw.netty;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.*;


/**
 * Created by shaw on 2017/1/10 0010.
 */
@Component
@ChannelHandler.Sharable
public class SimpleMessageServerHandler extends SimpleChannelInboundHandler<String> {

    public static Logger logger = LoggerFactory.getLogger(SimpleMessageServerHandler.class);
    public static final String USER_AUTH_MAP = "UserAuthMap";

    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    //用于保存连接的channel，通过appkey 获取对应连接，给指定客户端发送数据 如果channel失效，从map中移除。
    public static ConcurrentMap<String, Channel> channelMap = new ConcurrentHashMap<String, Channel>();

    public static void checkChannelMap() {
        for (String key : channelMap.keySet()) {
            final Channel channel = channelMap.get(key);
            if (channel != null) {
                if (!channel.isActive()) {
                    channel.close();
                    SimpleMessageServerHandler.channelMap.remove(key);
                    logger.info(key + " is Remove from channelMap");
                }
            } else {
                SimpleMessageServerHandler.channelMap.remove(key);
                logger.info(key + " is Remove from channelMap");
            }
        }
    }

    static {
        //当SimpleMessageServerHandler 被创建时，开启一个监视channelMap的定时执行器。
        //定时检查channel,如果channel非活跃的 关闭channel，并从map中移除
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        //每5分钟检查一次所有连接
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                checkChannelMap();
            }
        }, 0, 5, TimeUnit.MINUTES);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        //@Deprecated 如果有大量无效请求，该时间会频繁触发，所以不再这里检查所有的连接的有效性。改为定时器触发
        //每当有channel失效时，重新检查Map
//        checkChannelMap();

    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String s) throws Exception {
        System.out.println("getMessage:" + s);
        SocketMessage message;
        try {
            message = JSON.parseObject(s, SocketMessage.class);
        } catch (Exception e) {
            logger.warn("Get JsonStr:ParseObject fail" + s);
            ctx.close();
            return;
        }
        if (message.getType() == 1) {
            if (!StringUtils.isEmpty(message.getAppKey())) {
                String info = (String) redisTemplate.opsForHash().get(USER_AUTH_MAP, message.getAppKey());
                //未通过登录验证
                if (info == null || message.getAppSecret() == null) {
                    ctx.close();
                    return;
                } else {
                    JSONObject infoObject = JSONObject.parseObject(info);
                    String appSercet = infoObject.getString("appSercet");
                    if (message.getAppSecret().equals(appSercet)) {
                        //登录验证通过
                        //当重复连接时，移除上一个连接
                        Channel channel = channelMap.get(message.getAppKey());
                        if (channel != null && channel.isActive()) {
                            logger.info("remove old channel. appkey:" + message.getAppKey());
                            channel.close();
                        }
                        logger.info("put channel. appkey:" + message.getAppKey());
                        channelMap.put(message.getAppKey(), ctx.channel());
                    } else {
                        ctx.close();
                    }
                }
            } else {
                ctx.close();
            }
        } else {
            ctx.close();
        }
    }
}
