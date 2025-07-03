package com.cloublab.aitraffic.helper;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.Arrays;
import java.util.function.Consumer;

public class Camera2TextureHelper {
    private final Context context;
    private final TextureView textureView;
    private final Size previewSize;
    private final Consumer<Image> imageListener;
    private CameraDevice cameraDevice;
    private CameraCaptureSession session;
    private ImageReader imageReader;
    private Handler backgroundHandler;

    public Camera2TextureHelper(Context context, TextureView textureView, Size previewSize, Consumer<Image> imageListener) {
        this.context = context;
        this.textureView = textureView;
        this.previewSize = previewSize;
        this.imageListener = imageListener;
    }

    public void start(boolean isFrontCamera) {
        HandlerThread thread = new HandlerThread("CameraBackground");
        thread.start();
        backgroundHandler = new Handler(thread.getLooper());

        imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);
        imageReader.setOnImageAvailableListener(reader -> {
            Image image = reader.acquireLatestImage();
            if (image != null) imageListener.accept(image);
        }, backgroundHandler);

        if(textureView.isAvailable()){
            openCamera(isFrontCamera);
        }else {
            textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                    openCamera(isFrontCamera);
                }

                @Override
                public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

                }

                @Override
                public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

                }
            });
        }
    }

    private void openCamera(boolean isFrontCamera){
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[isFrontCamera ? 1: 0];
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    try {
                        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
                        if(surfaceTexture != null) {
                            surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
                            Surface surface = new Surface(surfaceTexture);
                            CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            builder.addTarget(surface);
                            builder.addTarget(imageReader.getSurface());
                            camera.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()), new CameraCaptureSession.StateCallback() {

                                @Override
                                public void onConfigured(@NonNull CameraCaptureSession session) {
                                    Camera2TextureHelper.this.session = session;
                                    try {
                                        session.setRepeatingRequest(builder.build(), null, backgroundHandler);
                                    } catch (CameraAccessException e) {
                                        e.printStackTrace();
                                    }
                                }

                                @Override
                                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                                }
                            }, backgroundHandler);
                        }
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close();
                }
            }, backgroundHandler);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void stop(){
        if(session != null) session.close();
        if(cameraDevice != null) cameraDevice.close();
        if(imageReader != null) imageReader.close();
    }

}
