package com.example.jarvis

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/** Permet à Jarvis d'agir sur tout le téléphone (retour, accueil, appuyer sur un bouton, lire l'écran). */
class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: JarvisAccessibilityService? = null
        @Volatile var currentPackage: String? = null
        var connected by mutableStateOf(false)

        private val main = Handler(Looper.getMainLooper())

        fun <T> onMain(block: () -> T): T {
            if (Looper.myLooper() == Looper.getMainLooper()) return block()
            val task = FutureTask(Callable { block() })
            main.post(task)
            return task.get(3, TimeUnit.SECONDS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        connected = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.let { currentPackage = it.toString() }
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        connected = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        connected = false
        super.onDestroy()
    }

    fun global(action: String): Boolean = onMain {
        when (action) {
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "quick_settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            "lock" -> Build.VERSION.SDK_INT >= 28 && performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            "screenshot" -> Build.VERSION.SDK_INT >= 28 && performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            else -> false
        }
    }

    fun clickText(text: String): Boolean = onMain {
        val root = rootInActiveWindow ?: return@onMain false
        for (node in root.findAccessibilityNodeInfosByText(text)) {
            var cur: AccessibilityNodeInfo? = node
            while (cur != null) {
                if (cur.isClickable && cur.isEnabled) {
                    return@onMain cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                cur = cur.parent
            }
        }
        false
    }

    fun readScreen(): String = onMain {
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || sb.length > 3000 || depth > 25) return
            if (n.isVisibleToUser && !n.isPassword) {
                val t = n.text?.toString() ?: n.contentDescription?.toString()
                if (!t.isNullOrBlank()) sb.append(t.trim()).append('\n')
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(rootInActiveWindow, 0)
        sb.toString().take(3000)
    }
}
