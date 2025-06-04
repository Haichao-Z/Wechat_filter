package com.example.v2

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import java.util.HashSet
import android.util.Log

class MainActivity : AppCompatActivity() {
    private val allowedContacts = HashSet<String>()
    private val TAG = "MainActivity"
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_main)

            // 请求必要权限
            requestNecessaryPermissions()

            // 检查电池优化
            checkBatteryOptimization()

            // 检查通知访问权限
            checkNotificationPermission()

            // 加载已保存的联系人列表
            loadAllowedContacts()

            // 设置UI元素
            setupContactSelection()

            // 添加状态指示器更新定时器
            val handler = android.os.Handler(mainLooper)
            handler.postDelayed(object : Runnable {
                override fun run() {
                    updateServiceStatus()
                    handler.postDelayed(this, 3000)
                }
            }, 1000)

            showDeviceOptimizationHint()
            checkDoNotDisturbPermission()

        } catch (e: Exception) {
            Log.e(TAG, "onCreate error", e)
            // 防止闪退，显示错误信息
            Toast.makeText(this, "应用初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestNecessaryPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Android 13+ 需要通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun checkBatteryOptimization() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                val packageName = packageName

                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("电池优化设置")
                        .setMessage("为确保应用正常工作，请将本应用加入电池优化白名单")
                        .setPositiveButton("去设置") { _, _ ->
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                intent.data = Uri.parse("package:$packageName")
                                startActivity(intent)
                            } catch (e: Exception) {
                                // 如果上述方法失败，尝试通用设置页面
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                startActivity(intent)
                            }
                        }
                        .setNegativeButton("稍后", null)
                        .show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkBatteryOptimization error", e)
        }
    }

    private fun checkDoNotDisturbPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager

                if (!notificationManager.isNotificationPolicyAccessGranted) {
                    val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    startActivity(intent)
                    Toast.makeText(this, "请授予「微信提醒筛选器」免打扰访问权限以控制震动", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkDoNotDisturbPermission error", e)
        }
    }

    private fun showDeviceOptimizationHint() {
        try {
            val manufacturer = Build.MANUFACTURER.lowercase(java.util.Locale.getDefault())

            when {
                manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                    showDialog(
                        "小米设备重要设置",
                        "为避免闪退和服务被杀死，请进行以下设置:\n\n" +
                                "1. 设置 > 应用管理 > 本应用 > 自启动 (开启)\n" +
                                "2. 设置 > 应用管理 > 本应用 > 省电策略 > 无限制\n" +
                                "3. 设置 > 电量和性能 > 应用省电管理 > 找到本应用并设为无限制\n" +
                                "4. 最近任务中锁定本应用\n" +
                                "5. 安全中心 > 权限管理 > 自启动管理 (允许)"
                    )
                }
                manufacturer.contains("vivo") -> {
                    showDialog(
                        "vivo设备重要设置",
                        "为避免闪退和服务被杀死，请进行以下设置:\n\n" +
                                "1. 设置 > 应用管理 > 本应用 > 自启动 (开启)\n" +
                                "2. 设置 > 电池 > 后台高耗电 (允许)\n" +
                                "3. 设置 > 更多设置 > 权限管理 > 悬浮窗 (允许)\n" +
                                "4. i管家 > 应用管理 > 自启动管理 (允许)\n" +
                                "5. 最近任务中锁定本应用"
                    )
                }
                manufacturer.contains("oppo") -> {
                    showDialog(
                        "OPPO设备设置",
                        "在OPPO设备上，您还需要:\n" +
                                "1. 进入设置 > 应用管理 > 本应用\n" +
                                "2. 自启动 (开启)\n" +
                                "3. 电池 > 后台冻结 (关闭)\n" +
                                "4. 多任务管理器中锁定本应用"
                    )
                }
                manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                    showDialog(
                        "华为设备设置",
                        "在华为设备上，您还需要:\n" +
                                "1. 进入设置 > 应用 > 本应用\n" +
                                "2. 权限 > 自启动 (开启)\n" +
                                "3. 电池 > 启动管理 (允许)\n" +
                                "4. 电池 > 后台活动 (允许)"
                    )
                }
                manufacturer.contains("samsung") -> {
                    showDialog(
                        "三星设备设置",
                        "在三星设备上，您还需要:\n" +
                                "1. 进入设置 > 应用 > 本应用\n" +
                                "2. 电池 > 允许后台活动\n" +
                                "3. 设备维护 > 电池 > 未监视的应用 (添加本应用)"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "showDeviceOptimizationHint error", e)
        }
    }

    private fun showDialog(title: String, message: String) {
        try {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message + "\n\n否则通知监听服务可能被系统关闭或导致应用闪退")
                .setPositiveButton("我知道了", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "showDialog error", e)
        }
    }

    private fun updateServiceStatus() {
        try {
            val statusTextView = findViewById<TextView>(R.id.titleTextView)
            if (isNotificationServiceEnabled()) {
                statusTextView.text = "微信重要联系人提醒 (服务运行中)"
                statusTextView.setTextColor(android.graphics.Color.GREEN)
            } else {
                statusTextView.text = "微信重要联系人提醒 (服务未运行)"
                statusTextView.setTextColor(android.graphics.Color.RED)
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateServiceStatus error", e)
        }
    }

    private fun checkNotificationPermission() {
        try {
            val permissionButton = findViewById<Button>(R.id.permissionButton)

            if (isNotificationServiceEnabled()) {
                permissionButton.text = "通知访问权限已授予"
                // 尝试重载服务
                try {
                    val intent = Intent(this, NotificationFilterService::class.java)
                    stopService(intent)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Service start error", e)
                }
            } else {
                permissionButton.text = "授予通知访问权限"
            }

            permissionButton.setOnClickListener {
                if (!isNotificationServiceEnabled()) {
                    val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                    startActivity(intent)
                    Toast.makeText(this, "请允许「微信提醒筛选器」访问通知", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "通知访问权限已授予，服务正在运行", Toast.LENGTH_SHORT).show()
                    saveAllowedContacts()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkNotificationPermission error", e)
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        return try {
            val pkgName = packageName
            val flat = android.provider.Settings.Secure.getString(
                contentResolver,
                "enabled_notification_listeners"
            )
            flat != null && flat.contains(pkgName)
        } catch (e: Exception) {
            Log.e(TAG, "isNotificationServiceEnabled error", e)
            false
        }
    }

    private fun loadAllowedContacts() {
        try {
            val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val savedContacts = prefs.getStringSet("allowed_contacts", HashSet<String>())
            allowedContacts.clear()
            if (savedContacts != null) {
                allowedContacts.addAll(savedContacts)
            }
            Log.d(TAG, "已加载联系人列表: $allowedContacts")
        } catch (e: Exception) {
            Log.e(TAG, "loadAllowedContacts error", e)
        }
    }

    private fun saveAllowedContacts() {
        try {
            val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val editor = prefs.edit()

            Log.d(TAG, "保存联系人列表: $allowedContacts")

            editor.putStringSet("allowed_contacts", HashSet(allowedContacts))
            editor.apply()

            val intent = Intent(this, ContactListChangeReceiver::class.java)
            intent.action = "com.example.v2.CONTACT_LIST_CHANGED"
            sendBroadcast(intent)

            try {
                val serviceIntent = Intent(this, NotificationFilterService::class.java)
                stopService(serviceIntent)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                Log.d(TAG, "已重启通知服务")
            } catch (e: Exception) {
                Log.e(TAG, "重启服务失败", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveAllowedContacts error", e)
        }
    }

    fun addContact(contactName: String) {
        try {
            val simpleName = contactName.split(" ").firstOrNull() ?: contactName
            allowedContacts.add(simpleName)
            saveAllowedContacts()
            Log.d(TAG, "已添加联系人: $simpleName, 当前列表: $allowedContacts")
            updateContactsList()
        } catch (e: Exception) {
            Log.e(TAG, "addContact error", e)
        }
    }

    fun removeContact(contactName: String) {
        try {
            allowedContacts.remove(contactName)
            saveAllowedContacts()
            updateContactsList()
        } catch (e: Exception) {
            Log.e(TAG, "removeContact error", e)
        }
    }

    private fun setupContactSelection() {
        try {
            val addButton = findViewById<Button>(R.id.addContactButton)
            val contactInput = findViewById<EditText>(R.id.contactNameInput)
            val contactsRecyclerView = findViewById<RecyclerView>(R.id.contactsRecyclerView)

            contactsRecyclerView.layoutManager = LinearLayoutManager(this)
            val adapter = ContactAdapter(allowedContacts.toList()) { contactName ->
                removeContact(contactName)
            }
            contactsRecyclerView.adapter = adapter

            addButton.setOnClickListener {
                val name = contactInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    addContact(name)
                    contactInput.text.clear()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "setupContactSelection error", e)
        }
    }

    private fun updateContactsList() {
        try {
            val adapter = findViewById<RecyclerView>(R.id.contactsRecyclerView).adapter as? ContactAdapter
            adapter?.updateContacts(allowedContacts.toList())
        } catch (e: Exception) {
            Log.e(TAG, "updateContactsList error", e)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "通知权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "通知权限被拒绝，应用可能无法正常工作", Toast.LENGTH_LONG).show()
            }
        }
    }
}

class ContactAdapter(
    private var contacts: List<String>,
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.contactNameTextView)
        val deleteButton: ImageButton = view.findViewById(R.id.deleteContactButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        try {
            val contact = contacts[position]
            holder.nameTextView.text = contact
            holder.deleteButton.setOnClickListener { onDeleteClick(contact) }
        } catch (e: Exception) {
            android.util.Log.e("ContactAdapter", "onBindViewHolder error", e)
        }
    }

    override fun getItemCount() = contacts.size

    fun updateContacts(newContacts: List<String>) {
        contacts = newContacts
        notifyDataSetChanged()
    }
}