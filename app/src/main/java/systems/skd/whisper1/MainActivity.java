package systems.skd.whisper1;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Arrays;

import static java.security.AccessController.getContext;

public class MainActivity extends AppCompatActivity {


    ImageView ivIcon;
    Button bSelectFile;
    TextView tvInfo;
    SeekBar sbTrigger;
    TextView tvFileName;
    MediaPlayer mediaPlayer;
    int triggerLevel = 2000;
    final String TAG = "myLogs";
    boolean isPlaying = false;
    boolean isLogging = false;
    int myBufferSize = 128;
    final int SAMPLE_RATE = 8000;
    AudioRecord audioRecord;
    AudioTrack audioTrack;
    boolean isReading = false;
    private final int REQUEST_AUDIO = 1;
    BroadcastReceiver broadcastReceiver;
    Handler handler;

    boolean wiredHeadsetConnected;
    boolean btHeadsetConnected;

    class MyBroadcastReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
                int i = intent.getIntExtra("state", -1);
                if (i == 0) {
                    wiredHeadsetConnected = false;
                    if(btHeadsetConnected == false){
                        tvInfo.setText("Connect headset");
                        bSelectFile.setEnabled(false);
                        Toast.makeText(getApplicationContext(),"Headset disconnected",Toast.LENGTH_SHORT).show();
                    }


                }if (i == 1) {
                    wiredHeadsetConnected = true;
                    tvInfo.setText("Select file you want to play");
                    bSelectFile.setEnabled(true);
                    Toast.makeText(getApplicationContext(),"Headset connected",Toast.LENGTH_SHORT).show();
                }
            }
        }
    }


    private void resetAll(){
        sbTrigger.setEnabled(false);
        ivIcon.setImageResource(R.drawable.ic_mic_off_black_24dp);
        tvInfo.setText("Select file you want to play");
        if(wiredHeadsetConnected || btHeadsetConnected){

        }else{
            tvInfo.setText("Connect headset");
        }
        tvFileName.setText("No file selected");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        bSelectFile = findViewById(R.id.b_select_file);
        tvInfo = findViewById(R.id.tv_info);
        ivIcon = findViewById(R.id.iv_icon);
        sbTrigger = findViewById(R.id.sb_trigger);
        tvFileName = findViewById(R.id.tv_file_name);
        mediaPlayer = new MediaPlayer();
        if(isBluetoothHeadsetConnected()){
            btHeadsetConnected = true;
        }else{
            btHeadsetConnected = false;
        }

        resetAll();
        createAudioRecorder();
 //       createAudioTracker();

        broadcastReceiver = new MyBroadcastReceiver();
        IntentFilter receiverFilter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(broadcastReceiver, receiverFilter);

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        this.registerReceiver(mReceiver, filter);

        Log.d(TAG, "init state = " + audioRecord.getState());

        handler = new Handler(){
            @Override
            public void handleMessage(Message msg){
                if(msg.what == 1){
                    ivIcon.setImageResource(R.drawable.ic_mic_red_24dp);
                    mediaPlayer.start();
                }else{
                    ivIcon.setImageResource(R.drawable.ic_mic_black_24dp);
                    mediaPlayer.pause();
                }

            }
        };




        bSelectFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent_upload = new Intent();
                intent_upload.setType("audio/*");
                intent_upload.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent_upload,1);
                mediaPlayer.stop();
                audioRecord.stop();
            }
        });


        sbTrigger.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {


                if(isReading){
                    tvInfo.setText("" + progress);

                }else{
                    tvInfo.setText("Select file");
                }

                if(progress > 0){
                    triggerLevel = progress * 50;



                }else{
                    tvInfo.setText("Stop playing");
                    audioRecord.stop();
                    mediaPlayer.stop();
                    mediaPlayer.reset();
                    isReading = false;
                }

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });




        if(checkPermissionFromDevice()){

        }else{

            requestPermission();

        }


    }





    public static boolean isBluetoothHeadsetConnected() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()
                && mBluetoothAdapter.getProfileConnectionState(BluetoothHeadset.HEADSET) == BluetoothHeadset.STATE_CONNECTED;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if(isBluetoothHeadsetConnected()){
                btHeadsetConnected = true;
                tvInfo.setText("Headset connected");
            }else{
                btHeadsetConnected = false;
                if(wiredHeadsetConnected == false){
                    tvInfo.setText("Connect headset");
                }

            }


        }
    };

    public String getNameFromURI(Uri uri) {
        Cursor c = getContentResolver().query(uri, null, null, null, null);
        c.moveToFirst();
        return c.getString(c.getColumnIndex(OpenableColumns.DISPLAY_NAME));
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == 1 && resultCode == RESULT_OK){
            sbTrigger.setEnabled(true);
            ivIcon.setImageResource(R.drawable.ic_mic_black_24dp);
            Uri uri = data.getData();
            String s = getNameFromURI(uri);
            tvFileName.setText(s);
            startListening();
            tvInfo.setText("Start speaking");


            mediaPlayer.reset();
            try {
                // mediaPlayer.setDataSource(String.valueOf(myUri));
                mediaPlayer.setDataSource(MainActivity.this,uri);
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                mediaPlayer.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void startListening(){
        Log.d(TAG, "record start");
        audioRecord.startRecording();
        int recordingState = audioRecord.getRecordingState();
        Log.d(TAG, "recordingState = " + recordingState);

        sbTrigger.setProgress(40);


        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "start thread");
                if (audioRecord == null)
                    return;
                isReading = true;
                short[] myBuffer = new short[myBufferSize];
                int counter = 0;


                //audioTrack.play();
                while (isReading) {

                    audioRecord.read(myBuffer, 0, myBufferSize);
                    //audioTrack.write(myBuffer, 0, readCount);

                    for(short i : myBuffer){
                        if(i > triggerLevel){
                            if(counter == 0)handler.sendEmptyMessage(1);
                            counter = 30;
                            Log.d(TAG, "is speaking");

                            break;
                        }
                    }
                    if(counter != 0){
                        counter--;
                        if(counter == 1)handler.sendEmptyMessage(0);
                    }

                    Log.d(TAG, "myBuffer = " + Arrays.toString(myBuffer));
                }

            }
        }).start();


    }

//    void createAudioTracker(){
//
//        int mBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
//                AudioFormat.ENCODING_PCM_16BIT);
//        if (mBufferSize == AudioTrack.ERROR || mBufferSize == AudioTrack.ERROR_BAD_VALUE) {
//            // For some readon we couldn't obtain a buffer size
//            mBufferSize = SAMPLE_RATE * 2;
//        }
//        audioTrack = new AudioTrack(
//                AudioManager.STREAM_MUSIC,
//                SAMPLE_RATE,
//                AudioFormat.CHANNEL_OUT_MONO,
//                AudioFormat.ENCODING_PCM_16BIT,
//                mBufferSize,
//                AudioTrack.MODE_STREAM);
//    }

    void createAudioRecorder() {
        int sampleRate = SAMPLE_RATE;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

        int minInternalBufferSize = AudioRecord.getMinBufferSize(sampleRate,
                channelConfig, audioFormat);
        int internalBufferSize = minInternalBufferSize * 4;
        Log.d(TAG, "minInternalBufferSize = " + minInternalBufferSize
                + ", internalBufferSize = " + internalBufferSize
                + ", myBufferSize = " + myBufferSize);

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                sampleRate, channelConfig, audioFormat, internalBufferSize);

//        audioRecord.setPositionNotificationPeriod(1000);
//
//        audioRecord.setNotificationMarkerPosition(10000);
//        audioRecord.setRecordPositionUpdateListener(new AudioRecord.OnRecordPositionUpdateListener() {
//                    public void onPeriodicNotification(AudioRecord recorder) {
//                        Log.d(TAG, "onPeriodicNotification");
//                    }
//
//                    public void onMarkerReached(AudioRecord recorder) {
//                        Log.d(TAG, "onMarkerReached");
//                        isReading = false;
//                    }
//                });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        isReading = false;
        if (audioRecord != null) {
            audioRecord.release();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == 1000){
            Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();

        }else{
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestPermission(){
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO}, 1000);
    }

    private boolean checkPermissionFromDevice(){
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
    }







}
