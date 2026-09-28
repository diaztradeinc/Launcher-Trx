package com.apex.navlab

import android.graphics.Bitmap
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.After
import android.os.ParcelFileDescriptor
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SimulationTest {
    @get:Rule val rule=ActivityTestRule(MainActivity::class.java)
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun click(tag:String) { instrumentation.runOnMainSync { rule.activity.findViewById<View>(android.R.id.content).findViewWithTag<View>(tag).performClick() } }
    private fun awaitCondition(timeout:Long, condition:()->Boolean) {
        val deadline=System.currentTimeMillis()+timeout
        while(System.currentTimeMillis()<deadline) { if(condition())return;Thread.sleep(250) }
        assertTrue("Timed out waiting for observable simulation state",condition())
    }
    private fun save(name:String):Bitmap {
        val screenshot=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file=File(rule.activity.getExternalFilesDir(null),"$name.png")
        file.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) }
        for(command in listOf("mkdir -p /sdcard/Download/navlab", "cp ${file.absolutePath} /sdcard/Download/navlab/")) {
            instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
                val output=ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes().decodeToString()
                assertTrue("Screenshot export failed: $output",output.isBlank())
            }
        }
        return screenshot
    }
    @After fun retainLastScreen() { save("05-last-screen") }
    private fun capture(name:String) {
        val screenshot=save(name)
        assertNotNull("Surface screenshot available",screenshot)
        // A working surface must contain many distinct colors, not just HUD panels over black.
        val colors=HashSet<Int>();val x0=screenshot.width/4;val x1=screenshot.width*4/5
        for(x in x0 until x1 step 4)for(y in screenshot.height*45/100 until screenshot.height*75/100 step 4)colors.add(screenshot.getPixel(x,y))
        assertTrue("Rendered terrain must be visible: ${colors.size} sampled colors",colors.size>30)
    }
    @Test fun renderedSceneAndControls() {
        awaitCondition(60000){rule.activity.frames>10}
        assertTrue("Filament submitted frames",rule.activity.frames>10)
        assertTrue(rule.activity.paused)
        capture("01-chase")
        click("pause");awaitCondition(30000){rule.activity.progress>5}
        assertTrue(rule.activity.progress>5)
        click("pause");val p=rule.activity.progress;Thread.sleep(400)
        assertEquals(p,rule.activity.progress,.001)
        click("map");assertTrue(rule.activity.mapMode);Thread.sleep(700);capture("02-map")
        click("recenter");assertFalse(rule.activity.mapMode)
        click("light");Thread.sleep(700);capture("03-night")
        click("light")
        click("speed");click("pause");awaitCondition(90000){rule.activity.progress>280}
        click("pause");capture("04-exit")
        assertTrue("Reached curved route",rule.activity.progress>250)
        click("end");assertTrue(rule.activity.ended)
        click("pause");assertFalse(rule.activity.ended);assertTrue(rule.activity.progress<10)
        click("pause")
        // Exercise surface release/recreation across backgrounding.
        instrumentation.runOnMainSync { instrumentation.callActivityOnPause(rule.activity) }
        Thread.sleep(300)
        instrumentation.runOnMainSync { instrumentation.callActivityOnResume(rule.activity) }
        val before=rule.activity.frames;awaitCondition(15000){rule.activity.frames>before}
    }
}
