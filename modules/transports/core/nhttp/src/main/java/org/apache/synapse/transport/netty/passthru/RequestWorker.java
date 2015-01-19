package org.apache.synapse.transport.netty.passthru;


import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axiom.util.UIDGenerator;
import org.apache.axis2.*;
import org.apache.axis2.Constants;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.builder.BuilderUtil;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.engine.AxisEngine;
import org.apache.axis2.transport.RequestResponseTransport;
import org.apache.axis2.transport.TransportUtils;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.axis2.transport.http.HTTPTransportUtils;
import org.apache.http.protocol.HTTP;
import org.apache.log4j.Logger;
import org.apache.synapse.transport.netty.passthru.config.SourceConfiguration;
import org.apache.synapse.transport.nhttp.HttpCoreRequestResponseTransport;
import org.apache.synapse.transport.nhttp.NhttpConstants;
import org.apache.synapse.transport.nhttp.util.NhttpUtil;
import org.apache.synapse.transport.passthru.PassThroughConstants;


import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;


public class RequestWorker implements Runnable {

    private Request sourceRequest;
    private SourceHandler sourceHandler;
    private SourceConfiguration sourceConfiguration;
    private MessageContext messageContext;
    private TargetHandler targetHandler;
    private static Logger log = Logger.getLogger(RequestWorker.class);

    public RequestWorker(TargetHandler targetHandler, Request sourceRequest, SourceHandler sourceHandler,
                         SourceConfiguration sourceConfiguration) {
        this.sourceRequest = sourceRequest;
        this.sourceHandler = sourceHandler;
        this.sourceConfiguration = sourceConfiguration;
        this.messageContext = createMessageContext(sourceRequest);
        this.targetHandler = targetHandler;
    }


    public void run() {

        ConfigurationContext cfgCtx = sourceConfiguration.getConfigurationContext();
        messageContext.setProperty(Constants.Configuration.HTTP_METHOD, sourceRequest.getHttpMethod().toString());

        String method = sourceRequest.getHttpMethod().toString();

        //String uri = request.getUri();
        String oriUri = sourceRequest.getUri();
        String restUrlPostfix = NhttpUtil.getRestUrlPostfix(oriUri, cfgCtx.getServicePath());
        String servicePrefix = oriUri.substring(0, oriUri.indexOf(restUrlPostfix));


        messageContext.setProperty(PassThroughConstants.SERVICE_PREFIX, servicePrefix);

        messageContext.setTo(new EndpointReference(restUrlPostfix));
        messageContext.setProperty(PassThroughConstants.REST_URL_POSTFIX, restUrlPostfix);

        messageContext.setProperty(org.apache.synapse.transport.netty.passthru.Constants.SOURCE_HANDLER, sourceHandler);
        messageContext.setProperty(org.apache.synapse.transport.netty.passthru.Constants.PIPE, sourceRequest.getPipe());
        messageContext.setProperty(org.apache.synapse.transport.netty.passthru.Constants.HTTP_VERSION, sourceRequest.getHttpVersion().text());
        messageContext.setProperty(org.apache.synapse.transport.netty.passthru.Constants.REQUEST, sourceRequest.getFullHttpRequest());
        processEntityEnclosingRequest();

        //  sendAck();
    }

    private MessageContext createMessageContext(Request request) {
        //   Map excessHeaders = request.getHttptrailingHeaders();
        ConfigurationContext cfgCtx = sourceConfiguration.getConfigurationContext();
        MessageContext msgContext =
                   new MessageContext();
        msgContext.setMessageID(UIDGenerator.generateURNString());

        // Axis2 spawns a new threads to send a message if this is TRUE - and it has to
        // be the other way
        msgContext.setProperty(MessageContext.CLIENT_API_NON_BLOCKING,
                               Boolean.FALSE);
        msgContext.setConfigurationContext(cfgCtx);
        msgContext.setTransportOut(cfgCtx.getAxisConfiguration()
                                              .getTransportOut(org.apache.axis2.Constants.TRANSPORT_HTTP));
        msgContext.setTransportIn(cfgCtx.getAxisConfiguration()
                                             .getTransportIn(org.apache.axis2.Constants.TRANSPORT_HTTP));
        msgContext.setIncomingTransportName(sourceConfiguration.getInDescription() != null ?
                                            sourceConfiguration.getInDescription().getName() : org.apache.axis2.Constants.TRANSPORT_HTTP);
        msgContext.setProperty(org.apache.axis2.Constants.OUT_TRANSPORT_INFO, this);
        msgContext.setServerSide(true);
        msgContext.setProperty(
                   org.apache.axis2.Constants.Configuration.TRANSPORT_IN_URL, request.getUri());
        msgContext.setProperty(org.apache.synapse.transport.netty.passthru.Constants.REQUEST, sourceRequest);

        // http transport header names are case insensitive
        Map<String, String> headers = new TreeMap<String, String>(new Comparator<String>() {
            public int compare(String o1, String o2) {
                return o1.compareToIgnoreCase(o2);
            }
        });

        Set<Map.Entry<String, String>> entries = request.getHttpheaders().entrySet();
        for (Map.Entry<String, String> entry : entries) {
            headers.put(entry.getKey(), entry.getValue());
        }
        msgContext.setProperty(MessageContext.TRANSPORT_HEADERS, headers);
        // msgContext.setProperty(NhttpConstants.EXCESS_TRANSPORT_HEADERS, excessHeaders);
        msgContext.setProperty(RequestResponseTransport.TRANSPORT_CONTROL,
                               new HttpCoreRequestResponseTransport(msgContext));

        msgContext.setProperty(org.apache.synapse.transport.netty.passthru.Constants.CHANNEL_HANDLER_CONTEXT, sourceRequest.getInboundChannelHandlerContext());

        return msgContext;
    }

    private void processEntityEnclosingRequest() {
        try {
            String contentTypeHeader = sourceRequest.getHttpheaders().get(HTTP.CONTENT_TYPE);
            contentTypeHeader = contentTypeHeader != null ? contentTypeHeader : inferContentType();

            String charSetEncoding = null;
            String contentType = null;

            if (contentTypeHeader != null) {
                charSetEncoding = BuilderUtil.getCharSetEncoding(contentTypeHeader);
                contentType = TransportUtils.getContentType(contentTypeHeader, messageContext);
            }
            // get the contentType of char encoding
            if (charSetEncoding == null) {
                charSetEncoding = MessageContext.DEFAULT_CHAR_SET_ENCODING;
            }
            String method = sourceRequest.getHttpMethod().toString();


            messageContext.setTo(new EndpointReference(sourceRequest.getUri()));
            messageContext.setProperty(HTTPConstants.HTTP_METHOD, method);
            messageContext.setProperty(Constants.Configuration.CHARACTER_SET_ENCODING, charSetEncoding);
            messageContext.setServerSide(true);

            messageContext.setProperty(Constants.Configuration.CONTENT_TYPE, contentTypeHeader);
            messageContext.setProperty(Constants.Configuration.MESSAGE_TYPE, contentType);


            String soapAction = sourceRequest.getHttpheaders().get(org.apache.synapse.transport.netty.passthru.Constants.SOAP_ACTION_HEADER);

            int soapVersion = HTTPTransportUtils.
                       initializeMessageContext(messageContext, soapAction,
                                                sourceRequest.getUri(), contentTypeHeader);
            SOAPEnvelope envelope;

            if (soapVersion == 1) {
                SOAPFactory fac = OMAbstractFactory.getSOAP11Factory();
                envelope = fac.getDefaultEnvelope();
            } else if (soapVersion == 2) {
                SOAPFactory fac = OMAbstractFactory.getSOAP12Factory();
                envelope = fac.getDefaultEnvelope();
            } else {
                SOAPFactory fac = OMAbstractFactory.getSOAP12Factory();
                envelope = fac.getDefaultEnvelope();
            }

            messageContext.setEnvelope(envelope);
            AxisEngine.receive(messageContext);
        } catch (AxisFault axisFault) {
            log.error(axisFault.getMessage());
        }
    }

    private String inferContentType() {
        Map<String, String> headers = sourceRequest.getHttpheaders();
        for (String header : headers.keySet()) {
            if (HTTP.CONTENT_TYPE.equalsIgnoreCase(header)) {
                return headers.get(header);
            }
        }
        Parameter param = sourceConfiguration.getConfigurationContext().getAxisConfiguration().
                   getParameter(PassThroughConstants.REQUEST_CONTENT_TYPE);
        if (param != null) {
            return param.getValue().toString();
        }
        return null;
    }

    public TargetHandler getTargetHandler() {
        return targetHandler;
    }

}
