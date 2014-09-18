package org.apache.synapse.transport.netty.passthru;


import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.apache.synapse.transport.netty.passthru.config.SourceConfiguration;

public class SourceHandler extends ChannelInboundHandlerAdapter {
    private SourceConfiguration sourceConfiguration;



    protected  SourceHandler(SourceConfiguration sourceConfiguration){
        this.sourceConfiguration=sourceConfiguration;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
    }

}
