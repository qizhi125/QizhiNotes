package com.qizhi.qizhi_notes.adapter;

import android.content.Context; // Added for content resolver
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log; // Added
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.qizhi.qizhi_notes.AddInfoActivity; // For bitmap scaling helper static method
import com.qizhi.qizhi_notes.R;
import com.qizhi.qizhi_notes.bean.MemoBean;

import java.io.File; // Added
import java.io.FileNotFoundException; // Added
import java.io.IOException; // Added
import java.io.InputStream; // Added
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MemoAdapter extends RecyclerView.Adapter<MemoAdapter.MemoViewHolder> {

    private static final String TAG = "MemoAdapter"; // Added

    private List<MemoBean> memoList;
    private OnItemClickListener listener;

    // --- Added for background image loading ---
    private final ExecutorService imageLoadExecutor = Executors.newFixedThreadPool(3); // Pool for image loading
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // -----------------------------------------

    public interface OnItemClickListener {
        void onItemClick(int position);
        void onItemLongClick(int position); // Added for delete functionality
    }

    public MemoAdapter(List<MemoBean> memoList, OnItemClickListener listener) {
        this.memoList = memoList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MemoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recy_item, parent, false);
        return new MemoViewHolder(view, listener); // Pass listener to ViewHolder
    }

    @Override
    public void onBindViewHolder(@NonNull MemoViewHolder holder, int position) {
        MemoBean memo = memoList.get(position);
        holder.tvTitle.setText(memo.getTitle());
        holder.tvContent.setText(memo.getContent()); // Show full content or preview? Decide here.

        // Load image in background
        loadImageAsync(holder.ivImage, memo.getImgPath());
    }

    @Override
    public int getItemCount() {
        return memoList != null ? memoList.size() : 0;
    }

    // --- ViewHolder Class ---
    static class MemoViewHolder extends RecyclerView.ViewHolder {
        ImageView ivImage;
        TextView tvTitle;
        TextView tvContent;

        MemoViewHolder(@NonNull View itemView, final OnItemClickListener listener) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.iv_image); // Ensure ID matches recy_item.xml
            tvTitle = itemView.findViewById(R.id.tv_title); // Ensure ID matches
            tvContent = itemView.findViewById(R.id.tv_content); // Ensure ID matches

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getBindingAdapterPosition(); // Use getBindingAdapterPosition()
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onItemClick(position);
                    }
                }
            });

            // --- Added Long Click Listener ---
            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    int position = getBindingAdapterPosition(); // Use getBindingAdapterPosition()
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onItemLongClick(position);
                        return true; // Consume the long click
                    }
                }
                return false;
            });
            // -------------------------------
        }
    }

    // --- Background Image Loading Method ---
    private void loadImageAsync(ImageView imageView, String imagePathOrUriString) {
        // Set placeholder initially and manage visibility
        imageView.setImageResource(R.drawable.ic_image_24); // Default placeholder
        imageView.setVisibility(View.GONE); // Hide image view initially or if no path
        imageView.setTag(null); // Clear tag initially

        if (imagePathOrUriString != null && !imagePathOrUriString.isEmpty()) {
            imageView.setVisibility(View.VISIBLE); // Show if path exists
            // Use a tag to prevent setting image on recycled views
            imageView.setTag(imagePathOrUriString);

            imageLoadExecutor.execute(() -> {
                Bitmap bitmap = null;
                Context context = imageView.getContext(); // Get context safely
                if (context == null) return; // Cannot proceed without context

                try {
                    // Use smaller target size for list items
                    Uri imageUri = Uri.parse(imagePathOrUriString);
                    bitmap = decodeSampledBitmapFromUri(context, imageUri, 150, 150);
                    Log.d(TAG, "Loaded bitmap from URI for list item: " + imagePathOrUriString);
                } catch (Exception eUri) {
                    Log.w(TAG, "Failed URI load for list item '"+imagePathOrUriString+"', trying path. Error: " + eUri.getMessage());
                    try {
                        File imgFile = new File(imagePathOrUriString);
                        if (imgFile.exists()) {
                            bitmap = decodeSampledBitmapFromFile(imagePathOrUriString, 150, 150);
                            Log.d(TAG, "Loaded bitmap from file path for list item: " + imagePathOrUriString);
                        } else {
                            Log.w(TAG, "File path does not exist for list item: " + imagePathOrUriString);
                        }
                    } catch (Exception eFile) {
                        Log.e(TAG, "Failed loading image for list item '" + imagePathOrUriString + "' from both URI/Path.", eFile);
                    }
                }


                final Bitmap finalBitmap = bitmap;
                mainHandler.post(() -> {
                    // Check if the ImageView tag still matches the path (view wasn't recycled for another item)
                    if (imageView.getTag() != null && imageView.getTag().equals(imagePathOrUriString)) {
                        if (finalBitmap != null) {
                            imageView.setImageBitmap(finalBitmap);
                        } else {
                            // Set error placeholder if loading failed but path existed
                            imageView.setImageResource(R.drawable.ic_broken_image_24);
                            Log.w(TAG, "Setting broken image placeholder for: " + imagePathOrUriString);
                        }
                    } else {
                        Log.d(TAG, "ImageView recycled before image load finished for: " + imagePathOrUriString);
                    }
                });
            });
        }
        // No else needed, view is already hidden and tag cleared if path is null/empty
    }

    // --- Bitmap Scaling Helpers ---
    private Bitmap decodeSampledBitmapFromUri(Context context, Uri uri, int reqWidth, int reqHeight) throws FileNotFoundException {
        InputStream inputStream = null;
        InputStream boundsInputStream = null;
        try {
            boundsInputStream = context.getContentResolver().openInputStream(uri);
            if (boundsInputStream == null) throw new FileNotFoundException("Cannot open input stream for URI: " + uri);

            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(boundsInputStream, null, options);

            options.inSampleSize = AddInfoActivity.calculateInSampleSize(options, reqWidth, reqHeight); // Use static helper

            options.inJustDecodeBounds = false;
            inputStream = context.getContentResolver().openInputStream(uri); // Reopen stream
            if (inputStream == null) throw new FileNotFoundException("Cannot re-open input stream for URI: " + uri);
            return BitmapFactory.decodeStream(inputStream, null, options);
        } finally {
            try { if (boundsInputStream != null) boundsInputStream.close(); } catch (IOException ignored) {}
            try { if (inputStream != null) inputStream.close(); } catch (IOException ignored) {}
        }
    }

    private Bitmap decodeSampledBitmapFromFile(String filePath, int reqWidth, int reqHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);
        options.inSampleSize = AddInfoActivity.calculateInSampleSize(options, reqWidth, reqHeight); // Use static helper
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(filePath, options);
    }
    // ---------------------------------------------------------------------------------------

    // Method to properly shutdown executor when adapter is no longer needed
    public void shutdownExecutor() {
        if (imageLoadExecutor != null && !imageLoadExecutor.isShutdown()) {
            imageLoadExecutor.shutdown();
            Log.d(TAG, "Image loading executor shut down.");
        }
    }
}