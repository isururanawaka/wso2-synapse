package org.apache.synapse.transport.netty.passthru;


import org.apache.axis2.*;
import org.apache.axis2.Constants;
import org.apache.axis2.addressing.AddressingHelper;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.TransportOutDescription;
import org.apache.axis2.handlers.AbstractHandler;
import org.apache.axis2.transport.TransportSender;
import org.apache.log4j.Logger;
import org.apache.synapse.transport.passthru.PassThroughConstants;
import org.apache.synapse.transport.passthru.util.PassThroughTransportUtils;

public class HttpSender extends AbstractHandler implements TransportSender {
    private static Logger log = Logger.getLogger(HttpSender.class);

    private DiliveryAgent deliveryAgent;


    public InvocationResponse invoke(MessageContext messageContext) throws AxisFault {

        if (AddressingHelper.isReplyRedirected(messageContext)
            && !messageContext.getReplyTo().hasNoneAddress()) {
            messageContext.setProperty(PassThroughConstants.IGNORE_SC_ACCEPTED, Constants.VALUE_TRUE);
        }

        EndpointReference epr = PassThroughTransportUtils.getDestinationEPR(messageContext);
        if (epr != null) {
            if (!epr.hasNoneAddress() && messageContext.getProperty(Constants.OUT_TRANSPORT_INFO) != null) {
                RequestWorker requestWorker = (RequestWorker) messageContext.getProperty(Constants.OUT_TRANSPORT_INFO);
                deliveryAgent.submitRequest(messageContext, epr, requestWorker);
            } else {
                //  handleException("Cannot send message to " + AddressingConstants.Final.WSA_NONE_URI);
                log.error("Cannot send message");
            }
        } else {
            if (messageContext.getProperty(Constants.OUT_TRANSPORT_INFO) != null) {
                if (messageContext.getProperty(Constants.OUT_TRANSPORT_INFO) instanceof RequestWorker) {
                    try {
                        deliveryAgent.submitResponse(messageContext);
                    } catch (Exception e) {
                        // handleException("Failed to submit the response", e);
                        log.error("Failed to submit the response");
                    }
                } else {
                    //handleException("No valid destination EPR to send message");
                    //should be able to handle sendUsingOutputStream  Ref NHTTP_NIO
                    //   sendUsingOutputStream(messageContext);
                    log.error("No valid destination EPR to send message");
                }
            } else {
                // handleException("No valid destination EPR to send message");
                log.error("No valid destination EPR to send message");
            }
        }

        if (messageContext.getOperationContext() != null) {
            messageContext.getOperationContext().setProperty(
                       Constants.RESPONSE_WRITTEN, Constants.VALUE_TRUE);
        }

        return InvocationResponse.CONTINUE;
    }

    public void cleanup(MessageContext messageContext) throws AxisFault {

    }

    public void init(ConfigurationContext configurationContext, TransportOutDescription transportOutDescription)
               throws AxisFault {
        deliveryAgent = new DiliveryAgent();

    }

    public void stop() {

    }
}
