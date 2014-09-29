package org.apache.synapse.transport.netty.passthru;


import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import org.apache.axis2.context.MessageContext;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.synapse.transport.netty.passthru.config.SourceConfiguration;

public class TargetHandler extends ChannelInboundHandlerAdapter {

private Channel inboundChannel;
private SourceConfiguration sourceConfiguration;
private MessageContext messageContext;


public TargetHandler(SourceConfiguration sourceConfiguration, Channel inboundChannel){
    this.inboundChannel = inboundChannel;
    this.sourceConfiguration=sourceConfiguration;
}




    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Response response = new Response();
      if(msg instanceof FullHttpResponse){
          FullHttpResponse fullHttpResponse = (FullHttpResponse) msg;
          HttpHeaders headers = fullHttpResponse.headers();
          for (String val : headers.names()) {
              response.addHttpheaders(val, headers.get(val));
          }

          ByteBuf buf = fullHttpResponse.content();
          byte[] bytes = new byte[buf.readableBytes()];
          buf.readBytes(bytes);
          response.setContentBytes(bytes);
          HttpHeaders trailingHeaders = fullHttpResponse.trailingHeaders();
          for (String val : trailingHeaders.names()) {
              response.addHttpTrailingheaders(val, trailingHeaders.get(val));
          }
         response.setStatus(fullHttpResponse.getStatus().code());
          response.setStatusLine(fullHttpResponse.getStatus().toString());
      }

        sourceConfiguration.getWorkerPool().execute(new ResponseWorker(messageContext,response,sourceConfiguration));




    }

    public MessageContext getMessageContext() {
        return messageContext;
    }

    public void setMessageContext(MessageContext messageContext) {
        this.messageContext = messageContext;
    }

}
