package com.example.tvlauncher.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCardDataSourceTest {

    @Test
    fun `getCardConfigs returns empty list`() = runBlocking {
        val dataSource = LocalCardDataSource()
        assertTrue(dataSource.getCardConfigs().isEmpty())
    }

    @Test
    fun `getLauncherConfig returns default config with null screenColor`() = runBlocking {
        val dataSource = LocalCardDataSource()
        val config = dataSource.getLauncherConfig()
        assertNull(config.screenColor)
        assertEquals(LauncherConfig(), config)
    }

    @Test
    fun `loadLauncherData returns null for local source`() = runBlocking {
        val dataSource = LocalCardDataSource()
        assertNull(dataSource.loadLauncherData())
    }
}
