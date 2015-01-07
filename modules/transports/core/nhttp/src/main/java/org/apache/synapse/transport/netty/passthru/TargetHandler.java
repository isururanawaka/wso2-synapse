package org.apache.synapse.transport.netty.passthru;


import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import org.apache.axis2.context.MessageContext;
import org.apache.log4j.Logger;
import org.apache.synapse.transport.netty.passthru.config.SourceConfiguration;

import java.util.ArrayList;
import java.util.List;

public class TargetHandler extends ChannelInboundHandlerAdapter {

    private static Logger logger = Logger.getLogger(SourceHandler.class);
private Channel inboundChannel;
private SourceConfiguration sourceConfiguration;
private MessageContext messageContext;

    private List<Response> responseList = new ArrayList<Response>();

    int count=0;

public TargetHandler(SourceConfiguration sourceConfiguration, Channel inboundChannel){
    this.inboundChannel = inboundChannel;
    this.sourceConfiguration=sourceConfiguration;
}

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);

    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

//      if(msg instanceof FullHttpResponse){
//          FullHttpResponse fullHttpResponse = (FullHttpResponse) msg;
//          HttpHeaders headers = fullHttpResponse.headers();
//          for (String val : headers.names()) {
//              response.addHttpheaders(val, headers.get(val));
//          }
//
//          ByteBuf buf = fullHttpResponse.content();
//          byte[] bytes = new byte[buf.readableBytes()];
//          buf.readBytes(bytes);
//          response.setContentBytes(bytes);
//          HttpHeaders trailingHeaders = fullHttpResponse.trailingHeaders();
//          for (String val : trailingHeaders.names()) {
//              response.addHttpTrailingheaders(val, trailingHeaders.get(val));
//          }
//         response.setStatus(fullHttpResponse.getStatus().code());
//          response.setStatusLine(fullHttpResponse.getStatus().toString());
//      }
        if(msg instanceof HttpResponse){
            Response response = new Response();
            HttpResponse defaultHttpResponse = (HttpResponse) msg;
            HttpHeaders headers = defaultHttpResponse.headers();
            for (String val : headers.names()) {
                response.addHttpheaders(val, headers.get(val));
            }
            response.setStatus(defaultHttpResponse.getStatus().code());
            response.setStatusLine(defaultHttpResponse.getStatus().toString());
            response.setPipe(new Pipe("TargetPipe"));
            responseList.add(response);
            sourceConfiguration.getWorkerPool().execute(new ResponseWorker(messageContext,response,sourceConfiguration));
        }else if(msg instanceof HttpContent){
            if(responseList.get(0) != null){
                if(msg instanceof LastHttpContent){
                    LastHttpContent defaultLastHttpContent = (LastHttpContent)msg;
//                HttpHeaders trailingHeaders = defaultLastHttpContent.trailingHeaders();
//                for (String val : trailingHeaders.names()) {
//                    responseList.get(0).getPipe().addTrailingHeader(val,trailingHeaders.get(val));
//                }
                    responseList.get(0).getPipe().addContent(defaultLastHttpContent);
                    responseList.remove(0);
                }else {
                    DefaultHttpContent defaultHttpContent = (DefaultHttpContent) msg;
                    //  responseList.get(0).getPipe().writeContent(defaultHttpContent);
                    responseList.get(0).getPipe().addContent(defaultHttpContent);
                }

            }
        }
    //    sourceConfiguration.getWorkerPool().execute(new ResponseWorker(messageContext,response,sourceConfiguration));

    }

    public MessageContext getMessageContext() {
        return messageContext;
    }

    public void setMessageContext(MessageContext messageContext) {
        this.messageContext = messageContext;
    }

}
