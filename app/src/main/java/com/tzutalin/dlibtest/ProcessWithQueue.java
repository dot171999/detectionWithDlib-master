package com.tzutalin.dlibtest;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.media.Image;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Toast;

import com.tzutalin.dlib.Constants;
import com.tzutalin.dlib.FaceDet;
import com.tzutalin.dlib.VisionDetRet;

import junit.framework.Assert;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

import static android.content.Context.MODE_PRIVATE;

public class ProcessWithQueue extends Thread {
    private static final String TAG = "Queue";
    private static final String EyesBlinkDetection = "eyesBlinkDetection";

    private LinkedBlockingQueue<Bitmap> mQueue;
    private LinkedBlockingQueue<Bitmap> frameForDisplay;

    private static String checkMode = null;

    private List<VisionDetRet> results;

    private Handler mInferenceHandler;
    private Context mContext;
    private FaceDet mFaceDet;
    private TrasparentTitleView mTransparentTitleView;
    private FloatingCameraWindow mWindow;
    private Paint mFaceLandmardkPaint;
    private Paint mFaceLandmardkPaint1;

    private int mframeNum = 0;

    private double ear = 0;
    private int x = 0;
    private boolean ear_array_removed = false;
    private boolean drop_array_appended = false;
    private double THRESH = 0.04;
    private double DROP_THRESH = 0.065;
    private ArrayList<Double> ear_array = new ArrayList<>();
    private ArrayList<Integer> ax = new ArrayList<>();
    private ArrayList<Double> ay = new ArrayList<>();
    private int continuous_Increment = 0;
    private int continuous_Decrement = 0;
    private double drop = 0;
    private ArrayList<Double> drop_array = new ArrayList<>();
    private int temp = 0;
    private double closeEyes_drop = -5;
    private double openEyes_drop = 0;
    private int closeEyes_end = 0;
    private int openEyes_start = 0;
    private int blink = 0;
    private int frames_notFoundFace = 0;


    private SharedPreferences mSharedPreferences;


    public ProcessWithQueue(LinkedBlockingQueue<Bitmap> frameQueue, LinkedBlockingQueue<Bitmap> frameQueueForDisplay, Context context, TrasparentTitleView scoreView, Handler handler) {
        this.mContext = context;
        this.mTransparentTitleView = scoreView;
        this.mInferenceHandler = handler;

        mQueue = frameQueue;
        frameForDisplay = frameQueueForDisplay;

        mFaceDet = new FaceDet(Constants.getFaceShapeModelPath());
        mWindow = new FloatingCameraWindow(mContext);

        mFaceLandmardkPaint = new Paint();
        mFaceLandmardkPaint.setColor(Color.GREEN);
        mFaceLandmardkPaint.setStrokeWidth(2);
        mFaceLandmardkPaint.setStyle(Paint.Style.STROKE);

        mFaceLandmardkPaint1 = new Paint();
        mFaceLandmardkPaint1.setColor(Color.RED);
        mFaceLandmardkPaint1.setStrokeWidth(2);
        mFaceLandmardkPaint1.setStyle(Paint.Style.STROKE);

        mSharedPreferences = context.getSharedPreferences("userInfo", MODE_PRIVATE);
        checkMode = mSharedPreferences.getString("detectionMode","");
        startTimer();
        start();
    }

    public void release(){
        if (mFaceDet != null) {
            mFaceDet.release();
        }

        if (mWindow != null) {
            mWindow.release();
        }
    }

    @Override
    public void run() {
        while (true) {
            Bitmap frameData = null;
            Bitmap framefordisplay = null;
            try {
                frameData = mQueue.take();
                framefordisplay = frameForDisplay.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (frameData == null) {
                break;
            }
            processFrame(frameData, framefordisplay);
        }
    }

    private void processFrame(final Bitmap frameData, final Bitmap framefordisplay) {

        if(frameData != null){
            mInferenceHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {

                            if (!new File(Constants.getFaceShapeModelPath()).exists()) {
                                mTransparentTitleView.setText("Please WAIT... " + Constants.getFaceShapeModelPath());
                                FileUtils.copyFileFromRawToOthers(mContext, R.raw.shape_predictor_68_face_landmarks, Constants.getFaceShapeModelPath());
                            }

                            mframeNum++;

                            switch (checkMode){

                                case EyesBlinkDetection:{

                                    results = mFaceDet.detect(frameData);

                                    if (results.size() != 0) {
                                        for (final VisionDetRet ret : results) {
                                            float resizeRatio = 4f;
                                            Canvas canvas = new Canvas(framefordisplay);

                                            ArrayList<Point> landmarks = ret.getFaceLandmarks();

                                            int i = 1;

                                            //get the 6 key point from 68_face_landmarks
                                            Point[] leftEye = new Point[6];
                                            Point[] rightEye = new Point[6];

                                            for (Point point : landmarks) {
                                                if (i > 36 && i < 43) {
                                                    //for more efficient procession, the data we process were zoomed out
                                                    //So the point must be magnified , to display correctly in the original image.
                                                    int pointX = (int) (point.x * resizeRatio);
                                                    int pointY = (int) (point.y * resizeRatio);
                                                    leftEye[i - 37] = new Point(pointX, pointY);
                                                canvas.drawCircle(pointX, pointY, 2, mFaceLandmardkPaint1);
                                                } else if (i > 42 && i < 49) {
                                                    int pointX = (int) (point.x * resizeRatio);
                                                    int pointY = (int) (point.y * resizeRatio);
                                                    rightEye[i - 43] = new Point(pointX, pointY);
                                                canvas.drawCircle(pointX, pointY, 2, mFaceLandmardkPaint1);
                                                }
                                                if (i > 48) {
                                                    break;
                                                }
                                                i++;
                                            }

                                            canvas.drawPath(getPath(leftEye), mFaceLandmardkPaint);
                                            canvas.drawPath(getPath(rightEye), mFaceLandmardkPaint);

                                            double leftEAR = eye_aspect_ratio(leftEye);
                                            double rightEAR = eye_aspect_ratio(rightEye);
                                            ear = (leftEAR + rightEAR) / 2.0;
                                        }
                                    } else {
                                        frames_notFoundFace++;
                                        Log.i("frames_notFoundFace", String.valueOf(frames_notFoundFace));
                                    }

                                    if (ear != 0) {

                                        //codes below are difficult to read,but it dose work
                                        x += 1;
                                        ear_array.add(ear);
                                        ax.add(x);
                                        ay.add(ear);
                                        ear_array_removed = filter_unexpected_values(ear_array, ax, THRESH);

                                        if (ear_array.size() > 2 && !ear_array_removed) {
                                            if (ear_array.get(ear_array.size() - 2) > ear_array.get(ear_array.size() - 3)) {
                                                continuous_Increment += 1;
                                                if (continuous_Decrement != 0) {
                                                    drop = ear_array.get(ear_array.size() - 3) - ear_array.get(ear_array.size() - 3 - continuous_Decrement);
                                                    if (continuous_Decrement != 1) {
                                                        drop_array.add(drop);
                                                        drop_array_appended = true;
                                                    }
                                                    temp = continuous_Decrement;
                                                    continuous_Decrement = 0;
                                                }
                                            } else if (ear_array.get(ear_array.size() - 2) < ear_array.get(ear_array.size() - 3)) {
                                                continuous_Decrement += 1;
                                                if (continuous_Increment != 0) {
                                                    drop = ear_array.get(ear_array.size() - 3) - ear_array.get(ear_array.size() - 3 - continuous_Increment);
                                                    if (continuous_Increment != 1) {
                                                        drop_array.add(drop);
                                                        drop_array_appended = true;
                                                    }
                                                    temp = continuous_Increment;
                                                    continuous_Increment = 0;
                                                }
                                            }
                                        }

                                        if (drop_array_appended) {
                                            if (drop_array.get(drop_array.size() - 1) < -DROP_THRESH) {
                                                closeEyes_drop = drop_array.get(drop_array.size() - 1);
                                                closeEyes_end = ax.get(ax.size() - 3);
                                            }
                                            if (drop_array.get(drop_array.size() - 1) > DROP_THRESH) {
                                                openEyes_drop = drop_array.get(drop_array.size() - 1);
                                                openEyes_start = ax.get(ax.size() - 3 - temp);
                                                if (Math.abs(closeEyes_drop + openEyes_drop) < 0.1 && ear_array.get(ear_array.size() - 3 - temp) < 0.21
                                                        && openEyes_start - closeEyes_end < 20) {
                                                    blink += 1;
                                                    generateNoteOnSD(mContext,"log",timeStamp()+" Blink="+blink);
                                                    closeEyes_drop = -5;
                                                }
                                            }
                                        }

                                    }
                                    mTransparentTitleView.setText(" Blink: " + String.valueOf(blink));

                                    Log.i("processingFrame", String.valueOf(mframeNum));
                                    mWindow.setRGBBitmap(framefordisplay);

                                }break;
                            }
                        }

                    });
        }
    }


    private Timer timer;
    private TimerTask timerTask;

    public void startTimer() {
        //set a new Timer
        timer = new Timer();

        //initialize the TimerTask's job
        initializeTimerTask();

        //schedule the timer, to wake up every 1 second
        timer.schedule(timerTask, 1000, 1000); //
    }

    public void initializeTimerTask() {
        timerTask = new TimerTask() {
            public void run() {
                Log.i("in timer", "in timer ++++  "+ (blink));
                try {
                    File root = new File(Environment.getExternalStorageDirectory(), "Blink");
                    if (!root.exists()) {
                        root.mkdirs();
                    }
                    File gpxfile = new File(root, "log"+".txt");
                    FileWriter writer = new FileWriter(gpxfile,true);
                    writer.append(timeStamp()+" Blink="+blink);
                    writer.append("\n\r");
                    writer.flush();
                    writer.close();
                    //Toast toast= Toast.makeText(context,
                    //"Logged", Toast.LENGTH_SHORT);
                    //toast.setGravity(Gravity.BOTTOM|Gravity.END, 0, 0);
                    //toast.show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
    }

    public void stoptimertask() {
        //stop the timer, if it's not already null
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    public void generateNoteOnSD(Context context, String sFileName, String sBody) {
        try {
            File root = new File(Environment.getExternalStorageDirectory(), "Blink");
            if (!root.exists()) {
                root.mkdirs();
            }
            File gpxfile = new File(root, sFileName+".txt");
            FileWriter writer = new FileWriter(gpxfile,true);
            writer.append(sBody);
            writer.append("\n\r");
            writer.flush();
            writer.close();
            //Toast toast= Toast.makeText(context,
                    //"Logged", Toast.LENGTH_SHORT);
            //toast.setGravity(Gravity.BOTTOM|Gravity.END, 0, 0);
            //toast.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    String timeStamp(){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy-hh-mm-ss");
        String format = simpleDateFormat.format(new Date());
        return format;
    }

    private double eye_aspect_ratio(Point[] eye){
        double ear = 0;
        double A = euclidean(eye[1],eye[5]);
        double B = euclidean(eye[2],eye[4]);
        double C = euclidean(eye[0],eye[3]);
        ear = (A + B) / (2.0 * C);
        return  ear;
    }

    private double euclidean(Point p1, Point p2){
        double result = 0;
        result = Math.sqrt(Math.pow((p1.x-p2.x),2)+Math.pow((p1.y-p2.y),2));
        return result;
    }

    private static boolean filter_unexpected_values(ArrayList<Double> AL, ArrayList<Integer> ax, double THRESH){
        if(AL.size()>2){
            if(AL.get(AL.size()-3) < AL.get(AL.size()-1)){
                if(AL.get(AL.size()-3) > AL.get(AL.size()-2) && AL.get(AL.size()-3)-AL.get(AL.size()-2)>THRESH){
                    AL.remove(AL.size()-2);
                    ax.remove(AL.size()-2);
                    return true;
                }
            }else if(AL.get(AL.size()-3) > AL.get(AL.size()-1)){
                if(AL.get(AL.size()-2) > AL.get(AL.size()-3) && AL.get(AL.size()-2)-AL.get(AL.size()-3)>THRESH){
                    AL.remove(AL.size()-2);
                    ax.remove(AL.size()-2);
                    return true;
                }
            }
        }
        return false;
    }

    private Path getPath(Point[] points){
        Path path = new Path();
        path.moveTo(points[0].x, points[0].y);
        for(int i = 1; i < points.length; i++){
            path.lineTo(points[i].x, points[i].y);
        }
        path.close();
        return path;
    }

}
