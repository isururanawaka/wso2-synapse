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


import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.apache.log4j.Logger;
import org.apache.synapse.transport.netty.passthru.config.SourceConfiguration;

public class SourceChannelInitializer extends ChannelInitializer<SocketChannel> {
    private static final Logger logger = Logger.getLogger(SourceChannelInitializer.class);
    private SourceConfiguration sourceConfiguration;


    protected  SourceChannelInitializer(SourceConfiguration sourceConfiguration){
        this.sourceConfiguration = sourceConfiguration;
    }


    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        if(logger.isDebugEnabled()) {
            logger.info("initializing channel pipeline");
        }
        ChannelPipeline p = ch.pipeline();
        p.addLast(PassThroughConstants.HTTP_CODEC,new HttpServerCodec());
        p.addLast(PassThroughConstants.HTTP_AGREGRATOR,new HttpObjectAggregator(PassThroughConstants.MAXIMUM_CHUNK_SIZE_AGGREGATOR));
        p.addLast(PassThroughConstants.HANDLER, new SourceHandler(sourceConfiguration));

    }


}
