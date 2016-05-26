package com.danielkim.soundrecorder.fragments;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.TextView;

import com.danielkim.soundrecorder.R;
import com.danielkim.soundrecorder.RecordingService;
import com.melnykov.fab.FloatingActionButton;

public class RecordFragment extends Fragment {
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_POSITION = "position";


    private static final String LOG_TAG = RecordFragment.class.getSimpleName();

    private static final String[] RECORD_PERMISSIONS = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.RECORD_AUDIO
    };
    final static int REQUEST_CODE_ALL_RECORD_PERMISSIONS = 1;

    private boolean requestPermissions() {
        for (String permission : RECORD_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(getActivity(), permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(getActivity(), RECORD_PERMISSIONS, REQUEST_CODE_ALL_RECORD_PERMISSIONS);
                if (ContextCompat.checkSelfPermission(getActivity(), permission) != PackageManager.PERMISSION_GRANTED) {
                    Intent setting_intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getActivity().getPackageName(), null);
                    setting_intent.setData(uri);
                    startActivityForResult(setting_intent, 0);
                }
                if (ContextCompat.checkSelfPermission(getActivity(), permission) != PackageManager.PERMISSION_GRANTED) {
                    Log.println(Log.ERROR, LOG_TAG, "insufficient permission to record");
                    return false;
                }
            }
        }
        return true;
    }

    private FloatingActionButton mRecordButton = null;
    private TextView mRecordingPrompt;
    private Chronometer mChronometer = null;

    private RecordingService recordingService;
    boolean isBound = false;
    boolean mStarted = false;
    boolean mStartPending = false;
    boolean mAllPermissions = false;

    private ServiceConnection recordingServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            RecordingService.MyBinder binder = (RecordingService.MyBinder) service;
            recordingService = binder.getService();
            isBound = true;
            if(recordingService.isRecording() && !mStarted) {
                startGui();
            }else if(mStartPending){
                startRecord();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            recordingService = null;
            isBound = false;
        }
    };

    public static RecordFragment newInstance(int position) {
        RecordFragment f = new RecordFragment();
        Bundle b = new Bundle();
        b.putInt(ARG_POSITION, position);
        f.setArguments(b);

        return f;
    }

    public RecordFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //position = getArguments().getInt(ARG_POSITION);
        Intent intent = new Intent(getActivity(), RecordingService.class);
        if(!getActivity().bindService(intent, recordingServiceConnection, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT)){
            Log.e(LOG_TAG, "recording service is not bound.");
        }
    }

    private void startGui() {
        mStarted = true;
        long r_start =  recordingService.getStartTime();
        long c_time = SystemClock.elapsedRealtime();
        mChronometer.setBase(r_start);
        mChronometer.start();
        //keep screen on while recording
        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mRecordingPrompt.setText(getString(R.string.record_in_progress));
        mStartPending = false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View recordView = inflater.inflate(R.layout.fragment_record, container, false);

        mChronometer = (Chronometer) recordView.findViewById(R.id.chronometer);
        //update recording prompt text
        mRecordingPrompt = (TextView) recordView.findViewById(R.id.recording_status_text);
        mAllPermissions = requestPermissions();

        mRecordButton = (FloatingActionButton) recordView.findViewById(R.id.btnRecord);
        mRecordButton.setColorNormal(getResources().getColor(R.color.primary));
        mRecordButton.setColorPressed(getResources().getColor(R.color.primary_dark));

        if(!mAllPermissions) {
            mRecordingPrompt.setText(getString(R.string.notification_insufficient_permission));
        }

        if(isBound && recordingService.isRecording()){
            startGui();
        }

        mRecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!mAllPermissions) {
                    return;
                }

                if(mStartPending || mStarted){
                    stopRecord();
                }else{
                    startRecord();
                }
            }
        });
        return recordView;
    }

    // Recording Start/Stop

    private void startRecord() {
        if(mStarted){
            Log.e(LOG_TAG, "record command already issued");
            return;
        }
        if(isBound) {
            recordingService.startRecording();
            startGui();
        }else{
            mStartPending = true;
            mRecordingPrompt.setText(getString(R.string.notification_record_pending));
        }
    }

    private void stopRecord(){
        if(isBound){
            recordingService.stopRecording();
        }

        mRecordingPrompt.setText(getString(R.string.record_prompt));

        if(mStarted){
            mChronometer.stop();
            mChronometer.setBase(SystemClock.elapsedRealtime());
            getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            mStarted = false;
        }
        mStartPending = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().unbindService(recordingServiceConnection);
    }
}