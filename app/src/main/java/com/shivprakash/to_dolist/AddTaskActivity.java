package com.shivprakash.to_dolist;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class AddTaskActivity extends AppCompatActivity {
    private TextInputEditText taskNameInput;
    private TextInputEditText taskDateInput;
    private TextInputEditText taskTimeInput;
    private TextInputEditText taskCategoryInput;
    private Spinner prioritySpinner;
    private TextInputEditText taskNotesInput;
    private Spinner reminderSpinner;
    private Calendar calendar;
    private String existingTaskName;
    private TaskDBHelper dbHelper;
    private SimpleDateFormat dateFormat;
    private SimpleDateFormat timeFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
       setContentView(R.layout.activity_add_task);

        dbHelper = new TaskDBHelper(this);
        calendar = Calendar.getInstance();
        existingTaskName = getIntent().getStringExtra("task");

        // Initialize date formats
        dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        // Initialize views
        taskNameInput = findViewById(R.id.task_name_input);
        taskDateInput = findViewById(R.id.task_date_input);
        taskTimeInput = findViewById(R.id.task_time_input);
        taskCategoryInput = findViewById(R.id.task_category_input);
        prioritySpinner = findViewById(R.id.priority_spinner);
        taskNotesInput = findViewById(R.id.task_notes_input);
        reminderSpinner = findViewById(R.id.reminder_spinner);
        Button saveButton = findViewById(R.id.save_button);

        // Set up spinners
        setupSpinners();

        // Set current date and time as default
        taskDateInput.setText(dateFormat.format(calendar.getTime()));
        taskTimeInput.setText(timeFormat.format(calendar.getTime()));

        // If editing existing task, load its data
        if (existingTaskName != null) {
            loadExistingTask();
        }

        // Date picker dialog
        taskDateInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePicker();
            }
        });

        // Time picker dialog
        taskTimeInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTimePicker();
            }
        });

        // Save button click listener
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveTask();
            }
        });
    }

    private void setupSpinners() {
        // Set up priority spinner
        ArrayAdapter<CharSequence> priorityAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.priorities,
                android.R.layout.simple_spinner_item
        );
        priorityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        prioritySpinner.setAdapter(priorityAdapter);

        // Set up reminder spinner
        ArrayAdapter<CharSequence> reminderAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.reminders,
                android.R.layout.simple_spinner_item
        );
        reminderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        reminderSpinner.setAdapter(reminderAdapter);
    }

    private void loadExistingTask() {
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

        String selection = TaskContract.TaskEntry.COLUMN_TASK + " = ?";
        String[] selectionArgs = {existingTaskName};
        android.database.Cursor cursor = db.query(
                TaskContract.TaskEntry.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null
        );

        if (cursor.moveToFirst()) {
            taskNameInput.setText(cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_TASK)));
            taskDateInput.setText(cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_DUE_DATE)));
            taskTimeInput.setText(cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_DUE_TIME)));
            taskCategoryInput.setText(cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_CATEGORY)));
            taskNotesInput.setText(cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_NOTES)));

            // Set priority spinner
            String priority = cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_PRIORITY));
            int priorityPosition = getPriorityPosition(priority);
            prioritySpinner.setSelection(priorityPosition);

            // Set reminder spinner
            String reminder = cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_REMINDER));
            int reminderPosition = getReminderPosition(reminder);
            reminderSpinner.setSelection(reminderPosition);
        }

        cursor.close();
        db.close();
    }

    private int getPriorityPosition(String priority) {
        String[] priorities = getResources().getStringArray(R.array.priorities);
        for (int i = 0; i < priorities.length; i++) {
            if (priorities[i].equals(priority)) {
                return i;
            }
        }
        return 0;
    }

    private int getReminderPosition(String reminder) {
        String[] reminders = getResources().getStringArray(R.array.reminders);
        for (int i = 0; i < reminders.length; i++) {
            if (reminders[i].equals(reminder)) {
                return i;
            }
        }
        return 0;
    }

    private void showDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    taskDateInput.setText(dateFormat.format(calendar.getTime()));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void showTimePicker() {
        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    calendar.set(Calendar.MINUTE, minute);
                    taskTimeInput.setText(timeFormat.format(calendar.getTime()));
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
        );
        timePickerDialog.show();
    }

    private boolean validateInputs() {
        String taskName = taskNameInput.getText().toString().trim();
        String dueDate = taskDateInput.getText().toString().trim();
        String dueTime = taskTimeInput.getText().toString().trim();

        if (taskName.isEmpty()) {
            taskNameInput.setError("Please enter a task name");
            return false;
        }

        if (dueDate.isEmpty()) {
            taskDateInput.setError("Please select a due date");
            return false;
        }

        if (dueTime.isEmpty()) {
            taskTimeInput.setError("Please select a due time");
            return false;
        }

        // Validate date and time
        try {
            // Create a combined date-time format
            SimpleDateFormat combinedFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            String dateTimeStr = dueDate + " " + dueTime;
            Date dateTime = combinedFormat.parse(dateTimeStr);
            
            // Get current time
            Calendar now = Calendar.getInstance();
            now.set(Calendar.SECOND, 0);
            now.set(Calendar.MILLISECOND, 0);
            
            // Create calendar for selected date/time
            Calendar selectedDateTime = Calendar.getInstance();
            selectedDateTime.setTime(dateTime);
            selectedDateTime.set(Calendar.SECOND, 0);
            selectedDateTime.set(Calendar.MILLISECOND, 0);
            
            // Compare timestamps
            if (selectedDateTime.getTimeInMillis() < now.getTimeInMillis()) {
                taskDateInput.setError("Due date and time cannot be in the past");
                return false;
            }
        } catch (ParseException e) {
            taskDateInput.setError("Invalid date or time format");
            return false;
        }

        return true;
    }

    private void saveTask() {
        if (!validateInputs()) {
            return;
        }

        String taskName = taskNameInput.getText().toString().trim();
        String dueDate = taskDateInput.getText().toString().trim();
        String dueTime = taskTimeInput.getText().toString().trim();
        String category = taskCategoryInput.getText().toString().trim();
        String priority = prioritySpinner.getSelectedItem().toString();
        String notes = taskNotesInput.getText().toString().trim();
        String reminder = reminderSpinner.getSelectedItem().toString();

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(TaskContract.TaskEntry.COLUMN_TASK, taskName);
        values.put(TaskContract.TaskEntry.COLUMN_DUE_DATE, dueDate);
        values.put(TaskContract.TaskEntry.COLUMN_DUE_TIME, dueTime);
        values.put(TaskContract.TaskEntry.COLUMN_CATEGORY, category);
        values.put(TaskContract.TaskEntry.COLUMN_PRIORITY, priority);
        values.put(TaskContract.TaskEntry.COLUMN_NOTES, notes);
        values.put(TaskContract.TaskEntry.COLUMN_REMINDER, reminder);

        try {
            if (existingTaskName != null) {
                // Check if task name has changed
                if (!taskName.equals(existingTaskName)) {
                    // Delete old task and insert new one
                    String deleteSelection = TaskContract.TaskEntry.COLUMN_TASK + " = ?";
                    String[] deleteArgs = {existingTaskName};
                    db.delete(TaskContract.TaskEntry.TABLE_NAME, deleteSelection, deleteArgs);

        long newRowId = db.insert(TaskContract.TaskEntry.TABLE_NAME, null, values);
                    if (newRowId != -1) {
                        Toast.makeText(this, "Task updated successfully", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                    } else {
                        Toast.makeText(this, "Error updating task", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_CANCELED);
                    }
                } else {
                    // Update existing task
                    String selection = TaskContract.TaskEntry.COLUMN_TASK + " = ?";
                    String[] selectionArgs = {existingTaskName};
                    int count = db.update(TaskContract.TaskEntry.TABLE_NAME, values, selection, selectionArgs);
                    if (count > 0) {
                        Toast.makeText(this, "Task updated successfully", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                    } else {
                        Toast.makeText(this, "Error updating task", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_CANCELED);
                    }
                }
        } else {
                // Insert new task
                values.put(TaskContract.TaskEntry.COLUMN_COMPLETED, 0); // Reset completed status
                long newRowId = db.insert(TaskContract.TaskEntry.TABLE_NAME, null, values);
                if (newRowId != -1) {
            Toast.makeText(this, "Task added successfully", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                } else {
                    Toast.makeText(this, "Error adding task", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_CANCELED);
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Database error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
        } finally {
            db.close();
        }

        finish();
    }

    @Override
    protected void onDestroy() {
        dbHelper.close();
        super.onDestroy();
    }
}


