package com.cloublab.aitraffic.helper;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
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
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.Arrays;
import java.util.function.Consumer;

public class Camera2Helper {
    private final Context context;
    private final SurfaceHolder surfaceHolder;
    private final Size previewSize;
    private final Consumer<Image> imageListener;

    private CameraDevice cameraDevice;
    private CameraCaptureSession session;
    private ImageReader imageReader;
    private Handler backgroundHandler;

    public Camera2Helper(Context context, SurfaceHolder surfaceHolder, Size previewSize, Consumer<Image> imageListener) {
        this.context = context;
        this.surfaceHolder = surfaceHolder;
        this.previewSize = previewSize;
        this.imageListener = imageListener;
    }

    public void start() {
        HandlerThread thread = new HandlerThread("CameraBackground");
        thread.start();
        backgroundHandler = new Handler(thread.getLooper());

        imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);
        imageReader.setOnImageAvailableListener(reader -> {
            Image image = reader.acquireLatestImage();
            if (image != null) imageListener.accept(image);
        }, backgroundHandler);

        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[1];
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    try {
                        Surface surface = surfaceHolder.getSurface();
                        CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        builder.addTarget(surface);
                        builder.addTarget(imageReader.getSurface());
                        camera.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()), new CameraCaptureSession.StateCallback() {

                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession session) {
                                Camera2Helper.this.session = session;
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
