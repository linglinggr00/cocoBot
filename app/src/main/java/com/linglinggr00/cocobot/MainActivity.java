package com.linglinggr00.cocobot;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import ai.kitt.snowboy.AppResCopy;
import ai.kitt.snowboy.MsgEnum;
import ai.kitt.snowboy.audio.AudioDataSaver;
import ai.kitt.snowboy.audio.RecordingThread;
import pub.devrel.easypermissions.EasyPermissions;

import static com.linglinggr00.cocobot.GlobalConfig.AUDIO_FORMAT;
import static com.linglinggr00.cocobot.GlobalConfig.CHANNEL_CONFIG;
import static com.linglinggr00.cocobot.GlobalConfig.SAMPLE_RATE_INHZ;

public class MainActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {

    //permission
    private static final int RC_AUDIO_AND_WRITE = 100 ;
    private boolean hasAllPermission = false;

    //snow
    private RecordingThread recordingThread = null;
    private boolean isDetectionOn = false;

    //audio record
    private static final String TAG = "recordLog";
    private boolean isRecording;
    private AudioRecord audioRecord;
    int count = 0;

    //firebase
    private FirebaseAuth mAuth;
    private String Uid;
    private String audio, status, uid; //send
    private String res_text, emo, user_name, res_status; //response
    private TextView textView;

    //ui state
    static private final int SNOW_START = 1;
    static private final int SNOW_RESULT = 2;
    static private final int AUDIO_START = 3;
    static private final int AUDIO_STOP  = 4;
    static private final int DATA_UPLOAD  = 5;
    static private final int DATA_GET  = 6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        methodRequiresTwoPermission();

        if(hasAllPermission) {
            //初始化語音喚醒
            initHotword();
            snowStart();
        }

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        //uid
        Uid = mAuth.getUid();
        System.out.println(Uid);

        //停止錄音＆轉檔
        Button btn = findViewById(R.id.btn);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecord();
            }
        });

        textView = findViewById(R.id.showText);

    }

    //設定UI狀態
    private void setUiState(int state) {
        switch (state) {
            case SNOW_START:
                Toast.makeText(this, "請呼叫Coco喚醒", Toast.LENGTH_SHORT).show();
                break;
            case SNOW_RESULT:
                Toast.makeText(this, "Coco已被喚醒", Toast.LENGTH_SHORT).show();
                break;
            case AUDIO_START:
                Toast.makeText(this, "錄音開始", Toast.LENGTH_SHORT).show();
                break;
            case AUDIO_STOP:
                Toast.makeText(this, "錄音結束", Toast.LENGTH_SHORT).show();
                uploadAudio();
                break;
            case DATA_UPLOAD:
                Toast.makeText(this, "上傳資料成功", Toast.LENGTH_SHORT).show();
                break;
            case DATA_GET:
                Toast.makeText(this, "抓取資料成功", Toast.LENGTH_SHORT).show();
                timeSleep();
                snowStart();
                break;

        }
    }

    //暫停1秒
    private void timeSleep() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    //上傳音檔
    private void uploadAudio() {
        Uri file = Uri.fromFile(new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "test.wav"));
        StorageReference reference = FirebaseStorage.getInstance().getReference()
                .child("Audio")
                .child("audio.wav");

        reference.putFile(file)
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        updateData();   //上傳資料
                        System.out.println("錄音上傳成功");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        // Handle unsuccessful uploads
                        System.out.println("錄音上傳錯誤");
                    }
                });
    }

    private void updateData() {
        //audio, uid
        audio = "true";
        status = "true";

        FirebaseDatabase rootNode = FirebaseDatabase.getInstance();
        DatabaseReference reference = rootNode.getReference("User");

        reference.child("audio").setValue(audio);
        reference.child("uid").setValue(uid);
        reference.child("status").setValue(status);

        setUiState(DATA_UPLOAD);

        getData();

    }

    private void getData() {
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference().child("Bot");
        reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists()){
                    if(snapshot.child("status").getValue().toString().equals("true")) {
                        res_text = snapshot.child("res_text").getValue().toString();
                        emo = snapshot.child("emo").getValue().toString();
                        user_name = snapshot.child("user_name").getValue().toString();
                        Uid = snapshot.child("uid").getValue().toString();
                        res_status = snapshot.child("status").getValue().toString();
                        textView.setText("Response: " + res_text + "  Emotion: " + emo + "  Name: " + user_name + "  Uid: " + Uid + "  Status: " + res_status);
                        setBotStatus();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                System.out.println("抓取資料錯誤");
            }
        });
    }

    private void setBotStatus() {
        FirebaseDatabase rootNode = FirebaseDatabase.getInstance();
        DatabaseReference reference = rootNode.getReference("Bot");

        reference.child("status").setValue("false");
        setUiState(DATA_GET);
    }

    //開始錄音
    private void startRecord() {
        setUiState(AUDIO_START);

        final int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_INHZ, CHANNEL_CONFIG, AUDIO_FORMAT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE_INHZ,
                CHANNEL_CONFIG, AUDIO_FORMAT, minBufferSize);

        final byte data[] = new byte[minBufferSize];
        final File file = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "test.pcm");
        if (!file.mkdirs()) {
            Log.e(TAG, "Directory not created");
        }
        if (file.exists()) {
            file.delete();
        }

        audioRecord.startRecording();
        isRecording = true;

        new Thread(new Runnable() {
            @Override
            public void run() {

                FileOutputStream os = null;
                try {
                    os = new FileOutputStream(file);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

                if (null != os) {
                    while (isRecording) {
                        int read = audioRecord.read(data, 0, minBufferSize);
                        // 如果讀取音頻數據沒有出現錯誤，就將數據寫到文件
                        if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                            try {
                                os.write(data);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    try {
                        Log.i(TAG, "run: close file output stream !");
                        os.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    //停止錄音
    private void stopRecord() {
        setUiState(AUDIO_STOP);
        //暫停1秒
        timeSleep();

        isRecording = false;
        pcmToWav();
        //釋放資源
        if (null != audioRecord) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }

    }

    //轉檔
    private void pcmToWav() {
        PcmToWavUtil pcmToWavUtil = new PcmToWavUtil(SAMPLE_RATE_INHZ, CHANNEL_CONFIG, AUDIO_FORMAT);
        File pcmFile = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "test.pcm");
        File wavFile = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "test.wav");
        if (!wavFile.mkdirs()) {
            Log.e(TAG, "wavFile Directory not created");
        }
        if (wavFile.exists()) {
            wavFile.delete();
        }
        pcmToWavUtil.pcmToWav(pcmFile.getAbsolutePath(), wavFile.getAbsolutePath());


    }

    //語音喚醒開始
    private void snowStart() {
        setUiState(SNOW_START);
        timeSleep();

        if(recordingThread !=null && !isDetectionOn) {
            recordingThread.startRecording();
            isDetectionOn = true;
        }
    }

    //語音喚醒暫停
    private void snowStop() {
        if(recordingThread !=null && isDetectionOn){
            recordingThread.stopRecording();
            isDetectionOn = false;
        }
        //暫停1秒
        timeSleep();
    }

    private void initHotword() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            AppResCopy.copyResFromAssetsToSD(this);

            recordingThread = new RecordingThread(new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    MsgEnum message = MsgEnum.getMsgEnum(msg.what);
                    switch(message) {
                        case MSG_ACTIVE:
                            setUiState(SNOW_RESULT);
                            System.out.println("COCO已被喚醒");
                            snowStop();
                            System.out.println("開始錄音Thread");
                            startRecord();
                            countRecord();
                            break;
                        case MSG_INFO:
                            break;
                        case MSG_VAD_SPEECH:
                            break;
                        case MSG_VAD_NOSPEECH:
                            break;
                        case MSG_ERROR:
                            break;
                        default:
                            super.handleMessage(msg);
                            break;
                    }
                }
            }, new AudioDataSaver());
        }
    }

    private void countRecord() {
        count = 0;
      Handler handler = new Handler();
      new Thread(new Runnable() {
          @Override
          public void run() {
              if(count==5) {
                  stopRecord();
                  handler.removeCallbacks(this::run);
              }

              count++;
              handler.postDelayed(this, 1000);
          }
      }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    private void methodRequiresTwoPermission() {
        String[] perms = {Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        if (EasyPermissions.hasPermissions(this, perms)) {
            // Already have permission, do the thing
            hasAllPermission = true;

        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(this, "請允許權限",
                    RC_AUDIO_AND_WRITE, perms);
        }
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {

    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        methodRequiresTwoPermission();
    }

    private void updateUI(FirebaseUser currentUser) {
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        updateUI(currentUser);
        mAuth.signInAnonymously();
        uid = Uid;

        if(!isDetectionOn && hasAllPermission)
            snowStart();
    }

    @Override
    protected void onStop() {
        if(isRecording)
            stopRecord();
        if(isDetectionOn)
            snowStop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if(isRecording)
            stopRecord();
        if(isDetectionOn)
            snowStop();
        super.onDestroy();
    }
}