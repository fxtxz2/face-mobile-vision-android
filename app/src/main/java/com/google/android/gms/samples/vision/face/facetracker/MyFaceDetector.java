package com.google.android.gms.samples.vision.face.facetracker;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Environment;
import android.util.SparseArray;

import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 包装FaceDetecor
 * Created by zhangyalin on 2017/4/21.
 */

public class MyFaceDetector extends Detector<Face> {
    private Detector<Face> mDelegate;

    MyFaceDetector(Detector<Face> delegate) {
        mDelegate = delegate;
    }

    public SparseArray<Face> detect(Frame frame) {
        // *** add your custom frame processing code here
        SparseArray<Face> detectedFaces = mDelegate.detect(frame);
        for(int i=0;i<detectedFaces.size();i++) {          //can't use for-each loops for SparseArrays
            Face face = detectedFaces.valueAt(i);

            Frame.Metadata metadata = frame.getMetadata();
            ByteBuffer byteBuffer = frame.getGrayscaleImageData();
            int width = metadata.getWidth();
            int height = metadata.getHeight();
            int rotation = metadata.getRotation();
            byte[] bytes = byteBuffer.array();

            YuvImage yuvImage = new YuvImage(bytes, ImageFormat.NV21, width,height, null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, baos); // Where 100 is the quality of the generated jpeg
            byte[] jpegArray =  baos.toByteArray();
            Bitmap bitmap = BitmapFactory.decodeByteArray(jpegArray, 0, jpegArray.length);

            Matrix matrix = new Matrix();
            switch (rotation) {
                case Frame.ROTATION_0:
                    matrix.postRotate(0);
                    break;
                case Frame.ROTATION_90:
                    matrix.postRotate(90);
                    break;
                case Frame.ROTATION_180:
                    matrix.postRotate(90);
                    break;
                case Frame.ROTATION_270:
                    matrix.postRotate(270);
                    break;
            }

            Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

            if (resizedBitmap != null){
                Bitmap faceBitmap = Bitmap.createBitmap(resizedBitmap, (int) face.getPosition().x, (int) face.getPosition().y, (int) face.getWidth(), (int) face.getHeight());
                FileOutputStream out = null;
                try {
                    //you can create a new file name "test.jpg" in sdcard folder.
                    File file = new File(Environment.getExternalStoragePublicDirectory("face") + File.separator + "myface"+ System.currentTimeMillis() + face.getId() +".png");
                    File parenFile = file.getParentFile();
                    if (!parenFile.exists()){
                        parenFile.mkdirs();
                    }
                    out = new FileOutputStream(file);
                    faceBitmap.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
                    // PNG is a lossless format, the compression factor (100) is ignored
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (out != null) {
                            out.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return detectedFaces;
    }

    public boolean isOperational() {
        return mDelegate.isOperational();
    }

    public boolean setFocus(int id) {
        return mDelegate.setFocus(id);
    }
}
