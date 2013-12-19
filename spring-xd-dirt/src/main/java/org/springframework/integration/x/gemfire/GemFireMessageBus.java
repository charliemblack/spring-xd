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
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.distributed.DistributedMember;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.MediaType;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.endpoint.EventDrivenConsumer;
import org.springframework.integration.endpoint.MessageProducerSupport;
import org.springframework.integration.handler.AbstractReplyProducingMessageHandler;
import org.springframework.integration.x.bus.Binding;
import org.springframework.integration.x.bus.MessageBusSupport;
import org.springframework.integration.x.bus.serializer.MultiTypeCodec;
import org.springframework.messaging.*;
import org.springframework.util.Assert;

import java.util.Collection;

import static com.gemstone.gemfire.cache.partition.PartitionRegionHelper.getPrimaryMemberForKey;
import static com.gemstone.gemfire.cache.partition.PartitionRegionHelper.isPartitionedRegion;


/**
 * @author Charlie Black
 */
public class GemFireMessageBus extends MessageBusSupport implements DisposableBean {
    public static final String PREFIX = "__spring_xd_";
    public static final String PARTITION_KEY = "partition_key";
    public static final String DESTINATION_REGION = "destination_region";
    private final Log logger = LogFactory.getLog(this.getClass());

    public GemFireMessageBus(MultiTypeCodec<Object> codec) {
        System.out.println("org.springframework.integration.x.gemfire.GemFireMessageBus.GemFireMessageBus");
        setCodec(codec);
    }

    //incoming queue
    @Override
    public void bindConsumer(String name, MessageChannel moduleInputChannel, Collection<MediaType> acceptedMediaTypes, boolean aliasHint) {
        System.out.println("org.springframework.integration.x.gemfire.GemFireMessageBus.bindConsumer");
        System.out.println("name = " + name);
        String messageChannelName = String.format("%s%s.queue", PREFIX, name);
        System.out.println("messageChannelName = " + messageChannelName);
        MessageProducerSupport messageProducerSupport = GemFireRPCMessaging.createMessageProducer(messageChannelName);
        doRegisterConsumer(name, moduleInputChannel, acceptedMediaTypes, messageProducerSupport);
    }

    //incomming topic
    @Override
    public void bindPubSubConsumer(String name, MessageChannel inputChannel, Collection<MediaType> acceptedMediaTypes) {
        System.out.println("org.springframework.integration.x.gemfire.GemFireMessageBus.bindPubSubConsumer");
        System.out.println("name = " + name);
        String messageChannelName = String.format("%s%s.topic", PREFIX, name);
        System.out.println("messageChannelName = " + messageChannelName);
        MessageProducerSupport messageProducerSupport = GemFireRPCMessaging.createMessageProducer(messageChannelName);
        doRegisterConsumer(name, inputChannel, acceptedMediaTypes, messageProducerSupport);

    }

    //outgoing queue
    @Override
    public void bindProducer(String name, MessageChannel outputChannel, boolean aliasHint) {
        System.out.println("org.springframework.integration.x.gemfire.GemFireMessageBus.bindProducer");
        System.out.println("name = " + name);

        doRegisterProducer(name, outputChannel, new QueueMessageSender(String.format("%s%s.queue", PREFIX, name)));
    }

    //outgoing topic
    @Override
    public void bindPubSubProducer(String name, MessageChannel outputChannel) {
        System.out.println("org.springframework.integration.x.gemfire.GemFireMessageBus.bindPubSubProducer");
        System.out.println("name = " + name);
        doRegisterProducer(name, outputChannel, new TopicMessageSender(String.format("%s%s.topic", PREFIX, name)));
    }

    @Override
    public void destroy() throws Exception {
        stopBindings();
    }

    private void doRegisterConsumer(String name, MessageChannel moduleInputChannel,
                                    final Collection<MediaType> acceptedMediaTypes, MessageProducerSupport adapter) {
        DirectChannel bridgeToModuleChannel = new DirectChannel();
        bridgeToModuleChannel.setBeanName(name + ".bridge");
        adapter.setOutputChannel(bridgeToModuleChannel);
        adapter.setBeanName("inbound." + name);
        adapter.afterPropertiesSet();
        addBinding(Binding.forConsumer(adapter, moduleInputChannel));
        GemFireRPCListener convertingBridge = new GemFireRPCListener(acceptedMediaTypes);
        convertingBridge.setOutputChannel(moduleInputChannel);
        convertingBridge.setBeanName(name + ".convert.bridge");
        convertingBridge.afterPropertiesSet();
        bridgeToModuleChannel.subscribe(convertingBridge);
        adapter.start();
    }

    private void doRegisterProducer(final String name, MessageChannel moduleOutputChannel, MessageHandler handler) {
        Assert.isInstanceOf(SubscribableChannel.class, moduleOutputChannel);
        EventDrivenConsumer consumer = new EventDrivenConsumer((SubscribableChannel) moduleOutputChannel, handler);
        consumer.setBeanName("outbound." + name);
        consumer.afterPropertiesSet();
        addBinding(Binding.forProducer(moduleOutputChannel, consumer));
        consumer.start();
    }

    private class QueueMessageSender implements MessageHandler {
        private String name;

        private QueueMessageSender(String name) {
            this.name = name;
        }

        @Override
        public void handleMessage(Message<?> message) throws MessagingException {
            DistributedMember member = null;
            MessageHeaders messageHeaders = message.getHeaders();
            Object destinationRegionName = messageHeaders.get(DESTINATION_REGION);
            if (destinationRegionName != null) {
                Region region = CacheFactory.getAnyInstance().getRegion((String) destinationRegionName);
                Object key = messageHeaders.get(PARTITION_KEY);
                if (region != null) {
                    if (isPartitionedRegion(region)) {
                        member = getPrimaryMemberForKey(region, key);
                    }
                }
            }
            if (member == null) {
                //TODO : Maybe come up with some kind of round robin approach.  For now lets focus on NO Network HOPs.
                member = CacheFactory.getAnyInstance().getDistributedSystem().getDistributedMember();
            }
            GemFireRPCMessaging.send(member, name, message.getPayload());
        }
    }

    private class TopicMessageSender implements MessageHandler {
        private String name;

        private TopicMessageSender(String name) {
            this.name = name;
        }

        @Override
        public void handleMessage(Message<?> message) throws MessagingException {
            GemFireRPCMessaging.send(name, message.getPayload());
        }
    }

    private class GemFireRPCListener extends AbstractReplyProducingMessageHandler {
        private Collection<MediaType> acceptedMediaTypes;

        private GemFireRPCListener(Collection<MediaType> acceptedMediaTypes) {
            this.acceptedMediaTypes = acceptedMediaTypes;
        }

        @Override
        protected Object handleRequestMessage(Message<?> requestMessage) {
            return transformPayloadForConsumerIfNecessary(requestMessage, acceptedMediaTypes);
        }

        @Override
        protected boolean shouldCopyRequestHeaders() {
            /*
			 * we've already copied the headers so no need for the ARPMH to do it, and we don't want the content-type
			 * restored if absent.
			 */
            return false;
        }
    }


}