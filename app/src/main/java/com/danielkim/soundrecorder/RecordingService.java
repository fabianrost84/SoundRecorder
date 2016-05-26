package com.danielkim.soundrecorder;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.danielkim.soundrecorder.activities.MainActivity;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class RecordingService extends Service {

    private static final String LOG_TAG = "RecordingService";
    private static final int RECORDING_NOTE_ID = 1;
    private static final SimpleDateFormat mTimerFormat = new SimpleDateFormat("mm:ss", Locale.getDefault());

    private IBinder myBinder = new MyBinder();

    private String mFileName = null;
    private String mFilePath = null;

    private MediaRecorder mRecorder = null;
    private NotificationCompat.Builder mNotificationBuilder;
    private NotificationManager notifyManager = null;
    private Timer mTimer = null;
    private TimerTask mIncrementTimerTask = null;
    private OnTimerChangedListener onTimerChangedListener = null;

    private LinkedHashSet<Intent> mIntends;


    private long mStartingTimeMillis = -1;
    private boolean mRecording = false;

    @Override
    public IBinder onBind(Intent intent) {
        mIntends.add(intent);
        return myBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (mIntends.contains(intent))
            mIntends.remove(intent);
        return false;
    }

    public interface OnTimerChangedListener {
        void onTimerChanged();
    }

    public class MyBinder extends Binder {
        public RecordingService getService() {
            return RecordingService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mIntends = new LinkedHashSet<>();
        notifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationBuilder = new NotificationCompat.Builder(getApplicationContext());
        mNotificationBuilder.setSmallIcon(R.drawable.ic_mic_white_36dp);
        mTimer = new Timer();
    }

    @Override
    public void onDestroy() {
        if (mRecording) {
            stopRecording();
        }
        super.onDestroy();
    }

    public boolean isRecording(){
        return mRecording;
    }

    public long getStartTime(){
        if(!mRecording) return -1;
        return mStartingTimeMillis;
    }

    public long getRecordTime(){
        return SystemClock.elapsedRealtime() - mStartingTimeMillis;
    }

    public void startRecording() {

        if(mRecording){
            Log.e(LOG_TAG, "trying to start running recorder");
            return;
        }
        setFileNameAndPath();

        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mRecorder.setOutputFile(mFilePath);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC);
        // the following 2 options are taken from the Soundcloud android app
        mRecorder.setAudioEncodingBitRate(96000);
        mRecorder.setAudioSamplingRate(44100);
        mRecorder.setAudioChannels(1);

        try {
            mRecorder.prepare();
            mRecorder.start();
            mStartingTimeMillis = SystemClock.elapsedRealtime();
            mRecording = true;

            startTimer();

        } catch (IOException e) {
            Log.e(LOG_TAG, "prepare() failed");
            mRecorder.release();
            mRecorder = null;
        }
    }

    public void setFileNameAndPath(){
        int count = 0;
        File f;

        do{
            count++;
            mFileName = getString(R.string.default_file_name) + "-" + count + ".mp4";
            mFilePath = "/storage/sdcard1/";
            mFilePath += "/" + getString(R.string.storage_dir) + "/" + mFileName;

            f = new File(mFilePath);
        }while (f.exists() && !f.isDirectory());
    }

    public void stopRecording() {
        if(!mRecording){
            Log.e(LOG_TAG, "stop recording while not started");
            return;
        }

        if (mIncrementTimerTask != null) {
            mIncrementTimerTask.cancel();
            mTimer.purge();
            mIncrementTimerTask = null;
        }

        if (mRecorder != null) {
            mRecorder.stop();
            mRecorder.reset();
            long mElapsedMillis = getRecordTime();
            mRecorder.release();
            mRecorder = null;
            Toast.makeText(this, getString(R.string.toast_recording_finish) + " " + mFilePath, Toast.LENGTH_LONG).show();

            String mimeType = "audio/mp4";

            File outFile = new File(mFilePath);
            long fileSize = outFile.length();

            ContentValues values = new ContentValues(12);
            values.put(MediaStore.MediaColumns.DATA, mFilePath);
            values.put(MediaStore.MediaColumns.TITLE, mFileName);
            values.put(MediaStore.MediaColumns.SIZE, fileSize);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            values.put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000);
            values.put(MediaStore.Audio.Media.ARTIST, getString(R.string.app_name));
            values.put(MediaStore.Audio.Media.DURATION, mElapsedMillis);
            values.put(MediaStore.Audio.Media.IS_PODCAST, true);

            Uri uri = MediaStore.Audio.Media.getContentUriForPath(mFilePath);
            getContentResolver().insert(uri, values);

        } else {
            Log.e(LOG_TAG, "MediaRecorder not initialized");
        }

        mRecording = false;
        stopForeground(true);
    }

    private void startTimer() {
        mNotificationBuilder.setContentIntent(PendingIntent.getActivities(getApplicationContext(), 0,
                new Intent[]{new Intent(getApplicationContext(), MainActivity.class)}, PendingIntent.FLAG_UPDATE_CURRENT));

        mNotificationBuilder.setContentTitle(getString(R.string.notification_recording));
        mNotificationBuilder.setContentText(mTimerFormat.format(0));

        startForeground(1, mNotificationBuilder.build());

        mIncrementTimerTask = new TimerTask() {
            @Override
            public void run() {
                if (onTimerChangedListener != null)
                    onTimerChangedListener.onTimerChanged();
                mNotificationBuilder.setContentText(mTimerFormat.format(getRecordTime()));
                notifyManager.notify(RECORDING_NOTE_ID, mNotificationBuilder.build());
            }
        };
        mTimer.scheduleAtFixedRate(mIncrementTimerTask, 1000, 1000);
    }
}
