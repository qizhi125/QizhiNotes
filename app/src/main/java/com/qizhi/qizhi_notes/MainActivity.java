package com.qizhi.qizhi_notes;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log; // Added for logging
import android.view.View; // Needed for View.GONE/VISIBLE
import android.widget.ProgressBar; // Added progress bar
import android.widget.TextView; // Added empty state text view
import android.widget.Toast; // For showing messages


import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.qizhi.qizhi_notes.adapter.MemoAdapter;
import com.qizhi.qizhi_notes.bean.MemoBean;
import com.qizhi.qizhi_notes.db.MyDbHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements MemoAdapter.OnItemClickListener {

    private static final String TAG = "MainActivity"; // Added for logging

    private RecyclerView recyclerView;
    private FloatingActionButton fabAdd;
    private MemoAdapter adapter;
    private List<MemoBean> memoList = new ArrayList<>();
    private MyDbHelper dbHelper;

    // --- Added for Background Tasks & UI Updates ---
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ProgressBar progressBar; // Added
    private TextView tvEmptyState; // Added for empty list message
    // ---------------------------------------------

    // Activity Result Launcher for Add/Edit Activity
    private final ActivityResultLauncher<Intent> addEditMemoLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // Data might have changed, refresh the list
                    Log.d(TAG, "Returned from Add/Edit, refreshing list.");
                    loadMemos(); // Reload data from DB
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Theme handles background now
        setContentView(R.layout.activity_main);

        dbHelper = new MyDbHelper(this);

        recyclerView = findViewById(R.id.recyclerView);
        fabAdd = findViewById(R.id.fab_add);
        progressBar = findViewById(R.id.main_progress_bar); // Find ProgressBar
        tvEmptyState = findViewById(R.id.tv_empty_state);   // Find Empty State TextView


        setupRecyclerView();

        fabAdd.setOnClickListener(v -> {
            Log.d(TAG, "FAB clicked, starting AddInfoActivity for new memo.");
            Intent intent = new Intent(MainActivity.this, AddInfoActivity.class);
            addEditMemoLauncher.launch(intent); // Use the launcher
        });

        // Load memos initially
        loadMemos();
    }

    private void setupRecyclerView() {
        adapter = new MemoAdapter(memoList, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    // --- Modified to run DB operations in background ---
    private void loadMemos() {
        showProgress(true); // Show progress bar
        Log.d(TAG, "Starting to load memos from database...");
        executorService.execute(() -> {
            // Background task
            List<MemoBean> loadedMemos = null;
            try {
                loadedMemos = dbHelper.getAllMemos();
            } catch (Exception e) {
                Log.e(TAG, "Error loading memos from DB", e);
                // Post error message back to UI thread
                mainHandler.post(() -> {
                    showProgress(false);
                    Toast.makeText(MainActivity.this, R.string.main_toast_load_error, Toast.LENGTH_LONG).show();
                    checkEmptyState(); // Still check empty state even on error
                });
                return; // Exit background task
            }

            final List<MemoBean> finalLoadedMemos = loadedMemos;
            mainHandler.post(() -> {
                // UI update on main thread
                memoList.clear();
                if (finalLoadedMemos != null) {
                    memoList.addAll(finalLoadedMemos);
                }
                adapter.notifyDataSetChanged(); // Update adapter
                showProgress(false); // Hide progress bar
                checkEmptyState(); // Show message if list is empty
                Log.d(TAG, "Finished loading memos. Count: " + memoList.size());
            });
        });
    }

    private void deleteMemoInBackground(final int memoId, final int position) {
        showProgress(true); // Show progress during delete
        Log.d(TAG, "Starting to delete memo with ID: " + memoId);
        executorService.execute(() -> {
            // Background task
            int rowsAffected = -1;
            try {
                rowsAffected = dbHelper.deleteMemo(memoId);
            } catch (Exception e) {
                Log.e(TAG, "Error deleting memo from DB", e);
                // Post error back to UI thread
                mainHandler.post(() -> {
                    showProgress(false);
                    Toast.makeText(MainActivity.this, R.string.memo_deleted_fail, Toast.LENGTH_SHORT).show();
                });
                return; // Exit background task
            }

            final int finalRowsAffected = rowsAffected;
            mainHandler.post(() -> {
                // UI update on main thread
                showProgress(false); // Hide progress
                if (finalRowsAffected > 0) {
                    // Successfully deleted from DB, now remove from list and notify adapter
                    if (position >= 0 && position < memoList.size()) {
                        // Verify if the item at the position still matches the ID before removing
                        if(memoList.get(position).getId() == memoId) {
                            memoList.remove(position);
                            adapter.notifyItemRemoved(position);
                            // Optional: Notify item range changed if positions shift
                            adapter.notifyItemRangeChanged(position, memoList.size());
                            Log.d(TAG, "Memo deleted successfully from UI list.");
                        } else {
                            Log.w(TAG, "Memo ID mismatch at position " + position + " after delete. Reloading list.");
                            loadMemos(); // Data inconsistency, reload
                        }
                        checkEmptyState(); // Check if list became empty
                    } else {
                        Log.w(TAG, "Delete successful in DB, but position " + position + " was invalid for UI update. Reloading list.");
                        loadMemos(); // Fallback to reload the whole list if position is wrong
                    }
                    Toast.makeText(MainActivity.this, R.string.memo_deleted_success, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, R.string.memo_deleted_fail, Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed to delete memo with ID: " + memoId + " from database (rowsAffected=0).");
                }
            });
        });
    }

    // --- Implementation of MemoAdapter.OnItemClickListener ---
    @Override
    public void onItemClick(int position) {
        if (position >= 0 && position < memoList.size()) {
            MemoBean selectedMemo = memoList.get(position);
            Log.d(TAG, "Item clicked at position: " + position + ", Memo ID: " + selectedMemo.getId());
            Intent intent = new Intent(MainActivity.this, AddInfoActivity.class);
            intent.putExtra(AddInfoActivity.EXTRA_MEMO_ID, selectedMemo.getId());
            intent.putExtra(AddInfoActivity.EXTRA_MEMO_TITLE, selectedMemo.getTitle());
            intent.putExtra(AddInfoActivity.EXTRA_MEMO_CONTENT, selectedMemo.getContent());
            intent.putExtra(AddInfoActivity.EXTRA_MEMO_IMG_PATH, selectedMemo.getImgPath());
            addEditMemoLauncher.launch(intent); // Use the launcher for editing too
        } else {
            Log.w(TAG, "Invalid position clicked: " + position);
        }
    }

    @Override
    public void onItemLongClick(int position) {
        if (position >= 0 && position < memoList.size()) {
            final int memoIdToDelete = memoList.get(position).getId();
            final String memoTitle = memoList.get(position).getTitle(); // Get title for confirmation
            Log.d(TAG, "Item long clicked at position: " + position + ", Memo ID: " + memoIdToDelete);

            // Use the note title in the confirmation message if available
            String message = getString(R.string.confirm_delete_message) + "\n\n\"" + memoTitle + "\"";

            new AlertDialog.Builder(this)
                    .setTitle(R.string.confirm_delete_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                        // User confirmed delete
                        deleteMemoInBackground(memoIdToDelete, position);
                    })
                    .setNegativeButton(R.string.cancel, null) // Just dismiss
                    .show();
        } else {
            Log.w(TAG, "Invalid position long clicked: " + position);
        }
    }
    // ---------------------------------------------------------


    // --- Helper methods for Progress Bar and Empty State ---
    private void showProgress(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        // If showing progress, hide RecyclerView and empty state text
        // If not showing progress, RecyclerView visibility depends on checkEmptyState
        if (recyclerView != null && show) {
            recyclerView.setVisibility(View.GONE);
        }
        if (tvEmptyState != null) {
            tvEmptyState.setVisibility(View.GONE); // Always hide empty state when progress shows
        }

    }

    private void checkEmptyState() {
        if (tvEmptyState != null && adapter != null && recyclerView != null) {
            boolean isEmpty = adapter.getItemCount() == 0;
            // Only show empty text if progress is not showing
            if (! (progressBar != null && progressBar.getVisibility() == View.VISIBLE) ) {
                tvEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            } else {
                // If progress is showing, both should be hidden
                tvEmptyState.setVisibility(View.GONE);
                recyclerView.setVisibility(View.GONE);
            }
            Log.d(TAG, "Checked empty state. Is empty: " + isEmpty);
        }
    }
    // ------------------------------------------------------


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Shutdown executor when activity is destroyed
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        // Close the database helper if it's open
        // dbHelper is opened/closed within its methods now, less critical here
        // if (dbHelper != null) {
        //     dbHelper.close();
        // }
        // Shutdown adapter executor if needed
        if (adapter != null) {
            adapter.shutdownExecutor();
        }
    }
}