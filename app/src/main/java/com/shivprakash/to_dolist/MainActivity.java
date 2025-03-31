package com.shivprakash.to_dolist;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.content.ContentValues;
import android.os.Bundle;
import android.view.View;
import android.database.sqlite.SQLiteDatabase;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import androidx.annotation.Nullable;

public class MainActivity extends AppCompatActivity {
    private static final int NOTIFICATION_PERMISSION_CODE = 123;
    private static final int ADD_TASK_REQUEST = 1;
    private static final int EDIT_TASK_REQUEST = 2;
    private static final String PREF_DARK_THEME = "dark_theme";
    private static final String CHANNEL_ID = "task_reminder_channel";
    private static final String CHANNEL_NAME = "Task Reminders";
    private static final String CHANNEL_DESCRIPTION = "Notifications for task reminders";
    private TaskDBHelper dbHelper;
    private List<Data> taskData;
    private TaskAdapter adapter;
    private RecyclerView recyclerView;
    private FloatingActionButton fabAddTask;
    private FloatingActionButton fabThemeToggle;
    private SharedPreferences preferences;
    private AlarmManager alarmManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        preferences = getSharedPreferences("theme_prefs", MODE_PRIVATE);
        boolean darkTheme = preferences.getBoolean(PREF_DARK_THEME, false);
        AppCompatDelegate.setDefaultNightMode(darkTheme ? 
            AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Create notification channel
        createNotificationChannel();

        // Request notification permission for Android 13 and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE);
            }
        }

        taskData = new ArrayList<>();
        dbHelper = new TaskDBHelper(this);
        fabAddTask = findViewById(R.id.fab_add_task);
        fabThemeToggle = findViewById(R.id.fab_theme_toggle);

        loadTasksFromSQLite(taskData);

        recyclerView = findViewById(R.id.recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TaskAdapter(this, taskData);
        recyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener(new TaskAdapter.OnItemClickListener() {
            @Override
            public void onEditClick(int position) {
                Intent intent = new Intent(MainActivity.this, AddTaskActivity.class);
                intent.putExtra("task", taskData.get(position).getName());
                startActivityForResult(intent, EDIT_TASK_REQUEST);
            }

            @Override
            public void onDeleteClick(int position) {
                deleteTask(position);
            }

            @Override
            public void onCheckboxClick(int position) {
                markTaskAsComplete(position);
            }
        });

        fabAddTask.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, AddTaskActivity.class);
                startActivityForResult(intent, ADD_TASK_REQUEST);
            }
        });

        fabThemeToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean darkTheme = preferences.getBoolean(PREF_DARK_THEME, false);
                preferences.edit().putBoolean(PREF_DARK_THEME, !darkTheme).apply();
                AppCompatDelegate.setDefaultNightMode(!darkTheme ? 
                    AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
                recreate();
            }
        });

        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
    }

    private void loadTasksFromSQLite(List<Data> data) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TaskContract.TaskEntry.TABLE_NAME, null);

        while (cursor.moveToNext()) {
            @SuppressLint("Range") String taskName = cursor.getString(cursor.getColumnIndex(TaskContract.TaskEntry.COLUMN_TASK));
            @SuppressLint("Range") String dueDate = cursor.getString(cursor.getColumnIndex(TaskContract.TaskEntry.COLUMN_DUE_DATE));
            @SuppressLint("Range") String dueTime = cursor.getString(cursor.getColumnIndex(TaskContract.TaskEntry.COLUMN_DUE_TIME));
            @SuppressLint("Range") String category = cursor.getString(cursor.getColumnIndex(TaskContract.TaskEntry.COLUMN_CATEGORY));
            @SuppressLint("Range") String priority = cursor.getString(cursor.getColumnIndex(TaskContract.TaskEntry.COLUMN_PRIORITY));
            @SuppressLint("Range") String notes = cursor.getString(cursor.getColumnIndex(TaskContract.TaskEntry.COLUMN_NOTES));
            @SuppressLint("Range") String reminder = cursor.getString(cursor.getColumnIndex(TaskContract.TaskEntry.COLUMN_REMINDER));
            @SuppressLint("Range") int completed = cursor.getInt(cursor.getColumnIndex(TaskContract.TaskEntry.COLUMN_COMPLETED));

            Data taskData = new Data(taskName, dueDate, dueTime, category, priority, notes, reminder);
            taskData.setCompleted(completed == 1);
            data.add(taskData);
            
            // Schedule notification for the task only if it's not completed
            if (!taskData.isCompleted()) {
                scheduleNotification(taskName, dueDate, dueTime, reminder);
            }
        }

        cursor.close();
        db.close();
    }

    private void markTaskAsComplete(int position) {
        try {
            Data task = taskData.get(position);
            String taskName = task.getName();
            boolean newCompletedState = !task.isCompleted();
            
            // Update the database first
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(TaskContract.TaskEntry.COLUMN_COMPLETED, newCompletedState ? 1 : 0);
            
            int count = db.update(
                TaskContract.TaskEntry.TABLE_NAME,
                values,
                TaskContract.TaskEntry.COLUMN_TASK + " = ?",
                new String[]{taskName}
            );
            db.close();

            if (count > 0) {
                // Update the model
                task.setCompleted(newCompletedState);
                
                // Handle notifications
                if (newCompletedState) {
                    // Cancel notification for completed task
                    Intent intent = new Intent(this, NotificationBroadcastReceiver.class);
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        this,
                        taskName.hashCode(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    );
                    if (alarmManager != null) {
                        alarmManager.cancel(pendingIntent);
                    }
                } else {
                    // Reschedule notification for uncompleted task
                    scheduleNotification(task.getName(), task.getDate(), task.getTime(), task.getReminder());
                }

                // Post UI updates to main thread after layout computation
                recyclerView.post(() -> {
                    // Update the UI
                    adapter.notifyItemChanged(position);
                    
                    // Show success message
                    Toast.makeText(MainActivity.this, 
                        newCompletedState ? "Task marked as complete" : "Task marked as incomplete",
                        Toast.LENGTH_SHORT).show();
                });
            }
        } catch (Exception e) {
            // Post error message to main thread
            recyclerView.post(() -> {
                Toast.makeText(MainActivity.this, "Error updating task: " + e.getMessage(), 
                    Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void deleteTask(int position) {
        String taskName = taskData.get(position).getName();
        
        // Delete from database
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(TaskContract.TaskEntry.TABLE_NAME, 
                TaskContract.TaskEntry.COLUMN_TASK + " = ?", 
                new String[]{taskName});
        db.close();

        // Remove from list and update UI
        taskData.remove(position);
        adapter.notifyItemRemoved(position);
        Toast.makeText(this, "Task deleted", Toast.LENGTH_SHORT).show();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(CHANNEL_DESCRIPTION);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{100, 200, 300, 400, 500});
            channel.enableLights(true);
            channel.setLightColor(android.graphics.Color.BLUE);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void scheduleNotification(String taskName, String dueDate, String dueTime, String reminder) {
        try {
            // Parse the date and time
            SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            Date taskDueDateTime = format.parse(dueDate + " " + dueTime);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(taskDueDateTime);

            // Adjust the time based on reminder setting
            switch (reminder) {
                case "15 minutes before":
                    calendar.add(Calendar.MINUTE, -15);
                    break;
                case "1 hour before":
                    calendar.add(Calendar.HOUR, -1);
                    break;
                case "1 day before":
                    calendar.add(Calendar.DAY_OF_MONTH, -1);
                    break;
                case "2 days before":
                    calendar.add(Calendar.DAY_OF_MONTH, -2);
                    break;
                case "No reminder":
                    return; // Don't schedule notification
            }

            // Check if the time is in the past
            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                return; // Don't schedule if the time has already passed
            }

            // Create intent for notification
        Intent intent = new Intent(this, NotificationBroadcastReceiver.class);
        intent.putExtra("task_name", taskName);
            intent.putExtra("due_date", dueDate);
            intent.putExtra("due_time", dueTime);
            intent.putExtra("channel_id", CHANNEL_ID);

            // Create pending intent
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                taskName.hashCode(),
                intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

            // Schedule the alarm
        if (alarmManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (!alarmManager.canScheduleExactAlarms()) {
                        Toast.makeText(this, "Cannot schedule exact alarms", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
                Toast.makeText(this, "Notification scheduled for " + taskName + " at " + 
                    format.format(calendar.getTime()), Toast.LENGTH_SHORT).show();
            }
        } catch (ParseException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error scheduling notification: " + e.getMessage(), 
                Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        dbHelper.close();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ADD_TASK_REQUEST && resultCode == RESULT_OK) {
            // Refresh the task list
            loadTasks();
            // Show success message
            Toast.makeText(this, "Task saved successfully", Toast.LENGTH_SHORT).show();
        } else if (requestCode == EDIT_TASK_REQUEST && resultCode == RESULT_OK) {
            // Refresh the task list
            loadTasks();
            // Show success message
            Toast.makeText(this, "Task updated successfully", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadTasks() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String[] projection = {
            TaskContract.TaskEntry.COLUMN_TASK,
            TaskContract.TaskEntry.COLUMN_DUE_DATE,
            TaskContract.TaskEntry.COLUMN_DUE_TIME,
            TaskContract.TaskEntry.COLUMN_CATEGORY,
            TaskContract.TaskEntry.COLUMN_PRIORITY,
            TaskContract.TaskEntry.COLUMN_NOTES,
            TaskContract.TaskEntry.COLUMN_REMINDER,
            TaskContract.TaskEntry.COLUMN_COMPLETED
        };

        String sortOrder = TaskContract.TaskEntry.COLUMN_DUE_DATE + " ASC";
        Cursor cursor = db.query(
            TaskContract.TaskEntry.TABLE_NAME,
            projection,
            null,
            null,
            null,
            null,
            sortOrder
        );

        List<Data> newTaskList = new ArrayList<>();
        while (cursor.moveToNext()) {
            String taskName = cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_TASK));
            String dueDate = cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_DUE_DATE));
            String dueTime = cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_DUE_TIME));
            String category = cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_CATEGORY));
            String priority = cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_PRIORITY));
            String notes = cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_NOTES));
            String reminder = cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_REMINDER));
            int completed = cursor.getInt(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_COMPLETED));

            Data taskData = new Data(taskName, dueDate, dueTime, category, priority, notes, reminder);
            taskData.setCompleted(completed == 1);
            newTaskList.add(taskData);
            
            // Schedule notification for the task only if it's not completed
            if (!taskData.isCompleted()) {
                scheduleNotification(taskName, dueDate, dueTime, reminder);
            }
        }

        cursor.close();
        db.close();

        // Update the task list and adapter
        taskData.clear();
        taskData.addAll(newTaskList);
        adapter.updateTasks(taskData);
    }

    public static class Data {
        private String name, date, time, category, priority, notes, reminder;
        private boolean completed;

        public Data(String name, String date, String time, String category, String priority, String notes, String reminder) {
            this.name = name;
            this.date = date;
            this.time = time;
            this.category = category;
            this.priority = priority;
            this.notes = notes;
            this.reminder = reminder;
            this.completed = false;
        }

        public String getName() {
            return name;
        }

        public String getDate() {
            return date;
        }

        public String getTime() {
            return time;
        }

        public String getCategory() {
            return category;
        }

        public String getPriority() {
            return priority;
        }

        public String getNotes() {
            return notes;
        }

        public String getReminder() {
            return reminder;
        }

        public boolean isCompleted() {
            return completed;
        }

        public void setCompleted(boolean completed) {
            this.completed = completed;
        }
    }

    private void updateTaskCompletion(String taskName, boolean isCompleted) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(TaskContract.TaskEntry.COLUMN_COMPLETED, isCompleted ? 1 : 0);

        String selection = TaskContract.TaskEntry.COLUMN_TASK + " = ?";
        String[] selectionArgs = {taskName};

        try {
            int count = db.update(TaskContract.TaskEntry.TABLE_NAME, values, selection, selectionArgs);
            if (count > 0) {
                // Refresh the task list
                loadTasks();
                Toast.makeText(this, isCompleted ? "Task marked as completed" : "Task marked as incomplete", 
                    Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Error updating task status", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Database error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            db.close();
        }
    }
}