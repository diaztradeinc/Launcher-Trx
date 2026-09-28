package com.apex.navlab

import android.app.Service
import android.content.Intent
import android.os.*
import com.google.android.libraries.mapsplatform.turnbyturn.TurnByTurnManager
import com.google.android.libraries.mapsplatform.turnbyturn.model.NavInfo

/** Main-thread, in-process delivery; the visible 3D activity installs/removes its sink. */
internal object LiveNavFeed {
    var receiver: ((NavInfo) -> Unit)? = null
}

class LiveNavFeedService : Service() {
    private val decoder = TurnByTurnManager.createInstance()
    private val incoming = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == TurnByTurnManager.MSG_NAV_INFO) {
                try { decoder.readNavInfoFromBundle(msg.data)?.let { LiveNavFeed.receiver?.invoke(it) } }
                catch (e: Exception) { android.util.Log.w("ApexLiveNav", "Guidance feed unavailable: ${e.javaClass.simpleName}") }
            } else super.handleMessage(msg)
        }
    })
    override fun onBind(intent: Intent): IBinder = incoming.binder
}
