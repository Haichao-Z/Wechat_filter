package com.example.v2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import java.util.HashSet

class NotificationFilterService : NotificationListenerService() {

    private val WECHAT_PACKAGE = "com.tencent.mm"
    private val allowedContacts = HashSet<String>()
    private val TAG = "NotificationFilter"

    private val RECENT_NOTIFICATIONS = HashSet<String>() // 用于存储最近处理过的通知ID
    private val MAX_RECENT_SIZE = 20 // 最大记录数量

    override fun onCreate() {
        super.onCreate()
        // 从应用设置中加载允许接收通知的联系人列表
        loadAllowedContacts()
        createSilentChannel()

        // 创建前台服务通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "notification_filter_service",
                "通知监听服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(serviceChannel)

            val notification = NotificationCompat.Builder(this, "notification_filter_service")
                .setContentTitle("微信通知筛选")
                .setContentText("服务正在运行中")
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            startForeground(101, notification)
        }

        Log.d(TAG, "通知监听服务已创建并初始化")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // 检查是否是微信通知
        if (WECHAT_PACKAGE == sbn.packageName) {
            // 获取通知内容
            val notification = sbn.notification
            val extras = notification.extras

            // 提取发送者信息（联系人名称）
            val title = extras.getString(Notification.EXTRA_TITLE)
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

            // 记录收到的通知信息
            Log.d(TAG, "收到微信通知: $title - $text")

            // 重新加载联系人列表(确保使用最新数据)
            loadAllowedContacts()
            Log.d(TAG, "当前允许的联系人: $allowedContacts")

            // 检查发送者是否在允许列表中
            val shouldKeep = isAllowedContact(title)

            if (!shouldKeep) {
                // 如果不是允许的联系人，取消通知并阻止震动
                Log.d(TAG, "取消通知: $title 不在允许列表中")

                try {
                    // 尝试更强硬的方式阻止震动
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager

                        // 如果有免打扰权限，使用临时免打扰模式
                        if (notificationManager.isNotificationPolicyAccessGranted) {
                            val currentInterruptionFilter = notificationManager.currentInterruptionFilter

                            // 临时启用全部静音
                            notificationManager.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_NONE)

                            // 取消通知
                            cancelNotification(sbn.key)

                            // 延迟后恢复原始设置
                            android.os.Handler(mainLooper).postDelayed({
                                notificationManager.setInterruptionFilter(currentInterruptionFilter)
                                Log.d(TAG, "已恢复原始免打扰设置")
                            }, 500) // 500毫秒后恢复
                        } else {
                            // 如果没有免打扰权限，只能取消通知
                            cancelNotification(sbn.key)
                        }
                    } else {
                        // 较旧版本Android，只能取消通知
                        cancelNotification(sbn.key)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "取消通知时出错", e)
                    // 如果上述方法失败，尝试常规方式取消通知
                    cancelNotification(sbn.key)
                }
            } else {
                // 如果是允许的联系人，保留通知
                Log.d(TAG, "保留通知: $title 在允许列表中")
            }
        }
    }

    // 创建无声通知渠道
    private fun createSilentChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager

            // 创建一个无声通知渠道
            val silentChannelId = "silent_wechat_channel"
            val silentChannelName = "微信静音通知"
            val silentChannel = android.app.NotificationChannel(
                silentChannelId,
                silentChannelName,
                android.app.NotificationManager.IMPORTANCE_LOW // 低重要性
            ).apply {
                setSound(null, null) // 无声音
                enableVibration(false) // 禁用震动
                vibrationPattern = longArrayOf(0L) // 设置空震动模式
                enableLights(false) // 禁用指示灯
            }

            notificationManager.createNotificationChannel(silentChannel)

            Log.d(TAG, "已创建无声通知渠道")
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "通知监听服务已连接")
        // 连接时重新加载联系人列表
        loadAllowedContacts()
    }

    private fun isAllowedContact(contactName: String?): Boolean {
        if (contactName == null) return false

        // 调试输出
        Log.d(TAG, "检查联系人: $contactName 是否在列表中: $allowedContacts")

        // 修复：检查联系人名称是否包含在允许列表中
        // 微信通知标题可能格式为"老婆 - [语音] 2""，所以我们需要提取联系人名称部分
        val simpleName = contactName.split(" ").firstOrNull() ?: contactName

        // 如果联系人列表为空，默认不允许任何通知
        if (allowedContacts.isEmpty()) {
            Log.d(TAG, "联系人列表为空，默认不允许任何通知")
            return false
        }

        // 检查联系人是否在列表中
        val isAllowed = allowedContacts.contains(simpleName)
        Log.d(TAG, "提取的联系人名称: $simpleName, 是否允许: $isAllowed")
        return isAllowed
    }

    private fun loadAllowedContacts() {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val savedContacts = prefs.getStringSet("allowed_contacts", HashSet<String>())

        // 清空当前列表并添加新数据
        allowedContacts.clear()
        if (savedContacts != null && savedContacts.isNotEmpty()) {
            allowedContacts.addAll(savedContacts)
        }

        Log.d(TAG, "已加载允许的联系人: $allowedContacts")
    }

}