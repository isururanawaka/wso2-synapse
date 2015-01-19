package org.apache.synapse.transport.netty.passthru;


import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.context.MessageContext;

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


    public void submitRequest(final MessageContext context, EndpointReference endpointReference,
                              final RequestWorker requestWorker) {

        final SourceHandler sourceHandler = (SourceHandler) context.getProperty(Constants.SOURCE_HANDLER);
        URL url = null;
        URI uri = null;

        try {
            url = new URL(endpointReference.getAddress());
            uri = url.toURI();
        } catch (MalformedURLException e) {

        } catch (URISyntaxException e) {

        }

        // final FullHttpRequest fullHttpRequest = createFullHttpRequest(context, uri.toString());
        final HttpRequest httpRequest = createHttpRequest(context, uri.toString());
        if (sourceHandler.getChannelFuture() == null) {
            Bootstrap b = sourceHandler.getBootstrap();
            ChannelFuture future = b.connect(url.getHost(), url.getPort());
            final Channel outboundChannel = future.channel();

            future.addListener(new ChannelFutureListener() {

                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        // connection complete start to read first data
                        requestWorker.getTargetHandler().setMessageContext(context);

                        //    outboundChannel.writeAndFlush(fullHttpRequest);
                        outboundChannel.write(httpRequest);
                        Object obj = context.getProperty(Constants.PIPE);
                        Pipe pipe = null;
                        if (obj != null && obj instanceof Pipe) {
                            pipe = (Pipe) obj;
                        }
                        while (true) {
                            HttpContent defaultHttpContent = pipe.getContent();
                            if (defaultHttpContent instanceof LastHttpContent) {
                                outboundChannel.writeAndFlush(defaultHttpContent);
                                break;
                            }
                            if (defaultHttpContent != null) {
                                outboundChannel.write(defaultHttpContent);
                            }
                        }
                        sourceHandler.setChannelFuture(future);
                        sourceHandler.setChannel(outboundChannel);
                    } else {
                        // Close the connection if the connection attempt has failed.
                        outboundChannel.close();
                    }
                }
            });

        } else {
            ChannelFuture future = sourceHandler.getChannelFuture();
            if (future.isSuccess() && sourceHandler.getChannel().isActive()) {
                requestWorker.getTargetHandler().setMessageContext(context);

                //  sourceHandler.getChannel().writeAndFlush(fullHttpRequest);
                sourceHandler.getChannel().write(httpRequest);

                Object obj = context.getProperty(Constants.PIPE);
                Pipe pipe = null;
                if (obj != null && obj instanceof Pipe) {
                    pipe = (Pipe) obj;
                }
                while (true) {
                    HttpContent defaultHttpContent = pipe.getContent();
                    if (defaultHttpContent instanceof LastHttpContent) {
                        sourceHandler.getChannel().writeAndFlush(defaultHttpContent);
                        break;
                    }
                    sourceHandler.getChannel().write(defaultHttpContent);
                }

            } else {
                Bootstrap b = sourceHandler.getBootstrap();
                final ChannelFuture futuretwo = b.connect(url.getHost(), url.getPort());
                final Channel outboundChannel = futuretwo.channel();
                futuretwo.addListener(new ChannelFutureListener() {

                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (futuretwo.isSuccess()) {
                            // connection complete start to read first data
                            requestWorker.getTargetHandler().setMessageContext(context);

                            //     outboundChannel.writeAndFlush(fullHttpRequest);
                            outboundChannel.write(httpRequest);

                            Object obj = context.getProperty(Constants.PIPE);
                            Pipe pipe = null;
                            if (obj != null && obj instanceof Pipe) {
                                pipe = (Pipe) obj;
                            }
                            while (true) {
                                HttpContent defaultHttpContent = pipe.getContent();
                                if (defaultHttpContent instanceof LastHttpContent) {
                                    outboundChannel.writeAndFlush(defaultHttpContent);
                                    break;
                                }
                                outboundChannel.write(defaultHttpContent);
                            }
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

    public void submitResponse(MessageContext context) {
        SOAPEnvelope envelope = context.getEnvelope();
        String contentType = (String) (context.getProperty(Constants.CONTENT_TYPE));
        ChannelHandlerContext channelHandlerContext = (ChannelHandlerContext) context.getProperty(Constants.CHANNEL_HANDLER_CONTEXT);
        if (envelope.getBody().getFirstElement() == null) {

            writeResponse(channelHandlerContext, context, contentType);
            //     channelHandlerContext.writeAndFlush(httpResponse);

        } else {
            FullHttpResponse fullHttpResponse = getHttpResponse(envelope, contentType);
            //Send the envelope using the ChannelHandlerContext
            channelHandlerContext.writeAndFlush(fullHttpResponse);
        }


    }

    private FullHttpRequest createFullHttpRequest(MessageContext context, String uri) {
        Object obj = context.getProperty(Constants.PIPE);
        Pipe pipe = null;
        ByteBuf content = null;
        Map trailingHeadrs = null;
        if (obj != null && obj instanceof Pipe) {
            pipe = (Pipe) obj;
        }

        if (pipe != null) {

            content = null;
            while (true) {
                HttpContent httpContent = pipe.getContent();
                if (httpContent instanceof DefaultHttpContent) {
                    ByteBuf byteBuf = ((DefaultHttpContent) httpContent).content();
                    byte[] bytes = new byte[byteBuf.readableBytes()];
                    byteBuf.readBytes(bytes);
                    content = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer(bytes));
                    // content.writeBytes(bytes);
                } else if (httpContent instanceof LastHttpContent) {
                    trailingHeadrs = pipe.getTrailingheaderMap();
                    break;
                }
            }
        }
        Map headers = (Map) context.getProperty(MessageContext.TRANSPORT_HEADERS);


        String httpMethod = (String) context.getProperty(org.apache.axis2.Constants.Configuration.HTTP_METHOD);
        HttpMethod httpMethod1 = new HttpMethod(httpMethod);

        String httpVersion = (String) context.getProperty(Constants.HTTP_VERSION);
        HttpVersion httpVersion1 = new HttpVersion(httpVersion, true);


        FullHttpRequest request = new DefaultFullHttpRequest(httpVersion1, httpMethod1, uri, content);


        if (headers != null) {
            Iterator iterator = headers.keySet().iterator();
            while (iterator.hasNext()) {
                String key = (String) iterator.next();
                request.headers().add(key, headers.get(key));
            }
        }
        if (trailingHeadrs != null) {
            Iterator iterator = trailingHeadrs.keySet().iterator();
            while (iterator.hasNext()) {
                String key = (String) iterator.next();
                request.trailingHeaders().add(key, trailingHeadrs.get(key));
            }
        }
        return request;
    }

    private HttpRequest createHttpRequest(MessageContext context, String uri) {

        Map headers = (Map) context.getProperty(MessageContext.TRANSPORT_HEADERS);


        String httpMethod = (String) context.getProperty(org.apache.axis2.Constants.Configuration.HTTP_METHOD);
        HttpMethod httpMethod1 = new HttpMethod(httpMethod);

        String httpVersion = (String) context.getProperty(Constants.HTTP_VERSION);
        HttpVersion httpVersion1 = new HttpVersion(httpVersion, true);


        HttpRequest request = new DefaultHttpRequest(httpVersion1, httpMethod1, uri);


        if (headers != null) {
            Iterator iterator = headers.keySet().iterator();
            while (iterator.hasNext()) {
                String key = (String) iterator.next();
                request.headers().add(key, headers.get(key));
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

    private FullHttpResponse getHttpResponseFrombyte(MessageContext context, String ContentType) {
        Pipe pipe = (Pipe) context.getProperty(Constants.PIPE);
        ByteBuf content = Unpooled.unreleasableBuffer(Unpooled.buffer());
        ;
        while (true) {
            HttpContent httpContent = pipe.getContent();
            if (httpContent instanceof DefaultHttpContent) {
                ByteBuf byteBuf = ((DefaultHttpContent) httpContent).content();
                byte[] bytes = new byte[byteBuf.readableBytes()];
                byteBuf.readBytes(bytes);
                content.writeBytes(bytes);
                // content.writeBytes(bytes);
            } else if (httpContent instanceof LastHttpContent) {
                //    trailingHeadrs = pipe.getTrailingheaderMap();
                break;
            }
        }
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, content);
        response.headers().set(CONTENT_TYPE, ContentType);
        response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        return response;

    }

    private void writeResponse(ChannelHandlerContext channelHandlerContext, MessageContext context,
                               String ContentType) {
        Pipe pipe = (Pipe) context.getProperty(Constants.PIPE);
        ByteBuf content = Unpooled.unreleasableBuffer(Unpooled.buffer());
        ;
        DefaultHttpResponse defaultHttpResponse = new DefaultHttpResponse(HTTP_1_1, OK);
        Map headers = (Map) context.getProperty(MessageContext.TRANSPORT_HEADERS);
        if (headers != null) {
            Iterator iterator = headers.keySet().iterator();
            while (iterator.hasNext()) {
                String key = (String) iterator.next();
                defaultHttpResponse.headers().add(key, headers.get(key));
            }
        }
        channelHandlerContext.write(defaultHttpResponse);
        while (true) {
            HttpContent httpContent = pipe.getContent();
            if (httpContent instanceof LastHttpContent || httpContent instanceof DefaultLastHttpContent) {
                //    trailingHeadrs = pipe.getTrailingheaderMap();
                channelHandlerContext.writeAndFlush(httpContent);
                break;
            }
            channelHandlerContext.write(httpContent);
        }

    }


}
