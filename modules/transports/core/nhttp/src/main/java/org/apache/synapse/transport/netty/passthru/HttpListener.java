/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *   * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.synapse.transport.netty.passthru;


import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.apache.axis2.AxisFault;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.context.SessionContext;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.description.TransportInDescription;
import org.apache.axis2.transport.TransportListener;
import org.apache.axis2.transport.base.BaseConstants;
import org.apache.axis2.transport.base.threads.WorkerPool;
import org.apache.log4j.Logger;
import org.apache.synapse.transport.http.conn.Scheme;
import org.apache.synapse.transport.netty.passthru.config.SourceConfiguration;


import java.util.Locale;


/**
 * Start ServerBootStrap in a given port for http inbound connections
 */

public class HttpListener implements TransportListener {
    private static Logger logger = Logger.getLogger(HttpListener.class);

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Thread listnerThread;
    private ConfigurationContext configurationContext;
    private TransportInDescription pttTransportInDescription;
    private String namePrefix;
    /**
     * Protocol Scheme for Listner
     */
    private Scheme scheme;
    private SourceConfiguration sourceConfiguration;
    private int localPort;


    private volatile int state = BaseConstants.STOPPED;

    protected Scheme initScheme() {
        return new Scheme("http", 80, false);
    }


    public void init(ConfigurationContext configurationContext, TransportInDescription transportInDescription) throws AxisFault {
        this.configurationContext = configurationContext;
        this.pttTransportInDescription = transportInDescription;
        namePrefix = transportInDescription.getName().toUpperCase(Locale.US);
        scheme = initScheme();


        int portOffset = Integer.parseInt(System.getProperty(Constants.PORT_OFF_SET, "0"));
        Parameter portParam = transportInDescription.getParameter(Constants.PORT);
        if (portParam != null) {
            int port = Integer.parseInt(portParam.getValue().toString());
            port = port + portOffset;
            this.localPort = port;
            portParam.setValue(String.valueOf(port));
            portParam.getParameterElement().setText(String.valueOf(port));
            System.setProperty(transportInDescription.getName() + Constants.NETTY_NIO_PORT, String.valueOf(port));
        } else {
            logger.error("Starting port cannnot be null");
            return;
        }

        Object obj = configurationContext.getProperty(Constants.PASS_THROUGH_TRANSPORT_WORKER_POOL);
        WorkerPool workerPool = null;
        if (obj != null) {
            workerPool = (WorkerPool) obj;
        }
        sourceConfiguration = new SourceConfiguration(configurationContext, transportInDescription, scheme, workerPool);
        sourceConfiguration.build();
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();


    }

    public void start() {
        logger.info("Starting Netty Passthrough" + namePrefix + "Listener on Port " + this.localPort);
        listnerThread = new Thread(new Runnable() {
            public void run() {

                try {
                    ServerBootstrap b = new ServerBootstrap();
                    b.option(ChannelOption.SO_BACKLOG, 10000);
                    b.group(bossGroup, workerGroup)
                            .channel(NioServerSocketChannel.class)
                            .childHandler(new SourceChannelInitializer(sourceConfiguration));
                    Channel ch = null;
                    try {
                        ch = b.bind(localPort).sync().channel();
                        ch.closeFuture().sync();
                        logger.info("Netty Passthrough Http Listner Started");
                    } catch (InterruptedException e) {
                        logger.error(e.getMessage());
                    }
                } finally {
                    bossGroup.shutdownGracefully();
                    workerGroup.shutdownGracefully();
                }
            }
        },
                "Inbound Listner"
        );
        listnerThread.start();
    }

    public void stop() throws AxisFault {

    }

    public EndpointReference getEPRForService(String s, String s2) throws AxisFault {
        return null;
    }

    public EndpointReference[] getEPRsForService(String s, String s2) throws AxisFault {
        return new EndpointReference[0];
    }

    public SessionContext getSessionContext(MessageContext messageContext) {
        return null;
    }

    public void destroy() {
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        listnerThread.suspend();
    }


}
