package com.shivprakash.to_dolist;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class NotificationBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String taskName = intent.getStringExtra("task_name");
        String dueDate = intent.getStringExtra("due_date");
        String dueTime = intent.getStringExtra("due_time");
        String channelId = intent.getStringExtra("channel_id");

        // Create notification intent
        Intent notificationIntent = new Intent(context, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Task Reminder")
                .setContentText("Task: " + taskName + "\nDue: " + dueDate + " " + dueTime)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setVibrate(new long[]{100, 200, 300, 400, 500})
                .setLights(android.graphics.Color.BLUE, 3000, 3000)
                .setContentIntent(pendingIntent);

        // Get notification manager
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Show notification
        if (notificationManager != null) {
            notificationManager.notify(taskName.hashCode(), builder.build());
        }
    }
} 