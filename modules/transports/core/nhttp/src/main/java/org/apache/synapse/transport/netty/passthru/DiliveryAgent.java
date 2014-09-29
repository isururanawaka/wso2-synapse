package org.apache.synapse.transport.netty.passthru;


import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.context.MessageContext;
import org.apache.commons.io.IOUtils;
import org.apache.synapse.transport.nhttp.NhttpConstants;
import org.apache.synapse.transport.passthru.Pipe;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;


public class DiliveryAgent {



public  void submitRequest(final MessageContext context, EndpointReference endpointReference, final RequestWorker requestWorker){

 final  SourceHandler sourceHandler = (SourceHandler) context.getProperty(Constants.SOURCE_HANDLER);
    URL url=null;
    URI uri=null;

    try {
      url  = new URL(endpointReference.getAddress());
        uri = url.toURI();
    } catch (MalformedURLException e) {

    } catch (URISyntaxException e) {

    }

   final FullHttpRequest fullHttpRequest = createHttpRequest(context, uri.toString());
   if(sourceHandler.getChannelFuture()==null){
       Bootstrap b = sourceHandler.getBootstrap();
       ChannelFuture future = b.connect(url.getHost(), url.getPort());
       final Channel outboundChannel = future.channel();

       future.addListener(new ChannelFutureListener() {

                              public void operationComplete(ChannelFuture future) throws Exception {
                                  if (future.isSuccess()) {
                                      // connection complete start to read first data
                                      requestWorker.getTargetHandler().setMessageContext(context);
                                      outboundChannel.writeAndFlush(fullHttpRequest);
                                     sourceHandler.setChannelFuture(future);
                                      sourceHandler.setChannel(outboundChannel);
                                  } else {
                                      // Close the connection if the connection attempt has failed.
                                     outboundChannel.close();
                                  }
                              }
                          });

   }else{
       ChannelFuture future = sourceHandler.getChannelFuture();
       if(future.isSuccess() && sourceHandler.getChannel().isActive()){
           requestWorker.getTargetHandler().setMessageContext(context);
         sourceHandler.getChannel().writeAndFlush(fullHttpRequest);
       }else{
           Bootstrap b = sourceHandler.getBootstrap();
          final  ChannelFuture  futuretwo = b.connect(url.getHost(), url.getPort());
           final Channel outboundChannel = futuretwo.channel();
           futuretwo.addListener(new ChannelFutureListener() {

               public void operationComplete(ChannelFuture future) throws Exception {
                   if (futuretwo.isSuccess()) {
                       // connection complete start to read first data
                       requestWorker.getTargetHandler().setMessageContext(context);
                       outboundChannel.writeAndFlush(fullHttpRequest);
                       sourceHandler.setChannelFuture(future);
                   } else {
                       // Close the connection if the connection attempt has failed.
                       outboundChannel.close();
                   }
               }
           });
       }

       //todo need to handle error connections
   }
 }

public  void submitResponse(MessageContext context){
    SOAPEnvelope envelope = context.getEnvelope();
    String contentType = (String) (context.getProperty(Constants.CONTENT_TYPE));
    ChannelHandlerContext channelHandlerContext = (ChannelHandlerContext) context.getProperty(Constants.CHANNEL_HANDLER_CONTEXT);
    if (envelope.getBody().getFirstElement() == null) {
      if(context.getProperty(Constants.CONTENT_RES_BUFFER)!=null){
            byte[] bytes= (byte[]) context.getProperty(Constants.CONTENT_RES_BUFFER);
          FullHttpResponse httpResponse =  getHttpResponseFrombyte(bytes,contentType);

          channelHandlerContext.writeAndFlush(httpResponse);

        }


    }
    else {
        FullHttpResponse fullHttpResponse = getHttpResponse(envelope, contentType);
        //Send the envelope using the ChannelHandlerContext
        channelHandlerContext.writeAndFlush(fullHttpResponse);
    }


}

private FullHttpRequest createHttpRequest(MessageContext context,String uri){
     byte[] byteContent = (byte[]) context.getProperty(Constants.CONTENT_BUFFER);
     ByteBuf  content = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer(byteContent));
     Map headers = (Map) context.getProperty(MessageContext.TRANSPORT_HEADERS);

     Map trailingHeadrs = (Map) context.getProperty(NhttpConstants.EXCESS_TRANSPORT_HEADERS);
     String httpMethod = (String) context.getProperty(org.apache.axis2.Constants.Configuration.HTTP_METHOD);
     HttpMethod httpMethod1 = new HttpMethod(httpMethod);

     String httpVersion = (String) context.getProperty(Constants.HTTP_VERSION);
     HttpVersion httpVersion1 = new HttpVersion(httpVersion,true);


     FullHttpRequest request = new DefaultFullHttpRequest(httpVersion1,httpMethod1,uri,content);

      if(headers != null) {
          Iterator iterator = headers.keySet().iterator();
          while (iterator.hasNext()) {
              String key = (String) iterator.next();
              request.headers().add(key, headers.get(key));
          }
      }
    if(trailingHeadrs != null){
        Iterator iterator = trailingHeadrs.keySet().iterator();
        while (iterator.hasNext()) {
            String key = (String) iterator.next();
            request.trailingHeaders().add(key, trailingHeadrs.get(key));
        }
    }
   return request;
}
    private FullHttpResponse getHttpResponse(SOAPEnvelope soapEnvelope, String ContentType) {
        byte[] bytes = soapEnvelope.toString().getBytes();
        ByteBuf CONTENT = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer(bytes));
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, CONTENT.duplicate());
        response.headers().set(CONTENT_TYPE, ContentType);
        response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        return response;
    }

    private FullHttpResponse getHttpResponseFrombyte(byte[] bytes, String ContentType) {

        ByteBuf CONTENT = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer(bytes));
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, CONTENT.duplicate());
        response.headers().set(CONTENT_TYPE, ContentType);
        response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        return response;

    }


}
