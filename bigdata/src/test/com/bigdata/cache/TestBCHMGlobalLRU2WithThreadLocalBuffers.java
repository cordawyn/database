/*

Copyright (C) SYSTAP, LLC 2006-2008.  All rights reserved.

Contact:
     SYSTAP, LLC
     4501 Tower Road
     Greensboro, NC 27410
     licenses@bigdata.com

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; version 2 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
/*
 * Created on Sep 16, 2009
 */

package com.bigdata.cache;

import java.util.concurrent.ExecutionException;

import com.bigdata.rawstore.Bytes;

/**
 * Some unit tests for the {@link BCHMGlobalLRU2}.
 * 
 * @author <a href="mailto:thompsonbry@users.sourceforge.net">Bryan Thompson</a>
 * @version $Id$
 */
public class TestBCHMGlobalLRU2WithThreadLocalBuffers extends
        AbstractHardReferenceGlobalLRUTest {

    /**
     * 
     */
    public TestBCHMGlobalLRU2WithThreadLocalBuffers() {
    }

    /**
     * @param name
     */
    public TestBCHMGlobalLRU2WithThreadLocalBuffers(String name) {
        super(name);
    }

    protected void setUp() throws Exception {

        super.setUp();

        final long maximumBytesInMemory = 10 * Bytes.kilobyte;

        // clear at least 25% of the memory.
        final long minCleared = maximumBytesInMemory / 4;

        final int minimumCacheSetCapacity = 0;

        final int initialCacheCapacity = 16;

        final float loadFactor = .75f;

        /* Note: A concurrencyLevel of zero means use true thread local buffers. */
        final int concurrencyLevel = 0;

        final int threadLocalBufferCapacity = 128;
        
        lru = new BCHMGlobalLRU2<Long, Object>(maximumBytesInMemory,
                minCleared, minimumCacheSetCapacity, initialCacheCapacity,
                loadFactor, concurrencyLevel, threadLocalBufferCapacity);

    }

    /**
     * This is a hook for running just this test under the profiler.
     * 
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public void test_concurrentOperations() throws InterruptedException,
            ExecutionException {

        super.test_concurrentOperations();

    }
    
}
