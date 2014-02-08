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

import org.springframework.messaging.Message;

import java.io.Serializable;

/**
 * @author Charlie Black
 */
public class GemFireMessage implements Serializable{

    private String channelName;
    private Message payload;

    public GemFireMessage() {
    }

    public GemFireMessage(String channelName, Message payload) {
        this.channelName = channelName;
        this.payload = payload;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public Message getPayload() {
        return payload;
    }

    public void setPayload(Message payload) {
        this.payload = payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GemFireMessage that = (GemFireMessage) o;

        if (channelName != null ? !channelName.equals(that.channelName) : that.channelName != null) return false;
        if (payload != null ? !payload.equals(that.payload) : that.payload != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = channelName != null ? channelName.hashCode() : 0;
        result = 31 * result + (payload != null ? payload.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "GemFireMessage{" +
                "channelName='" + channelName + '\'' +
                ", payload=" + payload +
                '}';
    }
}
