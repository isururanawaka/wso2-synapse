package org.apache.synapse.transport.netty.passthru;


import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.apache.synapse.transport.netty.passthru.config.SourceConfiguration;

public class TargetChannelinitializer extends ChannelInitializer<SocketChannel> {

    private TargetHandler targetHandler;

    public TargetChannelinitializer(TargetHandler targetHandler){
        this.targetHandler =targetHandler;
    }


    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline p = ch.pipeline();
        p.addLast("codec", new HttpClientCodec());
       // p.addLast(Constants.HTTP_CODEC,new HttpServerCodec());
//        p.addLast(Constants.HTTP_AGREGRATOR, new HttpObjectAggregator(Integer.MAX_VALUE));
        p.addLast(Constants.HANDLER, targetHandler);

    }
}
