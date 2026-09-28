package com.apex.navlab

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Google3DPreviewTest {
    @get:Rule val rule=ActivityTestRule(Google3DPreviewActivity::class.java,false,false)

    @Test fun realMapReadinessAndTruckPlacement() {
        val ins=InstrumentationRegistry.getInstrumentation()
        val a=rule.launchActivity(Intent(ins.targetContext,Google3DPreviewActivity::class.java)
            .putExtra("latitude",40.333).putExtra("longitude",-74.593))
        var message=""
        try {
            val deadline=SystemClock.elapsedRealtime()+60000
            while(SystemClock.elapsedRealtime()<deadline) {
                ins.runOnMainSync {
                    message=a.findViewById<ViewGroup>(android.R.id.content)
                        .findViewWithTag<TextView>("3d-status").text.toString()
                }
                if(message.contains("Google 3D ready") || message.contains("unavailable"))break
                Thread.sleep(500)
            }
            assertTrue("Real Google 3D scene did not become ready: $message",message.contains("Google 3D ready"))
            val bounds=Rect()
            ins.runOnMainSync {
                a.findViewById<ViewGroup>(android.R.id.content).findViewWithTag<View>("3d-viewport").getGlobalVisibleRect(bounds)
            }
            val now=SystemClock.uptimeMillis()
            for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                val event=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,bounds.centerX().toFloat(),bounds.centerY().toFloat(),0)
                ins.uiAutomation.injectInputEvent(event,true);event.recycle()
            }
            Thread.sleep(5000)
            ins.runOnMainSync {
                val root=a.findViewById<ViewGroup>(android.R.id.content)
                message=root.findViewWithTag<TextView>("3d-status").text.toString()
                assertTrue("Truck placement callback failed: $message",message.contains("Original truck preview"))
            }
        } finally {
            val dir=File(a.getExternalFilesDir(null),"google3d").apply { mkdirs() }
            File(dir,"google3d-status.txt").writeText(message)
            ins.uiAutomation.takeScreenshot()?.let { image ->
                File(dir,"07-google3d-preview.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
            }
            for(command in listOf("mkdir -p /sdcard/Download/navlab","cp ${dir.absolutePath}/* /sdcard/Download/navlab/")) {
                ins.uiAutomation.executeShellCommand(command).use { ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
            }
        }
    }
}
