package org.springframework.integration.x.gemfire;

import java.io.Serializable;

/**
 * @author Charlie Black
 */
public class GemFireMessage implements Serializable{

    private String channelName;
    private Object payload;

    public GemFireMessage() {
    }

    public GemFireMessage(String channelName, Object payload) {
        this.channelName = channelName;
        this.payload = payload;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
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
