package com.example.tvlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherDataParserTest {

    /** 与 data/data.json 结构一致的精简样本 */
    private val sampleJson = """
        {"config":{"lightMode": false,"smallIcon": false,"displayDesc": false,"displayHead": false,"screenColor":"#1a1a1a","displayTitle": false},
         "logoUrl":"http://x/logo.png","channel":"LauncherNexgen_T950","utc":1786005689,
         "modules":[{"moduleName":"home","sort":0,
           "productGroups":[{"config":"{\"width\": 989, \"height\": 339, \"arrange\": \"carousel\", \"textColor\": \"#fff\", \"displayTitle\": true}",
             "groupName":"group1","sort":1,
             "groupApps":[{"appName":"Settings","intents":"","packageName":"com.nexgen.launcher","iconUrl":null,"iconBgUrl":"http://x/banner_00.jpg","md5":"84b2d83c4c32af9427eae1166f8acd87","config":{"top":0,"left":0,"width":195,"height":115,"behavior":"app","displayName":0},"sort":1,"isCheckVer":0,"versionName":"0.0.0","versionCode":0}]}]}]}
    """.trimIndent()

    @Test
    fun `parse reads config and nested structure`() {
        val data = LauncherDataParser.parse(sampleJson)
        assertEquals("#1a1a1a", data.config?.screenColor)
        assertFalse(data.config!!.lightMode)
        assertEquals(1, data.modules.size)
        assertEquals("home", data.modules[0].moduleName)
        assertEquals(1, data.modules[0].productGroups.size)
        assertEquals("group1", data.modules[0].productGroups[0].groupName)
        assertEquals(1, data.modules[0].productGroups[0].groupApps.size)
    }

    @Test
    fun `parse reads groupApp fields`() {
        val app = LauncherDataParser.parse(sampleJson).modules[0].productGroups[0].groupApps[0]
        assertEquals("Settings", app.appName)
        assertEquals("com.nexgen.launcher", app.packageName)
        assertEquals("http://x/banner_00.jpg", app.iconBgUrl)
        assertEquals("84b2d83c4c32af9427eae1166f8acd87", app.md5)
        assertEquals("app", app.config?.behavior)
        assertEquals(0, app.versionCode)
    }

    @Test
    fun `parse handles missing optional fields with defaults`() {
        val data = LauncherDataParser.parse("""{"config":{},"modules":[]}""")
        assertNull(data.config?.screenColor)
        assertTrue(data.modules.isEmpty())
    }

    @Test
    fun `resolveIntent maps builtin behaviors`() {
        val fm = GroupApp(intents = "FILE_MANAGER")
        val st = GroupApp(intents = "SETTINGS")
        assertEquals("com.android.settings", st.resolveIntent())
        assertNull(GroupApp(intents = "").resolveIntent())
    }
}
