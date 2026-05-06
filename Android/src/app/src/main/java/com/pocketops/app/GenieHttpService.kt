package com.pocketops.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.system.Os
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.genieapiservice.MyNativeLib

// JNI class must match the original package name baked into libJNIGenieAPIService.so
// The native functions are registered as Java_com_example_genieapiservice_MyNativeLib_*
class GenieHttpService : Service() {
    private val stateLock = Any()
    private var nativeLib: MyNativeLib? = null
    @Volatile private var serviceThread: Thread? = null
    @Volatile private var stopRequested = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nativeLibPath = applicationContext.applicationInfo.nativeLibraryDir
        try {
            Os.setenv("ADSP_LIBRARY_PATH", nativeLibPath, true)
            Os.setenv("LD_LIBRARY_PATH", nativeLibPath, true)
        } catch (e: Exception) {
            Log.e(TAG, "setenv failed", e)
        }
        Log.d(TAG, "onCreate, nativeLibPath=$nativeLibPath")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        synchronized(stateLock) {
            if (serviceThread != null || nativeLib != null) {
                Log.d(TAG, "onStartCommand: service already running, skipping")
                return START_STICKY
            }
            stopRequested = false
        }

        val modelRoot = intent?.getStringExtra("model_dir") ?: "/sdcard/GenieModels"

        val configFile = java.io.File(modelRoot).listFiles()
            ?.filter { it.isDirectory }
            ?.map { java.io.File(it, "config.json") }
            ?.firstOrNull { it.exists() }
            ?.absolutePath
            ?: "$modelRoot/config.json"

        Log.d(TAG, "onStartCommand, config=$configFile")

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            "pocketops_service",
            "PocketOps NPU Service",
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, "pocketops_service")
            .setContentText("PocketOps NPU service is running")
            .setOngoing(true)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .build()

        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

        val thread = Thread {
            val localLib = MyNativeLib()
            synchronized(stateLock) {
                nativeLib = localLib
            }

            try {
                if (stopRequested) return@Thread

                val args = arrayOf("main", "-c", configFile, "-l", "-d", "2")
                Log.d(TAG, "Starting native HTTP service...")
                localLib.runService(args)
                Log.d(TAG, "Native HTTP service exited")
            } catch (e: Exception) {
                Log.e(TAG, "Native HTTP service crashed", e)
            } finally {
                synchronized(stateLock) {
                    if (nativeLib === localLib) {
                        nativeLib = null
                    }
                    serviceThread = null
                    isRunning = false
                }

                if (!stopRequested) {
                    stopSelf()
                }
            }
        }

        synchronized(stateLock) {
            serviceThread = thread
            isRunning = true
        }
        thread.start()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRequested = true
        val localLib = synchronized(stateLock) {
            serviceThread = null
            isRunning = false
            nativeLib.also { nativeLib = null }
        }
        Thread { localLib?.stopService() }.start()
        Log.d(TAG, "onDestroy")
    }

    companion object {
        private const val TAG = "GenieHttpService"
        @Volatile var isRunning = false
    }
}
