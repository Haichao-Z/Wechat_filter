package com.example.v2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class ContactListChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.v2.CONTACT_LIST_CHANGED") {
            // 尝试重启服务以刷新联系人列表
            val serviceIntent = Intent(context, NotificationFilterService::class.java)
            context.stopService(serviceIntent)
            context.startService(serviceIntent)

            android.util.Log.d("ContactListReceiver", "联系人列表已更新，服务已重启")
        }
    }
}