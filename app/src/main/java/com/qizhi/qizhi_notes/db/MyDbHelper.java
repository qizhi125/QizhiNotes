package com.qizhi.qizhi_notes.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log; // Added for logging

import com.qizhi.qizhi_notes.bean.MemoBean;

import java.util.ArrayList;
import java.util.List;

public class MyDbHelper extends SQLiteOpenHelper {

    private static final String TAG = "MyDbHelper"; // Added for logging
    private static final String DB_NAME = "notes.db";
    private static final int DB_VERSION = 2; // Incremented version for new table

    // Memo Table
    private static final String TABLE_MEMO = "memo";
    private static final String COL_ID = "id";
    private static final String COL_TITLE = "title";
    private static final String COL_CONTENT = "content";
    private static final String COL_IMG_PATH = "imgPath"; // Keep column name consistent
    private static final String COL_CREATE_TIME = "createTime";

    // User Table (New)
    private static final String TABLE_USERS = "users";
    private static final String COL_USER_ID = "user_id";
    private static final String COL_USERNAME = "username";
    private static final String COL_PASSWORD = "password"; // Insecure: Store hashed passwords in production!

    // SQL to create memo table
    private static final String CREATE_MEMO_TABLE = "CREATE TABLE " + TABLE_MEMO + " (" +
            COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_TITLE + " TEXT, " +
            COL_CONTENT + " TEXT, " +
            COL_IMG_PATH + " TEXT, " +  // Keep column name consistent
            COL_CREATE_TIME + " TEXT)";

    // SQL to create users table (New)
    private static final String CREATE_USERS_TABLE = "CREATE TABLE " + TABLE_USERS + " (" +
            COL_USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_USERNAME + " TEXT UNIQUE NOT NULL, " + // Username should be unique and not null
            COL_PASSWORD + " TEXT NOT NULL)"; // Password should not be null

    public MyDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.i(TAG, "Creating database tables..."); // Added logging
        db.execSQL(CREATE_MEMO_TABLE);
        db.execSQL(CREATE_USERS_TABLE); // Create users table
        Log.i(TAG, "Database tables created."); // Added logging
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion); // Added logging
        if (oldVersion < 2) {
            // If upgrading from version 1, only the users table needs to be added.
            try {
                db.execSQL(CREATE_USERS_TABLE);
                Log.i(TAG, "Successfully added users table during upgrade.");
            } catch (Exception e) {
                Log.e(TAG, "Error adding users table during upgrade, dropping all tables.", e);
                // Fallback if adding fails (e.g., table somehow exists)
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_MEMO);
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
                onCreate(db);
            }
        } else {
            // For other future upgrades, drop and recreate (simple but destructive)
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_MEMO);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
            onCreate(db);
        }
        Log.w(TAG, "Database upgrade complete."); // Added logging
    }

    // --- Memo Operations ---

    public long insertMemo(MemoBean memo) {
        SQLiteDatabase db = this.getWritableDatabase();
        long id = -1;
        try {
            ContentValues values = new ContentValues();
            values.put(COL_TITLE, memo.getTitle());
            values.put(COL_CONTENT, memo.getContent());
            values.put(COL_IMG_PATH, memo.getImgPath());
            values.put(COL_CREATE_TIME, memo.getCreateTime());
            id = db.insertOrThrow(TABLE_MEMO, null, values); // Use insertOrThrow
            Log.d(TAG, "Inserted memo with id: " + id);
        } catch (Exception e) {
            Log.e(TAG, "Error inserting memo", e);
            id = -1; // Ensure failure is indicated
        } finally {
            db.close(); // Close db after operation
        }
        return id;
    }

    public List<MemoBean> getAllMemos() {
        List<MemoBean> memoList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String selectQuery = "SELECT " + COL_ID + ", " + COL_TITLE + ", " + COL_CONTENT + ", " +
                COL_IMG_PATH + ", " + COL_CREATE_TIME + " FROM " + TABLE_MEMO +
                " ORDER BY " + COL_CREATE_TIME + " DESC"; // Order by time descending

        Cursor cursor = null;
        try {
            cursor = db.rawQuery(selectQuery, null);
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    MemoBean memo = new MemoBean();
                    // Use getColumnIndexOrThrow for robustness
                    memo.setId(cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID)));
                    memo.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)));
                    memo.setContent(cursor.getString(cursor.getColumnIndexOrThrow(COL_CONTENT)));
                    memo.setImgPath(cursor.getString(cursor.getColumnIndexOrThrow(COL_IMG_PATH)));
                    memo.setCreateTime(cursor.getString(cursor.getColumnIndexOrThrow(COL_CREATE_TIME)));
                    memoList.add(memo);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error while getting all memos", e);
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
            // Do not close the database here if called from background thread frequently
            // Let the caller manage closing if needed, or rely on SQLiteOpenHelper lifecycle
            // db.close();
        }
        Log.d(TAG, "Retrieved " + memoList.size() + " memos.");
        return memoList;
    }


    public int updateMemo(MemoBean memo) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsAffected = -1;
        try {
            ContentValues values = new ContentValues();
            values.put(COL_TITLE, memo.getTitle());
            values.put(COL_CONTENT, memo.getContent());
            values.put(COL_IMG_PATH, memo.getImgPath());
            // Don't update createTime on edit
            rowsAffected = db.update(TABLE_MEMO, values, COL_ID + " = ?",
                    new String[]{String.valueOf(memo.getId())});
            Log.d(TAG, "Updated memo with id: " + memo.getId() + ". Rows affected: " + rowsAffected);
        } catch (Exception e) {
            Log.e(TAG, "Error updating memo with id: " + memo.getId(), e);
            rowsAffected = -1;
        } finally {
            db.close(); // Close db
        }
        return rowsAffected;
    }

    public int deleteMemo(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsAffected = -1;
        try {
            rowsAffected = db.delete(TABLE_MEMO, COL_ID + " = ?",
                    new String[]{String.valueOf(id)});
            Log.d(TAG, "Deleted memo with id: " + id + ". Rows affected: " + rowsAffected);
        } catch (Exception e) {
            Log.e(TAG, "Error deleting memo with id: " + id, e);
            rowsAffected = -1;
        } finally {
            db.close(); // Close db
        }
        return rowsAffected;
    }

    // --- User Operations (New) ---

    public long addUser(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            Log.w(TAG, "Attempted to add user with empty username or password.");
            return -1; // Prevent adding empty credentials
        }
        SQLiteDatabase db = this.getWritableDatabase();
        long id = -1;
        try {
            ContentValues values = new ContentValues();
            values.put(COL_USERNAME, username.trim()); // Trim whitespace
            values.put(COL_PASSWORD, password); // Store plain password (INSECURE)
            id = db.insertOrThrow(TABLE_USERS, null, values); // Use insertOrThrow to catch uniqueness constraint violation
            Log.i(TAG, "Added user '" + username.trim() + "' with id: " + id);
        } catch (android.database.sqlite.SQLiteConstraintException e) {
            Log.w(TAG, "Failed to add user '" + username.trim() + "'. Username likely already exists.", e);
            id = -2; // Indicate username exists
        } catch (Exception e) {
            Log.e(TAG, "Error adding user '" + username.trim() + "'", e);
            id = -1; // Indicate general error
        } finally {
            db.close(); // Close db
        }
        return id; // Returns new user ID, -1 on general error, -2 if username exists
    }

    public boolean checkUser(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            return false; // Invalid input
        }
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {COL_USER_ID};
        String selection = COL_USERNAME + " = ? AND " + COL_PASSWORD + " = ?";
        String[] selectionArgs = {username.trim(), password}; // Trim username
        Cursor cursor = null;
        boolean userExists = false;
        try {
            cursor = db.query(TABLE_USERS, columns, selection, selectionArgs, null, null, null);
            if (cursor != null) {
                userExists = (cursor.getCount() > 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking user '" + username.trim() + "'", e);
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close(); // Ensure cursor is closed
            }
            // db.close(); // Let caller manage closing for readable DB if needed
        }
        Log.d(TAG, "Checked user '" + username.trim() + "'. Exists and password matches: " + userExists);
        return userExists;
    }

    public boolean checkUsernameExists(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false; // Invalid input
        }
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {COL_USER_ID};
        String selection = COL_USERNAME + " = ?";
        String[] selectionArgs = {username.trim()};
        Cursor cursor = null;
        boolean exists = false;
        try {
            cursor = db.query(TABLE_USERS, columns, selection, selectionArgs, null, null, null);
            if (cursor != null) {
                exists = (cursor.getCount() > 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking if username '" + username.trim() + "' exists", e);
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
            // db.close(); // Let caller manage closing
        }
        Log.d(TAG, "Checked if username '" + username.trim() + "' exists. Result: " + exists);
        return exists;
    }
}