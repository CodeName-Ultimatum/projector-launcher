package com.example.tvlauncher.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiCardDataSourceTest {

    private class MemoryLongStorage : LongStorage {
        private val map = mutableMapOf<String, Long>()
        override fun getLong(key: String, def: Long): Long = map[key] ?: def
        override fun putLong(key: String, value: Long) { map[key] = value }
    }

    private val newConfigJson = """
        {"channel":"H313_launch_test","utc":200,"modules":[]}
    """.trimIndent()

    private val wrongChannelJson = """
        {"channel":"OTHER_DEVICE","utc":500,"modules":[]}
    """.trimIndent()

    private class FakeDataSource(apiUrl: String, storage: LongStorage, private val json: String) :
        ApiCardDataSource(context = null, apiUrl = apiUrl, storage = storage) {
        override fun fetch(url: String): String? = json
    }

    @Test
    fun `utc greater than lastUtc returns config and updates lastUtc`() = runBlocking {
        val storage = MemoryLongStorage()
        val ds = FakeDataSource("http://x", storage, newConfigJson)
        val data = ds.loadLauncherData()
        assertTrue(data != null)
        assertEquals(200L, data!!.utc)
        assertEquals(200L, storage.getLong("last_utc", 0L))
    }

    @Test
    fun `utc equal to lastUtc returns null and does not update`() = runBlocking {
        val storage = MemoryLongStorage()
        storage.putLong("last_utc", 200L)
        val ds = FakeDataSource("http://x", storage, newConfigJson)
        assertNull(ds.loadLauncherData())
        assertEquals(200L, storage.getLong("last_utc", 0L))
    }

    @Test
    fun `channel mismatch returns null and does not update lastUtc`() = runBlocking {
        val storage = MemoryLongStorage()
        val ds = FakeDataSource("http://x", storage, wrongChannelJson)
        assertNull(ds.loadLauncherData())
        assertEquals(0L, storage.getLong("last_utc", 0L))
    }

    @Test
    fun `first launch lastUtc 0 applies config`() = runBlocking {
        val storage = MemoryLongStorage()
        val ds = FakeDataSource("http://x", storage, newConfigJson)
        assertTrue(ds.loadLauncherData() != null)
    }

    @Test
    fun `fetch failure returns null`() = runBlocking {
        val storage = MemoryLongStorage()
        val ds = object : ApiCardDataSource(context = null, apiUrl = "http://x", storage = storage) {
            override fun fetch(url: String): String? = null
        }
        assertNull(ds.loadLauncherData())
    }
}
