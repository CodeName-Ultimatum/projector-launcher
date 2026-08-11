package com.example.tvlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelStoreTest {

    private class MemoryLongStorage : LongStorage {
        private val map = mutableMapOf<String, Long>()
        override fun getLong(key: String, def: Long): Long = map[key] ?: def
        override fun putLong(key: String, value: Long) { map[key] = value }
    }

    @Test
    fun `channel is fixed H313_launch_test`() {
        val store = ChannelStore(MemoryLongStorage())
        assertEquals("H313_launch_test", store.channel)
    }

    @Test
    fun `lastUtc defaults to 0`() {
        val store = ChannelStore(MemoryLongStorage())
        assertEquals(0L, store.lastUtc)
    }

    @Test
    fun `lastUtc persists after set`() {
        val storage = MemoryLongStorage()
        ChannelStore(storage).lastUtc = 1786005689L
        assertEquals(1786005689L, ChannelStore(storage).lastUtc)
    }
}
