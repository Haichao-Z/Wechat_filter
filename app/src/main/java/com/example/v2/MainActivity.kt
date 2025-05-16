package com.example.v2

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
    // 使用可变引用
    private val allowedContacts = HashSet<String>()
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
                handler.postDelayed(this, 3000) // 每3秒更新一次
            }
        }, 1000)

        showDeviceOptimizationHint()

        // 添加测试功能
        testNotificationFiltering()

        val testButton = findViewById<Button>(R.id.testButton)
        testButton.setOnClickListener {
            // 显示当前状态
            val isEnabled = isNotificationServiceEnabled()
            val message = "服务状态: ${if(isEnabled) "已启用" else "未启用"}\n" +
                    "允许的联系人: $allowedContacts\n" +
                    "若要添加测试联系人，请输入'老婆'并点击添加"

            Toast.makeText(this, message, Toast.LENGTH_LONG).show()

            // 强制刷新服务
            saveAllowedContacts()
        }

        checkDoNotDisturbPermission()
    }  // 添加右大括号关闭onCreate方法

    // 在MainActivity.kt中添加
    private fun checkDoNotDisturbPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager

            if (!notificationManager.isNotificationPolicyAccessGranted) {
                val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                startActivity(intent)
                Toast.makeText(this, "请授予「微信提醒筛选器」免打扰访问权限以控制震动", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun testNotificationFiltering() {
        val testButton = findViewById<Button>(R.id.addContactButton)
        testButton.setOnLongClickListener {
            // 长按添加按钮进入测试模式
            Toast.makeText(this, "正在测试通知过滤...", Toast.LENGTH_SHORT).show()

            // 查询当前服务状态
            val isEnabled = isNotificationServiceEnabled()
            val contactsCount = allowedContacts.size

            Toast.makeText(this,
                "服务状态: ${if(isEnabled) "已启用" else "未启用"}\n" +
                        "联系人: $contactsCount 个\n" +
                        "请检查Logcat日志以获取更多信息",
                Toast.LENGTH_LONG).show()

            true
        }
    }

    private fun showDeviceOptimizationHint() {
        val manufacturer = android.os.Build.MANUFACTURER.lowercase(java.util.Locale.getDefault())

        when {
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
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                showDialog(
                    "小米设备设置",
                    "在小米设备上，您还需要:\n" +
                            "1. 进入设置 > 应用管理 > 本应用\n" +
                            "2. 自启动 (开启)\n" +
                            "3. 省电策略 > 无限制\n" +
                            "4. 设置 > 电量和性能 > 应用省电管理 > 找到本应用并设为无限制"
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
            manufacturer.contains("vivo") -> {
                showDialog(
                    "vivo设备设置",
                    "在vivo设备上，您还需要:\n" +
                            "1. 进入设置 > 应用管理 > 本应用\n" +
                            "2. 自启动 (开启)\n" +
                            "3. 电池 > 后台高耗电 (允许)"
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
    }

    private fun showDialog(title: String, message: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message + "\n\n否则通知监听服务可能被系统关闭")
            .setPositiveButton("我知道了", null)
            .show()
    }

    private fun updateServiceStatus() {
        val statusTextView = findViewById<TextView>(R.id.titleTextView)
        if (isNotificationServiceEnabled()) {
            statusTextView.text = "微信重要联系人提醒 (服务运行中)"
            statusTextView.setTextColor(android.graphics.Color.GREEN)
        } else {
            statusTextView.text = "微信重要联系人提醒 (服务未运行)"
            statusTextView.setTextColor(android.graphics.Color.RED)
        }
    }

    private fun checkNotificationPermission() {
        val permissionButton = findViewById<Button>(R.id.permissionButton)

        // 根据授权状态更新按钮文本
        if (isNotificationServiceEnabled()) {
            permissionButton.text = "通知访问权限已授予"
            // 尝试重载服务
            try {
                val intent = Intent(this, NotificationFilterService::class.java)
                stopService(intent)
                startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            permissionButton.text = "授予通知访问权限"
        }

        permissionButton.setOnClickListener {
            if (!isNotificationServiceEnabled()) {
                // 打开系统通知访问设置页面
                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                startActivity(intent)
                Toast.makeText(this, "请允许「微信提醒筛选器」访问通知", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "通知访问权限已授予，服务正在运行", Toast.LENGTH_SHORT).show()
                // 刷新联系人列表以确保服务获取最新数据
                saveAllowedContacts()
            }
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = android.provider.Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        return flat != null && flat.contains(pkgName)
    }

    private fun loadAllowedContacts() {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val savedContacts = prefs.getStringSet("allowed_contacts", HashSet<String>())
        allowedContacts.clear() // 清空当前列表再添加
        if (savedContacts != null) {
            allowedContacts.addAll(savedContacts)
        }
        Log.d(TAG, "已加载联系人列表: $allowedContacts")
    }

    private fun saveAllowedContacts() {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = prefs.edit()

        // 记录保存前的状态
        Log.d(TAG, "保存联系人列表: $allowedContacts")

        // 保存联系人集合 - 这里需要使用新的HashSet来解决可变集合的问题
        editor.putStringSet("allowed_contacts", HashSet(allowedContacts))
        editor.apply()

        // 发送广播通知服务联系人列表已更新
        val intent = Intent(this, ContactListChangeReceiver::class.java)
        intent.action = "com.example.v2.CONTACT_LIST_CHANGED"
        sendBroadcast(intent)

        // 尝试直接重启服务
        try {
            val serviceIntent = Intent(this, NotificationFilterService::class.java)
            stopService(serviceIntent)
            startService(serviceIntent)
            Log.d(TAG, "已重启通知服务")
        } catch (e: Exception) {
            Log.e(TAG, "重启服务失败", e)
        }
    }

    // 添加联系人到允许列表
    fun addContact(contactName: String) {
        // 简化联系人名称，只保留第一部分(如果有空格)
        val simpleName = contactName.split(" ").firstOrNull() ?: contactName

        // 添加到集合
        allowedContacts.add(simpleName)

        // 保存并在日志中记录
        saveAllowedContacts()
        Log.d(TAG, "已添加联系人: $simpleName, 当前列表: $allowedContacts")

        // 更新UI
        updateContactsList()
    }

    // 从允许列表中移除联系人
    fun removeContact(contactName: String) {
        allowedContacts.remove(contactName)
        saveAllowedContacts()
        updateContactsList()
    }

    // 设置联系人选择UI
    private fun setupContactSelection() {
        // 获取UI元素引用
        val addButton = findViewById<Button>(R.id.addContactButton)
        val contactInput = findViewById<EditText>(R.id.contactNameInput)
        val contactsRecyclerView = findViewById<RecyclerView>(R.id.contactsRecyclerView)

        // 设置RecyclerView
        contactsRecyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = ContactAdapter(allowedContacts.toList()) { contactName ->
            removeContact(contactName)
        }
        contactsRecyclerView.adapter = adapter

        // 设置添加按钮点击监听器
        addButton.setOnClickListener {
            val name = contactInput.text.toString()
            if (name.isNotEmpty()) {
                addContact(name)
                contactInput.text.clear()
            }
        }
    }

    // 更新联系人列表显示
    private fun updateContactsList() {
        val adapter = findViewById<RecyclerView>(R.id.contactsRecyclerView).adapter as? ContactAdapter
        adapter?.updateContacts(allowedContacts.toList())
    }
}

// ContactAdapter类
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
        val contact = contacts[position]
        holder.nameTextView.text = contact
        holder.deleteButton.setOnClickListener { onDeleteClick(contact) }
    }

    override fun getItemCount() = contacts.size

    fun updateContacts(newContacts: List<String>) {
        contacts = newContacts
        notifyDataSetChanged()
    }
    
}
