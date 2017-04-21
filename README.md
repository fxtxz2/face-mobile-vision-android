# face-mobile-vision-android
# 概念
面部识别：从图片或者帧里面判断出是不是人脸（Face）。
面部比对：两个人脸（Face）数据（不是图片或帧）是不是同一个人。

官方教程对面部识别，面部跟踪，特征，表情作出了说明。https://developers.google.com/vision/face-detection-concepts

下载官方的例子：https://github.com/googlesamples/android-vision.git
这里我主要关注：https://github.com/googlesamples/android-vision/tree/master/visionSamples/FaceTracker这个人脸跟踪项目。

# 目的
通过修改FaceTracker项目，达到拍摄照片的过程，把人脸截图保存到sd卡里面。

# 关键问题
## 手机需要提前安装google play服务
可以看我之前的文章：[您的设备不支持Google Play服务，因此无法运行XXX](http://www.jianshu.com/p/51071320daa0)

## 获得识别出的面部帧（整张图片，不是面部）
包装FaceDetector：
```java
class MyFaceDetector extends Detector<Face> {
  private Detector<Face> mDelegate;

  MyFaceDetector(Detector<Face> delegate) {
    mDelegate = delegate;
  }

  public SparseArray<Face> detect(Frame frame) {
    // *** add your custom frame processing code here
    return mDelegate.detect(frame);
  }

  public boolean isOperational() {
    return mDelegate.isOperational();
  }

  public boolean setFocus(int id) {
    return mDelegate.setFocus(id);
  }
}
```
使用包装好的MyFaceDetector：
```Java
MyFaceDetector myFaceDetector = new MyFaceDetector(detector);
myFaceDetector.setProcessor(
                new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory())
                        .build());
```
使用MultiDetector来添加多个识别器：
```Java
 MultiDetector multiDetector = new MultiDetector.Builder()
                .add(myFaceDetector)
                .build();
```
添加到CameraSource对象里面：
```Java
DisplayMetrics metrics = new DisplayMetrics();
getWindowManager().getDefaultDisplay().getMetrics(metrics);
Log.e(TAG, "widthPixels: " + metrics.widthPixels + " -- metrics: " + metrics.heightPixels);

//        mCameraSource = new CameraSource.Builder(context, detector)
mCameraSource = new CameraSource.Builder(context, multiDetector)
                .setRequestedPreviewSize(metrics.widthPixels, metrics.heightPixels)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedFps(60.0f)
                .setAutoFocusEnabled(true)
                .build();
```
这样就可以在MyFaceDetector的detect(Frame frame) 里面获得识别到的面部帧了。

## 处理识别到的人脸帧
最开始以为通过frame.getBitmap()就可以轻松获得到面部帧，然而没有这里获得是null。首先通过FaceDetector的代理识别出人脸帧：
```Java
SparseArray<Face> detectedFaces = mDelegate.detect(frame);
        for(int i=0;i<detectedFaces.size();i++) {          //can't use for-each loops for SparseArrays
            Face face = detectedFaces.valueAt(i);
}
```
然后通过frame.getGrayscaleImageData()方法获得帧数据，通过YuvImage获得jpg图片流，获得人脸帧的bitmap,注意*这里需要对bitmap进行一下图片的旋转处理*：
```Java
Frame.Metadata metadata = frame.getMetadata();
ByteBuffer byteBuffer = frame.getGrayscaleImageData();
int width = metadata.getWidth();
int height = metadata.getHeight();
int rotation = metadata.getRotation();
byte[] bytes = byteBuffer.array();

YuvImage yuvImage = new YuvImage(bytes, ImageFormat.NV21, width,height, null);
ByteArrayOutputStream baos = new ByteArrayOutputStream();
yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, baos); // Where 100 is the quality of the generated jpeg
byte[] jpegArray = baos.toByteArray();
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
```
## 裁剪人脸图片并保存到sd卡
```Java
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
```
## 完整的MyFaceDetector类
```Java
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
            byte[] jpegArray =  .toByteArray();
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

```

### 我的修改的项目在这里：
https://github.com/fxtxz2/face-mobile-vision-android.git

>参考：
[Mobile Vision API - concatenate new detector object to continue frame processing](http://stackoverflow.com/questions/32299947/mobile-vision-api-concatenate-new-detector-object-to-continue-frame-processing)  
[Get The Face bitmap from face detector](https://github.com/googlesamples/android-vision/issues/167)  
[Save bitmap to location](http://stackoverflow.com/questions/649154/save-bitmap-to-location)  
[Android Saving created bitmap to directory on sd card](http://stackoverflow.com/questions/4263375/android-saving-created-bitmap-to-directory-on-sd-card)  
[How to create Bitmap from grayscaled byte buffer image?](http://www.developersite.org/103-127259-android)
