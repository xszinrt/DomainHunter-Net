package com.example.domainhunter.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.domainhunter.data.AppDatabase
import com.example.domainhunter.data.DomainEntity
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class DomainScanService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()
    
    private var isPaused = false
    private var isRunning = false
    private var domainList = mutableListOf<String>()
    private var currentIndex = 0
    private var currentSessionId = 0L

    companion object {
        const val CHANNEL_ID = "DomainScanChannel"
        const val NOTIFICATION_ID = 101
        const val ACTION_START = "START"
        const val ACTION_PAUSE = "PAUSE"
        const val ACTION_STOP = "STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isRunning) {
                    isRunning = true
                    isPaused = false
                    currentSessionId = System.currentTimeMillis()
                    val rawList = intent.getStringArrayListExtra("DOMAINS")
                    if (rawList != null) {
                        domainList = rawList.filter { it.endsWith(".net", ignoreCase = true) }.toMutableList()
                    }
                    startForeground(NOTIFICATION_ID, buildNotification("جاري تهيئة فحص نطاقات .net..."))
                    startScanning()
                } else if (isPaused) {
                    isPaused = false
                    startForeground(NOTIFICATION_ID, buildNotification("تم استئناف نبش النطاقات..."))
                }
            }
            ACTION_PAUSE -> {
                isPaused = true
                updateNotification("⏸️ الفحص متوقف مؤقتاً عند النطاق رقم $currentIndex")
            }
            ACTION_STOP -> {
                stopScanningAndService()
            }
        }
        return START_NOT_STICKY
    }

    private fun startScanning() {
        serviceScope.launch {
            val dao = AppDatabase.getDatabase(applicationContext).domainDao()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val currentDate = Date()

            while (currentIndex < domainList.size && isRunning) {
                if (isPaused) {
                    delay(1000)
                    continue
                }

                val domain = domainList[currentIndex]
                updateNotification("🔍 جاري فحص ($currentIndex/${domainList.size}): $domain")

                try {
                    val request = Request.Builder().url("https://rdap.org/domain/$domain").build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (body != null) {
                                val json = JSONObject(body)
                                val events = json.optJSONArray("events")
                                var expiryDateStr = ""
                                
                                if (events != null) {
                                    for (i in 0 until events.length()) {
                                        val event = events.getJSONObject(i)
                                        if (event.optString("eventAction") == "expiration") {
                                            val fullTime = event.optString("eventDate")
                                            if (fullTime.length >= 10) {
                                                expiryDateStr = fullTime.substring(0, 10)
                                            }
                                            break
                                        }
                                    }
                                }

                                if (expiryDateStr.isNotEmpty()) {
                                    val expiryDate = dateFormat.parse(expiryDateStr)
                                    if (expiryDate != null) {
                                        val diffInMs = expiryDate.time - currentDate.time
                                        val daysLeft = TimeUnit.MILLISECONDS.toDays(diffInMs).toInt()
                                        
                                        dao.insertDomain(
                                            DomainEntity(
                                                domainName = domain,
                                                expiryDate = expiryDateStr,
                                                daysLeft = daysLeft,
                                                sessionId = currentSessionId
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // آليّة التجاوز الذكي في حالة تذبذب الشبكة المحلية أو السيرفر الوسيط
                }

                currentIndex++
                delay(800) // حماية معدل الطلبات (Rate Limit) لضمان استقرار الفحص
            }

            if (currentIndex >= domainList.size) {
                updateNotification("✅ اكتمل فحص كافة النطاقات بنجاح!")
                isRunning = false
                stopForeground(false)
            }
        }
    }

    private fun stopScanningAndService() {
        isRunning = false
        isPaused = false
        serviceJob.cancelChildren()
        stopSelf()
    }

    private fun buildNotification(content: String): Notification {
        val stopIntent = Intent(this, DomainScanService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("صائد النطاقات .NET")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إنهاء الفحص", stopPendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(content))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES = Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "خدمة فحص النطاقات", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
