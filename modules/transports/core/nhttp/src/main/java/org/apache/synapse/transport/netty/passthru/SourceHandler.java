package org.apache.synapse.transport.netty.passthru;


import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import org.apache.log4j.Logger;
import org.apache.synapse.transport.netty.passthru.config.SourceConfiguration;

import java.util.ArrayList;
import java.util.List;

public class SourceHandler extends ChannelInboundHandlerAdapter {

    private static Logger logger = Logger.getLogger(SourceHandler.class);

    private SourceConfiguration sourceConfiguration;
    private Bootstrap bootstrap;
    private ChannelFuture channelFuture;
    private TargetHandler  targetHandler;
    private Channel channel;

    private List<Request> requestList = new ArrayList<Request>();





    protected SourceHandler(SourceConfiguration sourceConfiguration){
        this.sourceConfiguration=sourceConfiguration;
    }


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        final Channel inboundChannel = ctx.channel();
        targetHandler = new TargetHandler(sourceConfiguration,inboundChannel);

        bootstrap = new Bootstrap();
        bootstrap.group(inboundChannel.eventLoop())
                .channel(ctx.channel().getClass())
                .handler(new TargetChannelinitializer(targetHandler));
        bootstrap.option(ChannelOption.TCP_NODELAY, true);
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,15000);
//        b.option(ChannelOption.SO_SNDBUF, 1024*5);
//        b.option(ChannelOption.SO_RCVBUF, 1024*50);
        bootstrap.option(ChannelOption.SO_SNDBUF, 1048576);
        bootstrap.option(ChannelOption.SO_RCVBUF, 1048576);

    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//        Request sourceRequest = new Request();
//        if (msg instanceof DefaultFullHttpRequest) {
//            FullHttpRequest fullHttpRequest = (FullHttpRequest) msg;
//            HttpHeaders headers = fullHttpRequest.headers();
//            for (String val : headers.names()) {
//                sourceRequest.addHttpheaders(val, headers.get(val));
//            }
//            sourceRequest.setTo(fullHttpRequest.getUri());
//            sourceRequest.setPipe(new Pipe("sourcePipe"));
//            ByteBuf buf = fullHttpRequest.content();
//            byte[] bytes = new byte[buf.readableBytes()];
//            buf.readBytes(bytes);
//            sourceRequest.setContentBytes(bytes);
//            HttpHeaders trailingHeaders = fullHttpRequest.trailingHeaders();
//            for (String val : trailingHeaders.names()) {
//                sourceRequest.getPipe().addTrailingHeader(val,trailingHeaders.get(val));
//            }
//            sourceRequest.setHttpMethod(fullHttpRequest.getMethod());
//            sourceRequest.setHttpVersion(fullHttpRequest.getProtocolVersion());
//            sourceRequest.setUri(fullHttpRequest.getUri());
//            sourceRequest.getPipe().writeFullContent(bytes);
//            sourceRequest.setInboundChannelHandlerContext(ctx);
//            sourceRequest.setBootstrap(bootstrap);
//            sourceConfiguration.getWorkerPool().execute(new RequestWorker(targetHandler,sourceRequest,this,sourceConfiguration));
//        }


        Request sourceRequest=null;
           if(msg instanceof DefaultHttpRequest){
               sourceRequest = new Request();
               DefaultHttpRequest defaultHttpRequest = (DefaultHttpRequest) msg;
               HttpHeaders headers = defaultHttpRequest.headers();
               for (String val : headers.names()) {
                   sourceRequest.addHttpheaders(val, headers.get(val));
               }
               sourceRequest.setTo(defaultHttpRequest.getUri());
               sourceRequest.setHttpMethod(defaultHttpRequest.getMethod());
               sourceRequest.setHttpVersion(defaultHttpRequest.getProtocolVersion());
               sourceRequest.setUri(defaultHttpRequest.getUri());
               sourceRequest.setInboundChannelHandlerContext(ctx);
               sourceRequest.setBootstrap(bootstrap);
               sourceRequest.setPipe(new Pipe("sourcePipe"));
               requestList.add(sourceRequest);
               sourceConfiguration.getWorkerPool().execute(new RequestWorker(targetHandler,sourceRequest,this,sourceConfiguration));
           }else if(msg instanceof DefaultHttpContent){
               if(requestList.get(0) != null){
                   DefaultHttpContent defaultHttpContent = (DefaultHttpContent)msg;
                //  requestList.get(0).getPipe().writeContent(defaultHttpContent);
                   requestList.get(0).getPipe().addContent(defaultHttpContent);

               }else{
                   logger.error("Cannot correlate source request with content");
               }
           }else if(msg instanceof LastHttpContent){
               if(requestList.get(0) != null){
                   LastHttpContent defaultLastHttpContent = (LastHttpContent)msg;
                   HttpHeaders trailingHeaders = defaultLastHttpContent.trailingHeaders();
                   for (String val : trailingHeaders.names()) {
                       requestList.get(0).getPipe().addTrailingHeader(val,trailingHeaders.get(val));
                   }
                   requestList.get(0).getPipe().addContent(defaultLastHttpContent);
                  requestList.remove(0);
               }else{
                   logger.error("Cannot correlate source request with content");
               }
           }else{
              logger.error("Request is not a HttpRequest");
           }


    }
    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    public void setBootstrap(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    public ChannelFuture getChannelFuture() {
        return channelFuture;
    }

    public void setChannelFuture(ChannelFuture channelFuture) {
        this.channelFuture = channelFuture;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }
}
