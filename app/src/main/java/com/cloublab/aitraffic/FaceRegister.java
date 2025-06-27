package com.cloublab.aitraffic;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.cloublab.aitraffic.helper.FaceRecognitionHelper;
import com.cloublab.aitraffic.helper.JsonDatabase;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class FaceRegister extends AppCompatActivity {
        private static final int CAMERA_REQUEST = 100;
        private ImageView imageView;
        private Bitmap faceBitmap;
        private File imageFile;

        private FaceRecognitionHelper faceHelper;
        private JsonDatabase jsonDatabase;

        private ActivityResultLauncher<Intent> cameraLauncher;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_face_register);
            imageView = findViewById(R.id.imageView);
            Button btnCapture = findViewById(R.id.btnCapture);
            Button btnSave = findViewById(R.id.btnSave);

            faceHelper = new FaceRecognitionHelper(this);
            jsonDatabase = new JsonDatabase(this);

            cameraLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            faceBitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
                            faceBitmap = correctImageRotation(imageFile, faceBitmap);
                            imageView.setImageBitmap(faceBitmap);
                            Log.d("FACE_RECOGNITION", "Image size: " + faceBitmap.getWidth() + "x" + faceBitmap.getHeight());

                        }
                    });


            btnCapture.setOnClickListener(v -> {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, CAMERA_REQUEST);
                } else {
                    imageFile = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "face.jpg");
                    Uri imageUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", imageFile);

                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT,imageUri);
                    cameraLauncher.launch(intent);
                }
            });

            btnSave.setOnClickListener(v -> {
                if (faceBitmap != null) {
                    float[] embedding = faceHelper.getFaceEmbedding(faceBitmap);
                    jsonDatabase.saveEmbedding("User1", embedding);
                    Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show();
                    //Log.d("FACE_REGISTER", Arrays.toString(embedding));
                }
            });
        }

    private Bitmap correctImageRotation(File imageFile, Bitmap bitmap) {
        try {
            ExifInterface exif = new ExifInterface(imageFile.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = new Matrix();

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: matrix.postRotate(90); break;
                case ExifInterface.ORIENTATION_ROTATE_180: matrix.postRotate(180); break;
                case ExifInterface.ORIENTATION_ROTATE_270: matrix.postRotate(270); break;
            }

            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (IOException e) {
            e.printStackTrace();
            return bitmap;
        }
    }
}