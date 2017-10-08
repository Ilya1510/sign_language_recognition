package com.future_prospects.mike.signlanguagerecognition.activities;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.future_prospects.mike.signlanguagerecognition.R;
import com.future_prospects.mike.signlanguagerecognition.model.ProcessImage;
import com.future_prospects.mike.signlanguagerecognition.presentors.ImagePresentor;
import com.future_prospects.mike.signlanguagerecognition.server.ImageSenderAsyncTask;
import com.future_prospects.mike.signlanguagerecognition.utils.PickBestImage;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CameraActivity extends Activity implements SurfaceHolder.Callback, View.OnClickListener,
        Camera.PictureCallback, Camera.PreviewCallback, Camera.AutoFocusCallback, ImagePresentor
{
    private Camera camera;
    private SurfaceHolder surfaceHolder;
    private SurfaceView preview;
    private Button shotBtn;
    private boolean photoAvailable = true;
    private long curtime = -1;
    private List<byte[]> photos;

    TextView messageFromServer;

    private StringBuilder message;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        // если хотим, чтобы приложение постоянно имело портретную ориентацию
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // если хотим, чтобы приложение было полноэкранным
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // и без заголовка
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_camera);
        messageFromServer = (TextView)findViewById(R.id.messageTV);

        // наше SurfaceView имеет имя SurfaceView01
        preview = (SurfaceView) findViewById(R.id.SurfaceView01);

        surfaceHolder = preview.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        // кнопка имеет имя Button01
        shotBtn = (Button) findViewById(R.id.Button01);
        shotBtn.setText("Shot");
        shotBtn.setOnClickListener(this);
        photos = new ArrayList<>();

        message = new StringBuilder();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        camera = Camera.open();
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        if (camera != null)
        {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
    {
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder)
    {
        try
        {
            camera.setPreviewDisplay(holder);
            camera.setPreviewCallback(this);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        Camera.Size previewSize = camera.getParameters().getPreviewSize();
        float aspect = (float) previewSize.width / previewSize.height;

        int previewSurfaceWidth = preview.getWidth();
        int previewSurfaceHeight = preview.getHeight();

        ViewGroup.LayoutParams lp = preview.getLayoutParams();

        // здесь корректируем размер отображаемого preview, чтобы не было искажений

        if (this.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE)
        {
            // портретный вид
            camera.setDisplayOrientation(90);
            lp.height = previewSurfaceHeight;
            lp.width = (int) (previewSurfaceHeight / aspect);
            ;
        }
        else
        {
            // ландшафтный
            camera.setDisplayOrientation(0);
            lp.width = previewSurfaceWidth;
            lp.height = (int) (previewSurfaceWidth / aspect);
        }

        preview.setLayoutParams(lp);
        camera.startPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder)
    {
    }

    @Override
    public void onClick(View v)
    {
        if (v == shotBtn)
        {
            // либо делаем снимок непосредственно здесь
            // 	либо включаем обработчик автофокуса

            //camera.takePicture(null, null, null, this);
            camera.autoFocus(this);
        }
    }

    @Override
    public void onPictureTaken(byte[] paramArrayOfByte, Camera paramCamera)
    {
        try {
            String filename = String.format(getExternalCacheDir().getAbsolutePath() + "/SLR/%d.jpg", System.currentTimeMillis());
            File saveDir = new File(filename);
            if (!saveDir.exists()) {
                saveDir.mkdirs();
            }

            FileOutputStream os = new FileOutputStream(filename);
            os.write(paramArrayOfByte);
            os.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        // после того, как снимок сделан, показ превью отключается. необходимо включить его
        paramCamera.startPreview();
    }

    @Override
    public void onAutoFocus(boolean paramBoolean, Camera paramCamera)
    {
        if (paramBoolean)
        {
            // если удалось сфокусироваться, делаем снимок
            paramCamera.takePicture(null, null, null, this);
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera)
    {
        if(curtime == -1)
            curtime = System.currentTimeMillis();
        if (System.currentTimeMillis() - curtime <= 250) {
            try {
                String filename = String.format(getExternalCacheDir().getAbsolutePath() + "/SLR/%d.jpg", System.currentTimeMillis());
                Camera.Parameters parameters = camera.getParameters();
                Camera.Size size = parameters.getPreviewSize();
                YuvImage image = new YuvImage(data, parameters.getPreviewFormat(),
                        size.width, size.height, null);

                File file = new File(filename);
                FileOutputStream filecon = new FileOutputStream(file);
                image.compressToJpeg(
                        new Rect(0, 0, image.getWidth(), image.getHeight()), 45,
                        filecon);
                byte[] array = new byte[(int) file.length()];
                FileInputStream io = new FileInputStream(file);
                io.read(array);
                photos.add(array);
            } catch (IOException e) {
                Toast toast = Toast
                        .makeText(getBaseContext(), e.getMessage(), Toast.LENGTH_LONG);
                toast.show();
            }
        }
        else if (photoAvailable) {
            File directory = new File(getExternalCacheDir().getAbsolutePath() + "/SLR");

            if(!directory.isDirectory()){
                directory.mkdir();
            }

            String[] children = directory.list();

            byte[] bytes = PickBestImage.pickImage(photos);
            photos.clear();

            try {
                BufferedInputStream buf = new BufferedInputStream(new ByteArrayInputStream(bytes));
                buf.read(bytes, 0, bytes.length);
                buf.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            for (int i = 0; i < children.length; i++) {
                new File(directory, children[i]).delete();
            }

            ProcessImage image = new ProcessImage(bytes);
            new ImageSenderAsyncTask(this, getApplicationContext()).execute(image);
            photoAvailable = false;
        }
    }

    @Override
    public void publicResult(String s) {
        Log.d("Char", s);
        photoAvailable = true;
        curtime = System.currentTimeMillis();
        ValueAnimator fadeAnim = ObjectAnimator.ofFloat(messageFromServer, "alpha", 1f, 0f);
        fadeAnim.setDuration(150);
        fadeAnim.reverse();
        if (s.length() < 10) {
            messageFromServer.setText(s);
            message.append(s);
        }
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        intent.putExtra("message", message.toString());
        setResult(2, intent);
        super.onBackPressed();
    }
}
