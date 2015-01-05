package org.apache.synapse.transport.netty.passthru;



import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpResponse;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;


public class Response {

    private Map<String, String> httpheaders = new HashMap<String, String>();
    private Map<String, String> httptrailingHeaders = new HashMap<String, String>();

    private int status = HttpStatus.SC_OK;

    private ChannelHandlerContext inboundChannelHandlerContext;

    private FullHttpResponse fullHttpResponse;
    private byte[] contentBytes;

    private String statusLine = "OK";

    private Pipe pipe;

    public Pipe getPipe() {
        return pipe;
    }

    public void setPipe(Pipe pipe) {
        this.pipe = pipe;
    }

    public ChannelHandlerContext getInboundChannelHandlerContext() {
        return inboundChannelHandlerContext;
    }

    public void setInboundChannelHandlerContext(ChannelHandlerContext inboundChannelHandlerContext) {
        this.inboundChannelHandlerContext = inboundChannelHandlerContext;
    }

    public Map<String, String> getHttptrailingHeaders() {
        return httptrailingHeaders;
    }

    public void addHttpTrailingheaders(String key, String value) {
        this.httptrailingHeaders.put(key, value);
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


    public FullHttpResponse getFullHttpResponse() {
        return fullHttpResponse;
    }

    public void setFullHttpResponse(FullHttpResponse fullHttpResponse) {
        this.fullHttpResponse = fullHttpResponse;
    }

    public int getStatus() {
        return status;
    }


    public void addHeader(String name, String value) {

            httpheaders.put(name, value);

    }

    public void setStatus(int status) {
        this.status = status;
    }
    public void removeHeader(String name) {
       httpheaders.remove(name);
    }


    public String getStatusLine() {
        return statusLine;
    }

    public void setStatusLine(String statusLine) {
        this.statusLine = statusLine;
    }
    public String getHeader(String name) {
        return httpheaders.get(name);
    }

}
