package com.apex.navlab

import android.content.Intent
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GoogleNavigationTest {
    @get:Rule val rule=ActivityTestRule(GoogleNavigationActivity::class.java,false,false)
    @Test fun permissionRecoveryAndCockpit() {
        val ins=InstrumentationRegistry.getInstrumentation()
        val a=rule.launchActivity(Intent(ins.targetContext,GoogleNavigationActivity::class.java))
        // Dismiss permission prompt using back; no Google account/key authorization is assumed.
        Thread.sleep(1500)
        ins.uiAutomation.executeShellCommand("input keyevent 4").close()
        ins.waitForIdleSync()
        ins.runOnMainSync {
            val root=a.findViewById<ViewGroup>(android.R.id.content)
            val status=root.findViewWithTag<TextView>("google-status")
            assertNotNull(status)
            assertTrue("Permission remains actionable",status.text.contains("location",true))
            fun texts(v:View):List<TextView> = if(v is ViewGroup)(0 until v.childCount).flatMap {texts(v.getChildAt(it))} else if(v is TextView)listOf(v) else emptyList()
            val all=texts(root)
            assertTrue(all.any {it.text.toString()=="TRX"})
            assertTrue(all.any {it.text.toString()=="Saved"})
            assertTrue(all.any {it.text.toString()=="End"})
            val diagnostic=status.text.toString()
            all.filterIsInstance<android.widget.EditText>().first().setText("Plainsboro NJ")
            all.first {it.text.toString()=="Find"}.performClick()
            assertEquals("Find must preserve the actionable startup error",diagnostic,status.text.toString())
            all.first {it.text.toString().contains("Setup")}.performClick()
        }
        ins.waitForIdleSync()
        val shot=checkNotNull(ins.uiAutomation.takeScreenshot())
        val file=File(a.getExternalFilesDir(null),"06-google-setup.png")
        file.outputStream().use {shot.compress(Bitmap.CompressFormat.PNG,100,it)}
        for(c in listOf("mkdir -p /sdcard/Download/navlab","cp ${file.absolutePath} /sdcard/Download/navlab/")) {
            ins.uiAutomation.executeShellCommand(c).use { ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        }
    }
}
