package com.exactpro.th2.dataservice.healer;

import com.exactpro.th2.dataservice.healer.cache.EventsCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EventsCacheTest {
    private final Map<String, Integer> cache = new EventsCache<>(10);


    @BeforeEach
    public void prepare() {
        for (int i = 0; i < 11; i++) {
            cache.put(String.valueOf(i), i);
        }
    }

    @Test
    public void maxSizeTest() { assertEquals(cache.size(), 10); }

    @Test
    public void firstElementRemoved() { assertFalse(cache.containsValue(0)); }

    @Test
    public void accessedElementPutLast() {
        cache.get("1");

        cache.put(String.valueOf(12), 12);

        assertTrue(cache.containsKey("1"));
    }
}
