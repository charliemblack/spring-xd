package org.springframework.integration.x.gemfire;

import com.gemstone.gemfire.cache.CacheListener;
import com.gemstone.gemfire.cache.EntryEvent;
import com.gemstone.gemfire.cache.RegionEvent;

/**
 * Created by cblack on 12/18/13.
 */
public class GemFireLoggingListener implements CacheListener {
    @Override
    public void afterCreate(EntryEvent entryEvent) {
        System.out.println("afterCreate entryEvent = " + entryEvent);
    }

    @Override
    public void afterUpdate(EntryEvent entryEvent) {
        System.out.println("afterUpdate entryEvent = " + entryEvent);
    }

    @Override
    public void afterInvalidate(EntryEvent entryEvent) {
        System.out.println("afterInvalidate entryEvent = " + entryEvent);
    }

    @Override
    public void afterDestroy(EntryEvent entryEvent) {
        System.out.println("afterDestroy entryEvent = " + entryEvent);
    }

    @Override
    public void afterRegionInvalidate(RegionEvent regionEvent) {

    }

    @Override
    public void afterRegionDestroy(RegionEvent regionEvent) {

    }

    @Override
    public void afterRegionClear(RegionEvent regionEvent) {

    }

    @Override
    public void afterRegionCreate(RegionEvent regionEvent) {

    }

    @Override
    public void afterRegionLive(RegionEvent regionEvent) {

    }

    @Override
    public void close() {

    }
}
