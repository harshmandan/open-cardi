package ink.harsh.opencardi.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import ink.harsh.opencardi.R

/**
 * Keeps the BLE link alive when no UI is on screen. Android Auto can start the
 * car app without anyone opening the phone app, and the phone screen sleeps
 * while driving — without a foreground service the process gets killed and the
 * lights stop responding mid-drive.
 */
class CardiService : Service() {

	override fun onCreate() {
		super.onCreate()
		CardiController.init(this)
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		startForeground(
			NOTIFICATION_ID,
			buildNotification(),
			ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
		)
		CardiController.connect()
		return START_STICKY
	}

	override fun onDestroy() {
		CardiController.disconnect()
		super.onDestroy()
	}

	override fun onBind(intent: Intent?): IBinder? = null

	private fun buildNotification(): Notification {
		val manager = getSystemService(NotificationManager::class.java)
		manager.createNotificationChannel(
			NotificationChannel(
				CHANNEL_ID,
				getString(R.string.app_name),
				NotificationManager.IMPORTANCE_LOW,
			)
		)
		return Notification.Builder(this, CHANNEL_ID)
			.setContentTitle(getString(R.string.app_name))
			.setContentText(getString(R.string.service_running))
			.setSmallIcon(android.R.drawable.ic_menu_manage)
			.setOngoing(true)
			.build()
	}

	companion object {
		private const val CHANNEL_ID = "cardi-link"
		private const val NOTIFICATION_ID = 1

		fun start(context: Context) {
			context.startForegroundService(Intent(context, CardiService::class.java))
		}

		fun stop(context: Context) {
			context.stopService(Intent(context, CardiService::class.java))
		}
	}
}
