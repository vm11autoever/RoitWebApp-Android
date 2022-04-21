package net.smartpmis.roit
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
//import androidx.work.OneTimeWorkRequest
//import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

//import com.google.firebase.quickstart.fcm.R

class MyFirebaseMessagingService : FirebaseMessagingService() {

// FCM Push는
// Background일 때 별도 조치 없이 noti에 나타나고
// Foreground일 때 여기서 아래에서 noti를 임의로 발생시켜 줘야한다.


    //     #C-2. Foreground 푸시 MyFirebaseMessagingService
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        var title: String? = ""
        var message: String? = ""

        var push_url: String? = ""
        var body: String? = ""

        val from = remoteMessage.from
        if (0 < remoteMessage.data.size) {
            push_url = remoteMessage.data["push_url"]
            body = remoteMessage.data["body"]
        }
        if (null != remoteMessage.notification) {
            title = remoteMessage.notification!!.title
            message = remoteMessage.notification!!.body
        }
        Log.d("vm11","push_url : "+push_url);

//        Log.d("vm11",""+message["url"])
//        Log.d("vm11",url+"  "+body)
//        message = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
//        sendNotification(link, title, "상세내용") //아마 noti를 눌렀을떄 발생을 위해 처리한듯.
        sendNotification(push_url, title, "onMessageReceived에서 처리되는 중.") //아마 noti를 눌렀을떄 발생을 위해 처리한듯.
    }

    private fun sendNotification(link: String?, title: String?, message: String?) {
        val notificationId = System.currentTimeMillis().toInt()
        val channelId = applicationContext.packageName
        val channelName = applicationContext.packageName
        val params = Intent(applicationContext, MainActivity::class.java)
//        params.putExtra("link-case", link)
        params.putExtra("push_url", link)
        var str = params.getStringExtra("push_url")
        Log.d("vm11","push_url : "+str)
        params.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)

        val intent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            params,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(
            applicationContext, channelId
        )
            .setSmallIcon(R.mipmap.ic_launcher)
//            .setSound()
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(intent)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}