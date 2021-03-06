package com.github.netfreer.shadowducks.server;

import com.github.netfreer.shadowducks.common.config.AppConfig;
import com.github.netfreer.shadowducks.common.config.PortContext;
import com.github.netfreer.shadowducks.common.utils.AttrKeys;
import com.github.netfreer.shadowducks.common.utils.DucksFactory;
import com.github.netfreer.shadowducks.server.handler.TcpServerHandler;
import com.github.netfreer.shadowducks.server.handler.UdpServerHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * @author: landy
 * @date: 2017-04-26 22:30
 */
public class DucksServer {
    private final Logger log = LoggerFactory.getLogger(getClass());

    public void start(final AppConfig config) {
        if (config.getPorts().isEmpty()) {
            log.error("Not config any port !");
            System.exit(2);
        }
        NioEventLoopGroup boss = new NioEventLoopGroup(1);
        NioEventLoopGroup work = new NioEventLoopGroup();
        try {
            ServerBootstrap tcpBootstrap = new ServerBootstrap().group(boss, work)
                    .channel(NioServerSocketChannel.class)
//                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.AUTO_READ, false)
                    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(512 * 1024, 1024 * 1024))
                    .childHandler(new ChannelInitializer<NioSocketChannel>() {
                        @Override
                        protected void initChannel(NioSocketChannel ch) throws Exception {
                            ch.attr(AttrKeys.CHANNEL_BEGIN_TIME).set(System.currentTimeMillis());
                            PortContext portContext = config.getPortContext(ch.localAddress().getPort());
                            ch.pipeline().addLast(DucksFactory.getChannelHandler(portContext, true));
                            ch.pipeline().addLast(new TcpServerHandler(config));
                        }
                    });
            for (PortContext tuple : config.getPorts()) {
                try {
                    Channel channel = tcpBootstrap.bind(config.getServerAddress(), tuple.getPort()).sync().channel();
                    log.info("Start listen tcp port {} on address {}, method: {}, password: {}.", config.getServerAddress(),
                            tuple.getPort(), tuple.getMethod(), tuple.getPassword());
                } catch (Exception e) {
                    throw new IllegalStateException("can't bind tcp port " + tuple.getPort() + " on address " + config.getServerAddress(), e);
                }
            }
            Bootstrap udpBootstrap = new Bootstrap().group(work)
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            Channel ch = ctx.channel();
                            PortContext portContext = config.getPortContext(((InetSocketAddress) ch.localAddress()).getPort());
                            ch.pipeline().addLast(DucksFactory.getChannelHandler(portContext, false));
//                            ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                            ch.pipeline().addLast(new UdpServerHandler());
                            ch.pipeline().remove(this);
                            ctx.fireChannelActive();
                        }
                    });

            for (PortContext tuple : config.getPorts()) {
                try {
                    Channel channel = udpBootstrap.bind(config.getServerAddress(), tuple.getPort()).sync().channel();
                    log.info("Start listen udp port {} on address {}, method: {}, password: {}.", config.getServerAddress(),
                            tuple.getPort(), tuple.getMethod(), tuple.getPassword());
                } catch (Exception e) {
                    throw new IllegalStateException("can't bind udp port " + tuple.getPort() + " on address " + config.getServerAddress(), e);
                }
            }
            log.info("start server success !");
            boss.terminationFuture().sync();
        } catch (Exception e) {
            log.error("start server failure !", e);
        } finally {
            log.info("shutdown server");
            boss.shutdownGracefully();
            work.shutdownGracefully();
        }
    }
}
