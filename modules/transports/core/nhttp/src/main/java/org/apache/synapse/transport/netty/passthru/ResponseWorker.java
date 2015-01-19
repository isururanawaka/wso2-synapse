package org.apache.synapse.transport.netty.passthru;


import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axiom.soap.impl.llom.soap11.SOAP11Factory;
import org.apache.axis2.*;
import org.apache.axis2.addressing.AddressingConstants;
import org.apache.axis2.builder.BuilderUtil;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.description.WSDL2Constants;
import org.apache.axis2.engine.AxisEngine;
import org.apache.axis2.wsdl.WSDLConstants;
import org.apache.http.HttpStatus;
import org.apache.http.protocol.HTTP;
import org.apache.log4j.Logger;
import org.apache.synapse.transport.netty.passthru.config.SourceConfiguration;
import org.apache.synapse.transport.nhttp.NhttpConstants;
import org.apache.synapse.transport.passthru.PassThroughConstants;
import org.apache.synapse.transport.passthru.SourceRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class ResponseWorker implements Runnable {
    private Response sourceRes;
    private SourceHandler sourceHandler;
    private SourceConfiguration sourceConfiguration;
    private MessageContext outmessageContext;
    private MessageContext responseMsgCtx;
    private boolean expectEntityBody = true;
    private static Logger log = Logger.getLogger(ResponseWorker.class);

    public ResponseWorker(MessageContext messageContext, Response sourceResponse,
                          SourceConfiguration sourceConfiguration) {
        this.sourceRes = sourceResponse;
        this.sourceConfiguration = sourceConfiguration;
        this.outmessageContext = messageContext;

        Map<String, String> headers = sourceResponse.getHttpheaders();
        Map excessHeaders = sourceResponse.getHttptrailingHeaders();

        String oriURL = headers.get(PassThroughConstants.LOCATION);
        if (oriURL != null && ((sourceRes.getStatus() != HttpStatus.SC_MOVED_TEMPORARILY) &&
                               (sourceRes.getStatus() != HttpStatus.SC_MOVED_PERMANENTLY) &&
                               (sourceRes.getStatus() != HttpStatus.SC_CREATED) &&
                               (sourceRes.getStatus() != HttpStatus.SC_SEE_OTHER) &&
                               (sourceRes.getStatus() != HttpStatus.SC_TEMPORARY_REDIRECT))) {
            URL url;
            try {
                url = new URL(oriURL);
            } catch (MalformedURLException e) {
                log.error("Invalid URL received", e);
                return;
            }

            headers.remove(PassThroughConstants.LOCATION);
            String prfix = (String) outmessageContext.getProperty(PassThroughConstants.SERVICE_PREFIX);
            if (prfix != null) {
                headers.put(PassThroughConstants.LOCATION, prfix + url.getFile());
            }

        }


        try {
            responseMsgCtx = outmessageContext.getOperationContext().
                       getMessageContext(WSDL2Constants.MESSAGE_LABEL_IN);
            // fix for RM to work because of a soapAction and wsaAction conflict
            if (responseMsgCtx != null) {
                responseMsgCtx.setSoapAction("");
            }
        } catch (AxisFault af) {
            log.error("Error getting IN message context from the operation context", af);
            return;
        }

        if (responseMsgCtx == null) {
            if (outmessageContext.getOperationContext().isComplete()) {
                if (log.isDebugEnabled()) {
                    log.debug("Error getting IN message context from the operation context. " +
                              "Possibly an RM terminate sequence message");
                }
                return;

            }
            responseMsgCtx = new MessageContext();
            responseMsgCtx.setOperationContext(outmessageContext.getOperationContext());
        }
        responseMsgCtx.setProperty("PRE_LOCATION_HEADER", oriURL);


        responseMsgCtx.setServerSide(true);
        responseMsgCtx.setDoingREST(outmessageContext.isDoingREST());
        responseMsgCtx.setProperty(MessageContext.TRANSPORT_IN, outmessageContext
                   .getProperty(MessageContext.TRANSPORT_IN));
        responseMsgCtx.setTransportIn(outmessageContext.getTransportIn());
        responseMsgCtx.setTransportOut(outmessageContext.getTransportOut());

        //setting the responseMsgCtx PassThroughConstants.INVOKED_REST property to the one set inside PassThroughTransportUtils
        responseMsgCtx.setProperty(PassThroughConstants.INVOKED_REST, outmessageContext.isDoingREST());

        // set any transport headers received
        Set<Map.Entry<String, String>> headerEntries = sourceResponse.getHttpheaders().entrySet();
        Map<String, String> headerMap = new TreeMap<String, String>(new Comparator<String>() {
            public int compare(String o1, String o2) {
                return o1.compareToIgnoreCase(o2);
            }
        });

        for (Map.Entry<String, String> headerEntry : headerEntries) {
            headerMap.put(headerEntry.getKey(), headerEntry.getValue());
        }
        responseMsgCtx.setProperty(MessageContext.TRANSPORT_HEADERS, headerMap);
        responseMsgCtx.setProperty(NhttpConstants.EXCESS_TRANSPORT_HEADERS, excessHeaders);

        if (sourceResponse.getStatus() == 202) {
            responseMsgCtx.setProperty(AddressingConstants.
                                                  DISABLE_ADDRESSING_FOR_OUT_MESSAGES, Boolean.TRUE);
            responseMsgCtx.setProperty(PassThroughConstants.MESSAGE_BUILDER_INVOKED, Boolean.FALSE);
            responseMsgCtx.setProperty(NhttpConstants.SC_ACCEPTED, Boolean.TRUE);
        }

        responseMsgCtx.setAxisMessage(outmessageContext.getOperationContext().getAxisOperation().
                   getMessage(WSDLConstants.MESSAGE_LABEL_IN_VALUE));
        responseMsgCtx.setOperationContext(outmessageContext.getOperationContext());
        responseMsgCtx.setConfigurationContext(outmessageContext.getConfigurationContext());
        responseMsgCtx.setTo(null);

        responseMsgCtx.setProperty(Constants.CONTENT_RES_BUFFER, sourceResponse.getContentBytes());

        responseMsgCtx.setProperty(Constants.CHANNEL_HANDLER_CONTEXT, outmessageContext.getProperty(Constants.CHANNEL_HANDLER_CONTEXT));

    }


    public void run() {
        if (responseMsgCtx == null) {
            return;
        }

        try {
            if (outmessageContext.getProperty(Constants.REQUEST) != null) {
                SourceRequest request = (SourceRequest) outmessageContext.getProperty(Constants.REQUEST);
                expectEntityBody = isResponseHaveBodyExpected(request.getMethod(), sourceRes.getStatus());
            }

            if (expectEntityBody) {
                String cType = sourceRes.getHeader(HTTP.CONTENT_TYPE);
                if (cType == null) {
                    cType = sourceRes.getHeader(HTTP.CONTENT_TYPE.toLowerCase());
                }
                String contentType;
                if (cType != null) {
                    // This is the most common case - Most of the time servers send the Content-Type
                    contentType = cType;
                } else {
                    // Server hasn't sent the header - Try to infer the content type
                    contentType = inferContentType();
                }

                responseMsgCtx.setProperty(org.apache.axis2.Constants.Configuration.CONTENT_TYPE, contentType);

                String charSetEnc = BuilderUtil.getCharSetEncoding(contentType);
                if (charSetEnc == null) {
                    charSetEnc = MessageContext.DEFAULT_CHAR_SET_ENCODING;
                }
                if (contentType != null) {
                    responseMsgCtx.setProperty(
                               org.apache.axis2.Constants.Configuration.CHARACTER_SET_ENCODING,
                               contentType.indexOf("charset") > 0 ?
                               charSetEnc : MessageContext.DEFAULT_CHAR_SET_ENCODING);
                }

                responseMsgCtx.setServerSide(false);
                SOAPFactory fac = OMAbstractFactory.getSOAP11Factory();
                SOAPEnvelope envelope = fac.getDefaultEnvelope();
                try {
                    responseMsgCtx.setEnvelope(envelope);
                } catch (AxisFault axisFault) {
                    log.error("Error setting SOAP envelope", axisFault);
                }

                responseMsgCtx.setServerSide(true);
            } else {
                // there is no response entity-body
                responseMsgCtx.setProperty(PassThroughConstants.NO_ENTITY_BODY, Boolean.TRUE);
                responseMsgCtx.setEnvelope(new SOAP11Factory().getDefaultEnvelope());
            }

            // copy the HTTP status code as a message context property with the key HTTP_SC to be
            // used at the sender to set the proper status code when passing the message
            int statusCode = this.sourceRes.getStatus();
            responseMsgCtx.setProperty(PassThroughConstants.HTTP_SC, statusCode);
            responseMsgCtx.setProperty(PassThroughConstants.HTTP_SC_DESC, sourceRes.getStatusLine());
            if (statusCode >= 400) {
                responseMsgCtx.setProperty(PassThroughConstants.FAULT_MESSAGE,
                                           PassThroughConstants.TRUE);
            } /*else if (statusCode == 202 && responseMsgCtx.getOperationContext().isComplete()) {
                // Handle out-only invocation scenario
                responseMsgCtx.setProperty(PassThroughConstants.MESSAGE_BUILDER_INVOKED, Boolean.TRUE);
            }*/
            responseMsgCtx.setProperty(PassThroughConstants.NON_BLOCKING_TRANSPORT, true);
            responseMsgCtx.setProperty(org.apache.synapse.transport.netty.passthru.Constants.PIPE, sourceRes.getPipe());
            // process response received
            try {
                AxisEngine.receive(responseMsgCtx);
            } catch (AxisFault af) {
                log.error("Fault processing response message through Axis2", af);
            }

        } catch (AxisFault af) {
            log.error("Fault creating response SOAP envelope", af);
        }
    }

    private boolean isResponseHaveBodyExpected(
               final String method, int status) {

        if ("HEAD".equalsIgnoreCase(method)) {
            return false;
        }


        return status >= HttpStatus.SC_OK
               && status != HttpStatus.SC_NO_CONTENT
               && status != HttpStatus.SC_NOT_MODIFIED
               && status != HttpStatus.SC_RESET_CONTENT;
    }

    private String inferContentType() {
        //Check whether server sent Content-Type in different case
        Map<String, String> headers = sourceRes.getHttpheaders();
        for (String header : headers.keySet()) {
            if (HTTP.CONTENT_TYPE.equalsIgnoreCase(header)) {
                return headers.get(header);
            }
        }
        String cType = sourceRes.getHeader("content-type");
        if (cType != null) {
            return cType;
        }
        cType = sourceRes.getHeader("Content-type");
        if (cType != null) {
            return cType;
        }

        // Try to get the content type from the message context
        Object cTypeProperty = responseMsgCtx.getProperty(PassThroughConstants.CONTENT_TYPE);
        if (cTypeProperty != null) {
            return cTypeProperty.toString();
        }
        // Try to get the content type from the axis configuration
        Parameter cTypeParam = outmessageContext.getConfigurationContext().getAxisConfiguration().getParameter(
                   PassThroughConstants.CONTENT_TYPE);
        if (cTypeParam != null) {
            return cTypeParam.getValue().toString();
        }

        // When the response from backend does not have the body(Content-Length is 0 )
        // and Content-Type is not set; ESB should not do any modification to the response and pass-through as it is.

        if (headers != null && headers.get(PassThroughConstants.HTTP_CONTENT_LENGTH).toString().equals("0") &&
            sourceRes.getHeader(PassThroughConstants.HTTP_CONTENT_TYPE) == null) {
            if (log.isDebugEnabled()) {
                log.debug("Content-Length is Zero and Content-type is not available in the response ");
            }
            return null;
        }

        // Unable to determine the content type - Return default value
        return PassThroughConstants.DEFAULT_CONTENT_TYPE;
    }
}
