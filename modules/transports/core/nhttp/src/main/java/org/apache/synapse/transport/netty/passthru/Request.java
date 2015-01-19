package org.apache.synapse.transport.netty.passthru;


import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;

import java.util.HashMap;
import java.util.Map;

public class Request {
    private Map<String, String> httpheaders = new HashMap<String, String>();

    private ChannelHandlerContext inboundChannelHandlerContext;

    private Bootstrap bootstrap;

    private HttpMethod httpMethod;
    private HttpVersion httpVersion;
    private String uri;


    private FullHttpRequest fullHttpRequest;
    private byte[] contentBytes;


    private String to;
    private String replyTo;

    private Pipe pipe;

    public ChannelHandlerContext getInboundChannelHandlerContext() {
        return inboundChannelHandlerContext;
    }

    public void setInboundChannelHandlerContext(ChannelHandlerContext inboundChannelHandlerContext) {
        this.inboundChannelHandlerContext = inboundChannelHandlerContext;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }


    public Pipe getPipe() {
        return pipe;
    }

    public void setPipe(Pipe pipe) {
        this.pipe = pipe;
    }


    public byte[] getContentBytes() {
        return contentBytes;
    }

    public void setContentBytes(byte[] contentBytes) {
        this.contentBytes = contentBytes;
    }

    public Map<String, String> getHttpheaders() {
        return httpheaders;
    }

    public void addHttpheaders(String key, String value) {
        this.httpheaders.put(key, value);
    }

    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    public void setBootstrap(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public HttpVersion getHttpVersion() {
        return httpVersion;
    }

    public void setHttpVersion(HttpVersion httpVersion) {
        this.httpVersion = httpVersion;
    }

    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(HttpMethod httpMethod) {
        this.httpMethod = httpMethod;
    }

    public FullHttpRequest getFullHttpRequest() {
        return fullHttpRequest;
    }

    public void setFullHttpRequest(FullHttpRequest fullHttpRequest) {
        this.fullHttpRequest = fullHttpRequest;
    }
}
