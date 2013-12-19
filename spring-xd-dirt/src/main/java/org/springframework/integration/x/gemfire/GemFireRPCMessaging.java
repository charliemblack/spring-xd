/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.x.gemfire;

import com.gemstone.gemfire.cache.CacheFactory;
import com.gemstone.gemfire.cache.Declarable;
import com.gemstone.gemfire.cache.execute.FunctionAdapter;
import com.gemstone.gemfire.cache.execute.FunctionContext;
import com.gemstone.gemfire.cache.execute.FunctionService;
import com.gemstone.gemfire.distributed.DistributedMember;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.integration.endpoint.MessageProducerSupport;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.Properties;

import static org.springframework.integration.support.MessageBuilder.withPayload;


/**
 * @author Charlie Black
 */
public class GemFireRPCMessaging extends FunctionAdapter implements Declarable {

    public static final String ID = GemFireRPCMessaging.class.getName();
    private static final Log logger = LogFactory.getLog(GemFireRPCMessaging.class);
    private static HashMap<String, GemFireMessageProducerSupport> messageProducer = new HashMap<String, GemFireMessageProducerSupport>();

    public GemFireRPCMessaging() {
        System.out.println("org.springframework.integration.x.gemfire.GemFireRPCMessaging.GemFireRPCMessaging");
    }

    public static void send(DistributedMember member, String channelName, Object payload) {
        if (logger.isDebugEnabled()) {
            logger.debug("org.springframework.integration.x.gemfire.GemFireRPCMessaging.send1" +
                    "\n\tmember = " + member +
                    "\n\tchannelName = " + channelName +
                    "\n\tpayload = " + payload);
        }
        FunctionService.onMember(member).withArgs(new GemFireMessage(channelName, payload)).execute(ID).getResult();
    }

    public static void send(String channelName, Object payload) {
        if (logger.isDebugEnabled()) {
            logger.debug("org.springframework.integration.x.gemfire.GemFireRPCMessaging.send2" +
                    "\n\tchannelName = " + channelName +
                    "\n\tpayload = " + payload +
                    "\n\tCacheFactory.getAnyInstance().getMembers() = " + CacheFactory.getAnyInstance().getMembers().size());
        }
        FunctionService.onMembers("container").withArgs(new GemFireMessage(channelName, payload)).execute(ID).getResult();
    }

    public static MessageProducerSupport createMessageProducer(String channelName) {
        GemFireMessageProducerSupport producer;

        synchronized (messageProducer) {
            producer = messageProducer.get(channelName);
            if (producer == null) {
                producer = new GemFireMessageProducerSupport();
                messageProducer.put(channelName, producer);
            }
        }
        return producer;
    }

    @Override
    public void execute(FunctionContext functionContext) {
        Assert.isInstanceOf(GemFireMessage.class, functionContext.getArguments());
        GemFireMessage gemFireMessage = (GemFireMessage) functionContext.getArguments();

        if (logger.isDebugEnabled()) {
            logger.debug("GemFireRPCMessaging.receive " +
                    "\n\tgemFireMessage = " + gemFireMessage +
                    "\n\tmessageProducer = " + messageProducer +
                    "\n\tmessageProducer.get() = " + messageProducer.get(gemFireMessage.getChannelName()));
        }
        GemFireMessageProducerSupport producerSupport = messageProducer.get(gemFireMessage.getChannelName());
        if (producerSupport != null) {
            Message message = withPayload(gemFireMessage.getPayload()).build();
            GemFireMessageProducerSupport handler = messageProducer.get(gemFireMessage.getChannelName());
            handler.pushMessage(message);
        }
        functionContext.getResultSender().lastResult(1);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void init(Properties properties) {

    }

    private static class GemFireMessageProducerSupport extends MessageProducerSupport {

        public void pushMessage(Message message) {
            sendMessage(message);
        }
    }
}

