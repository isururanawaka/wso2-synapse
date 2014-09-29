package org.apache.synapse.transport.netty.passthru;


import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import org.apache.synapse.transport.netty.passthru.config.SourceConfiguration;

public class SourceHandler extends ChannelInboundHandlerAdapter {
    private SourceConfiguration sourceConfiguration;
    private Bootstrap bootstrap;
    private ChannelFuture channelFuture;
    private TargetHandler  targetHandler;
    private Channel channel;





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

    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Request sourceRequest = new Request();
        if (msg instanceof DefaultFullHttpRequest) {
            FullHttpRequest fullHttpRequest = (FullHttpRequest) msg;
            HttpHeaders headers = fullHttpRequest.headers();
            for (String val : headers.names()) {
                sourceRequest.addHttpheaders(val, headers.get(val));
            }
            sourceRequest.setTo(fullHttpRequest.getUri());
            ByteBuf buf = fullHttpRequest.content();
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            sourceRequest.setContentBytes(bytes);
            HttpHeaders trailingHeaders = fullHttpRequest.trailingHeaders();
            for (String val : trailingHeaders.names()) {
                sourceRequest.addHttpTrailingheaders(val, trailingHeaders.get(val));
            }
            sourceRequest.setHttpMethod(fullHttpRequest.getMethod());
            sourceRequest.setHttpVersion(fullHttpRequest.getProtocolVersion());
            sourceRequest.setUri(fullHttpRequest.getUri());
        }
        sourceRequest.setInboundChannelHandlerContext(ctx);
        sourceRequest.setBootstrap(bootstrap);
        sourceConfiguration.getWorkerPool().execute(new RequestWorker(targetHandler,sourceRequest,this,sourceConfiguration));
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
