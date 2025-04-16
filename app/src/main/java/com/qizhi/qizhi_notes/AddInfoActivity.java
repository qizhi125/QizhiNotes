package com.qizhi.qizhi_notes;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull; // Added for permission result
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat; // Added for permissions
import androidx.core.content.ContextCompat; // Added for permissions
import androidx.core.content.FileProvider; // Keep FileProvider

import android.Manifest; // Added for permissions
import android.app.Activity; // Needed for RESULT_OK
import android.content.Intent;
import android.content.pm.PackageManager; // Added for permissions
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build; // Check build version for permissions
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log; // Added for logging
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.qizhi.qizhi_notes.bean.MemoBean;
import com.qizhi.qizhi_notes.db.MyDbHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddInfoActivity extends AppCompatActivity {

    private static final String TAG = "AddInfoActivity"; // Added for logging

    public static final String EXTRA_MEMO_ID = "memo_id";
    public static final String EXTRA_MEMO_TITLE = "memo_title";
    public static final String EXTRA_MEMO_CONTENT = "memo_content";
    public static final String EXTRA_MEMO_IMG_PATH = "memo_img_path";

    private static final int PERMISSION_REQUEST_CODE_CAMERA = 101;
    private static final int PERMISSION_REQUEST_CODE_STORAGE_READ = 102;
    // WRITE_EXTERNAL_STORAGE is often not needed on API 29+ if using MediaStore or app-specific directory
    // Let's request READ only for gallery for now. Camera needs write for temp file.
    private static final int PERMISSION_REQUEST_CODE_STORAGE_WRITE = 103; // Needed for Camera temp file pre-API 29


    private EditText etTitle;
    private EditText etContent;
    private ImageView ivImage;
    private Button btnSave;
    private Button btnCamera;
    private Button btnGallery;

    private MyDbHelper dbHelper;
    private String currentPhotoPath; // To store path of photo taken by camera
    private Uri currentPhotoUri; // URI for the photo taken by camera
    private String selectedImagePath; // To store path/URI string from camera or gallery

    private int currentMemoId = -1; // -1 indicates a new memo

    // --- Added for Background Tasks ---
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // ---------------------------------

    // --- New Activity Result Launchers ---
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> galleryLauncher;
    // -----------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Theme handles background now
        setContentView(R.layout.activity_add_info);

        dbHelper = new MyDbHelper(this);

        etTitle = findViewById(R.id.etTitle);
        etContent = findViewById(R.id.etContent);
        ivImage = findViewById(R.id.imageView);
        btnSave = findViewById(R.id.btnSave);
        btnCamera = findViewById(R.id.btnCamera);
        btnGallery = findViewById(R.id.btnGallery);

        // Initialize Activity Result Launchers
        setupResultLaunchers();

        // Check if editing existing memo
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_MEMO_ID)) {
            currentMemoId = intent.getIntExtra(EXTRA_MEMO_ID, -1);
            if (currentMemoId != -1) { // Check if ID is valid
                etTitle.setText(intent.getStringExtra(EXTRA_MEMO_TITLE));
                etContent.setText(intent.getStringExtra(EXTRA_MEMO_CONTENT));
                selectedImagePath = intent.getStringExtra(EXTRA_MEMO_IMG_PATH); // Load existing path
                loadImageIntoView(selectedImagePath); // Load existing image if path exists
                Log.d(TAG, "Editing existing memo. ID: " + currentMemoId + ", ImgPath: " + selectedImagePath);
                setTitle(R.string.title_activity_add_info); // Set title to "Edit" maybe? Or keep generic.
            } else {
                Log.w(TAG, "Received edit intent but memo ID was invalid (-1). Treating as new memo.");
                selectedImagePath = null;
                setTitle(R.string.title_activity_add_info); // Set title to "Add"
            }
        } else {
            Log.d(TAG, "Creating new memo.");
            selectedImagePath = null; // Ensure null for new memo
            setTitle(R.string.title_activity_add_info); // Set title to "Add"
        }


        btnSave.setOnClickListener(v -> saveMemo());

        btnCamera.setOnClickListener(v -> {
            Log.d(TAG, "Camera button clicked.");
            checkCameraPermissionsAndLaunch();
        });

        btnGallery.setOnClickListener(v -> {
            Log.d(TAG, "Gallery button clicked.");
            checkStoragePermissionAndLaunchGallery();
        });
    }

    private void setupResultLaunchers() {
        // Launcher for Camera result
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // Image captured and saved to currentPhotoUri specified in the Intent
                        Log.d(TAG, "Camera result OK. Photo URI: " + currentPhotoUri);
                        // No need for result.getData() as we specified EXTRA_OUTPUT
                        if (currentPhotoUri != null) {
                            selectedImagePath = currentPhotoUri.toString(); // Store URI as string
                            loadImageIntoView(selectedImagePath);
                        } else if (currentPhotoPath != null) {
                            // Fallback for older methods or if URI is somehow null but path exists
                            selectedImagePath = currentPhotoPath;
                            loadImageIntoView(selectedImagePath);
                        } else {
                            Log.e(TAG, "Camera returned OK but photo URI/Path is null!");
                            Toast.makeText(this, R.string.error_loading_image, Toast.LENGTH_SHORT).show();
                        }

                    } else {
                        Log.w(TAG, "Camera result cancelled or failed. ResultCode: " + result.getResultCode());
                        Toast.makeText(this, R.string.add_toast_operation_cancelled, Toast.LENGTH_SHORT).show();
                        // Optionally delete the temp file if capture was cancelled
                        if (currentPhotoPath != null) {
                            File photoFile = new File(currentPhotoPath);
                            if (photoFile.exists()) {
                                if (photoFile.delete()) {
                                    Log.d(TAG,"Temp camera file deleted: " + currentPhotoPath);
                                } else {
                                    Log.w(TAG,"Failed to delete temp camera file: " + currentPhotoPath);
                                }
                            }
                            // Reset paths/uris if cancelled
                            currentPhotoPath = null;
                            currentPhotoUri = null;
                        }
                    }
                });

        // Launcher for Gallery result
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        Log.d(TAG, "Gallery result OK. Image URI: " + imageUri);
                        if (imageUri != null) {
                            // IMPORTANT: Persist permission for content URIs if needed across restarts
                            try {
                                // Try to get persistent permission (optional but recommended for gallery URIs)
                                final int takeFlags = result.getData().getFlags()
                                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                getContentResolver().takePersistableUriPermission(imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                Log.d(TAG,"Persisted permissions for URI: "+imageUri);
                            } catch (SecurityException e){
                                Log.w(TAG, "Failed to get persistent permission for URI: " + imageUri, e);
                            }
                            selectedImagePath = imageUri.toString(); // Store URI as string
                            loadImageIntoView(selectedImagePath);
                        } else {
                            Log.e(TAG, "Gallery returned OK but data or URI is null!");
                            Toast.makeText(this, R.string.error_loading_image, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.w(TAG, "Gallery result cancelled or failed. ResultCode: " + result.getResultCode());
                        Toast.makeText(this, R.string.add_toast_operation_cancelled, Toast.LENGTH_SHORT).show();
                    }
                });
    }


    private void saveMemo() {
        String title = etTitle.getText().toString().trim();
        String content = etContent.getText().toString().trim();

        // Allow saving if only title or content or image exists
        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(content) && selectedImagePath == null) {
            Toast.makeText(this, R.string.error_empty_note, Toast.LENGTH_SHORT).show();
            return;
        }
        // If title is empty but content or image exists, provide a default title
        if (TextUtils.isEmpty(title)) {
            // Use part of content or a default title if title is empty but other fields are not
            if(!TextUtils.isEmpty(content)){
                title = content.substring(0, Math.min(content.length(), 20)) + "..."; // Example default
            } else {
                title = getString(R.string.default_note_title); // Or a resource string like "Note"
            }
        }

        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        MemoBean memo = new MemoBean();
        memo.setTitle(title);
        memo.setContent(content);
        memo.setImgPath(selectedImagePath); // Save the path/URI string


        // --- Run DB operation in background ---
        executorService.execute(() -> {
            long resultId = -1;
            int rowsAffected = -1;
            boolean success = false;

            try {
                if (currentMemoId == -1) {
                    // Insert new memo
                    memo.setCreateTime(currentTime); // Set create time only for new notes
                    resultId = dbHelper.insertMemo(memo);
                    success = (resultId != -1);
                    Log.d(TAG, "Attempting to insert new memo...");
                } else {
                    // Update existing memo
                    memo.setId(currentMemoId);
                    // Retrieve original create time if needed, or just don't update it
                    // String originalCreateTime = dbHelper.getMemoCreateTime(currentMemoId); // Requires new DB method
                    // memo.setCreateTime(originalCreateTime); // Keep original time
                    rowsAffected = dbHelper.updateMemo(memo);
                    success = (rowsAffected > 0);
                    Log.d(TAG, "Attempting to update memo with ID: " + currentMemoId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during database operation", e);
                success = false;
                // Post specific error?
                mainHandler.post(() -> Toast.makeText(AddInfoActivity.this, R.string.add_toast_save_error, Toast.LENGTH_SHORT).show());
            }

            final boolean finalSuccess = success;
            mainHandler.post(() -> {
                // Update UI on main thread
                if (finalSuccess) {
                    Toast.makeText(AddInfoActivity.this, R.string.memo_saved_success, Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Memo save operation successful.");
                    setResult(RESULT_OK); // Set result to notify MainActivity to refresh
                    finish(); // Close activity after saving
                } else {
                    // Avoid showing duplicate error if DB exception already showed one
                    if (! (Thread.currentThread().isInterrupted())) { // Crude check if error was already posted
                        Toast.makeText(AddInfoActivity.this, R.string.memo_saved_fail, Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Memo save operation failed.");
                    }
                }
            });
        });
        // -------------------------------------
    }

    // --- Permission Handling ---

    private void checkCameraPermissionsAndLaunch() {
        String[] permissions;
        // Write permission is needed for camera output file before API 29
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        } else {
            permissions = new String[]{Manifest.permission.CAMERA};
        }

        boolean cameraPermissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        // Only check write permission if needed (< API 29)
        boolean storageWritePermissionGranted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;


        if (cameraPermissionGranted && storageWritePermissionGranted) {
            Log.d(TAG, "Camera & required Storage permissions already granted.");
            dispatchTakePictureIntent();
        } else {
            Log.d(TAG, "Requesting Camera/Storage permissions...");
            // Build a list of permissions to request
            java.util.List<String> permissionsToRequest = new java.util.ArrayList<>();
            if (!cameraPermissionGranted) {
                permissionsToRequest.add(Manifest.permission.CAMERA);
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !storageWritePermissionGranted) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }

            if (!permissionsToRequest.isEmpty()) {
                ActivityCompat.requestPermissions(this,
                        permissionsToRequest.toArray(new String[0]),
                        PERMISSION_REQUEST_CODE_CAMERA); // Use single code for camera related group
            }
        }
    }

    private void checkStoragePermissionAndLaunchGallery() {
        // Starting from Android Tiramisu (API 33), need specific permissions for images/videos
        String permissionNeeded;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionNeeded = Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            permissionNeeded = Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        if (ContextCompat.checkSelfPermission(this, permissionNeeded) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Storage read permission ("+permissionNeeded+") already granted.");
            dispatchPickFromGalleryIntent();
        } else {
            Log.d(TAG, "Requesting storage read permission: "+ permissionNeeded);
            ActivityCompat.requestPermissions(this, new String[]{permissionNeeded}, PERMISSION_REQUEST_CODE_STORAGE_READ);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean allGranted = true;
        if (grantResults.length > 0) {
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
        } else {
            // Consider request cancelled if grantResults is empty
            allGranted = false;
            Log.w(TAG, "Permission request cancelled or interrupted for code: " + requestCode);
        }


        switch (requestCode) {
            case PERMISSION_REQUEST_CODE_CAMERA: // Handles Camera + optional Write Storage group
                if (allGranted) {
                    Log.d(TAG, "Camera & required Storage permissions granted via request.");
                    dispatchTakePictureIntent();
                } else {
                    Log.w(TAG, "Camera or required Storage permission denied.");
                    Toast.makeText(this, R.string.permission_denied_camera_storage, Toast.LENGTH_SHORT).show();
                }
                break;
            case PERMISSION_REQUEST_CODE_STORAGE_READ:
                if (allGranted) {
                    Log.d(TAG, "Storage read permission granted via request.");
                    dispatchPickFromGalleryIntent();
                } else {
                    Log.w(TAG, "Storage read permission denied.");
                    Toast.makeText(this, R.string.permission_denied_storage_read, Toast.LENGTH_SHORT).show();
                }
                break;
            // Handle other permission request codes if needed
        }
    }

    // --- Intent Dispatching ---

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                Log.e(TAG, "Error creating image file", ex);
                Toast.makeText(this, R.string.error_creating_file, Toast.LENGTH_SHORT).show();
                return; // Abort if file creation fails
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                // Get the URI for the file using FileProvider
                try {
                    // Use getApplicationContext().getPackageName() for safety across contexts
                    String authority = getApplicationContext().getPackageName() + ".provider";
                    currentPhotoUri = FileProvider.getUriForFile(this, authority, photoFile);
                    Log.d(TAG, "Photo file created: " + currentPhotoPath + ", Authority: " + authority + ", URI: " + currentPhotoUri);

                    // Add the URI to the intent
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);

                    // Grant temporary permissions to the camera app
                    // Required for ACTION_IMAGE_CAPTURE with EXTRA_OUTPUT
                    if (currentPhotoUri != null) { // Add this check
                        takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    } else {
                        Log.e(TAG, "currentPhotoUri is null, cannot add permission flags.");
                        Toast.makeText(this, R.string.error_camera_intent, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Launch the camera intent
                    cameraLauncher.launch(takePictureIntent);

                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "FileProvider IllegalArgumentException. Check authority (" + getApplicationContext().getPackageName() + ".provider) and file path ("+photoFile.getAbsolutePath()+"). Ensure provider_paths.xml is correct.", e);
                    Toast.makeText(this, R.string.error_file_provider, Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Log.e(TAG, "Error setting up camera intent", e);
                    Toast.makeText(this, R.string.error_camera_intent, Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Log.e(TAG, "No camera app found to handle the intent.");
            Toast.makeText(this, R.string.add_toast_no_camera_app, Toast.LENGTH_SHORT).show();
        }
    }


    private void dispatchPickFromGalleryIntent() {
        Intent pickPhotoIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT); // Use OPEN_DOCUMENT for persistent access
        pickPhotoIntent.addCategory(Intent.CATEGORY_OPENABLE);
        pickPhotoIntent.setType("image/*"); // Select only images
        pickPhotoIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION); // Request persistable read permission

        // Check if there's an activity to handle the intent
        if (pickPhotoIntent.resolveActivity(getPackageManager()) != null) {
            galleryLauncher.launch(pickPhotoIntent);
        } else {
            Log.e(TAG, "No gallery app found to handle the intent.");
            Toast.makeText(this, R.string.error_no_gallery_app, Toast.LENGTH_SHORT).show();
        }
    }

    // --- Image Handling ---

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        // Use app-specific external files directory (requires no special permission after API 18)
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (storageDir == null) {
            Log.e(TAG, "External files directory is null. Cannot create image file.");
            throw new IOException("External files directory not available.");
        }
        if (!storageDir.exists()) {
            if (!storageDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory: " + storageDir.getAbsolutePath());
                String err = String.format(Locale.ROOT, getString(R.string.add_toast_dir_creation_error), storageDir.getAbsolutePath());
                Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                throw new IOException("Failed to create storage directory for pictures.");
            }
        }

        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents or if URI fails later
        currentPhotoPath = image.getAbsolutePath();
        Log.d(TAG, "Created image file: " + currentPhotoPath);
        return image;
    }


    private void loadImageIntoView(String imagePathOrUriString) {
        if (imagePathOrUriString == null || imagePathOrUriString.isEmpty()) {
            ivImage.setImageResource(R.drawable.ic_image_24); // Set placeholder if no image
            Log.d(TAG, "No image path/URI provided, setting placeholder.");
            return;
        }
        Log.d(TAG, "Attempting to load image: " + imagePathOrUriString);

        // --- Load image in background with scaling ---
        executorService.execute(() -> {
            Bitmap bitmap = null;
            try {
                Uri imageUri = Uri.parse(imagePathOrUriString); // Try parsing as URI first
                bitmap = decodeSampledBitmapFromUri(imageUri, 400, 400); // Decode with target size
                Log.d(TAG, "Successfully decoded bitmap from URI.");
            } catch (Exception eUri) {
                Log.w(TAG, "Failed to load as URI, trying as file path. Error: " + eUri.getMessage());
                // If URI parsing/loading failed, try treating it as a direct file path (less common now)
                try {
                    File imgFile = new File(imagePathOrUriString);
                    if (imgFile.exists()) {
                        bitmap = decodeSampledBitmapFromFile(imagePathOrUriString, 400, 400);
                        Log.d(TAG, "Successfully decoded bitmap from file path.");
                    } else {
                        Log.e(TAG, "Image file does not exist at path: " + imagePathOrUriString);
                    }
                } catch (Exception eFile) {
                    Log.e(TAG, "Failed to load image from both URI and File path.", eFile);
                }
            }

            final Bitmap finalBitmap = bitmap;
            mainHandler.post(() -> {
                if (finalBitmap != null) {
                    ivImage.setImageBitmap(finalBitmap);
                } else {
                    ivImage.setImageResource(R.drawable.ic_broken_image_24); // Set error placeholder
                    Toast.makeText(AddInfoActivity.this, R.string.error_loading_image, Toast.LENGTH_SHORT).show();
                }
            });
        });
        // -------------------------------------------
    }

    // --- Bitmap Scaling Helpers ---
    private Bitmap decodeSampledBitmapFromUri(Uri uri, int reqWidth, int reqHeight) throws FileNotFoundException {
        InputStream inputStream = null;
        InputStream boundsInputStream = null;
        try {
            boundsInputStream = getContentResolver().openInputStream(uri);
            if (boundsInputStream == null) throw new FileNotFoundException("Cannot open input stream for URI: " + uri);

            // First decode with inJustDecodeBounds=true to check dimensions
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(boundsInputStream, null, options);

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false;
            inputStream = getContentResolver().openInputStream(uri); // Reopen stream
            if (inputStream == null) throw new FileNotFoundException("Cannot re-open input stream for URI: " + uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);

            Log.d(TAG, "Decoded bitmap from URI. Original: " + options.outWidth + "x" + options.outHeight +
                    ", SampleSize: " + options.inSampleSize + ", Final: " +
                    (bitmap != null ? bitmap.getWidth() + "x" + bitmap.getHeight() : "null"));

            return bitmap;
        } finally {
            // Ensure streams are closed
            if (boundsInputStream != null) {
                try { boundsInputStream.close(); } catch (IOException ignored) {}
            }
            if (inputStream != null) {
                try { inputStream.close(); } catch (IOException ignored) {}
            }
        }

    }

    private Bitmap decodeSampledBitmapFromFile(String filePath, int reqWidth, int reqHeight) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(filePath, options);

        Log.d(TAG, "Decoded bitmap from file. Original: " + options.outWidth + "x" + options.outHeight +
                ", SampleSize: " + options.inSampleSize + ", Final: " +
                (bitmap != null ? bitmap.getWidth() + "x" + bitmap.getHeight() : "null"));

        return bitmap;
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (reqWidth <= 0 || reqHeight <= 0) { // Avoid division by zero if req sizes are invalid
            return inSampleSize;
        }


        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
    // -------------------------


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Shutdown executor
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        // Close DB helper
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}