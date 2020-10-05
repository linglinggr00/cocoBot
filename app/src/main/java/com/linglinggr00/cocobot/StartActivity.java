package com.linglinggr00.cocobot;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Intent;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import pub.devrel.easypermissions.EasyPermissions;

import static com.linglinggr00.cocobot.GlobalConfig.AUDIO_FORMAT;
import static com.linglinggr00.cocobot.GlobalConfig.CHANNEL_CONFIG;
import static com.linglinggr00.cocobot.GlobalConfig.SAMPLE_RATE_INHZ;

public class StartActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {

    //permission
    private static final int RC_AUDIO_AND_WRITE = 100 ;
    private boolean hasAllPermission = false;

    //audio record
    private static final String TAG = "recordLog";
    private boolean isRecording;
    private AudioRecord audioRecord;
    int count = 0;

    private Button nextBtn;
    private ImageButton recordBtn;
    private Button sendBtn;

    private EditText nameEdt;
    private String name;
    private FirebaseAuth mAuth;
    private String Uid;
    private String new_name, new_audio ,new_status;

    //ui state
    static private final int RECORD_START = 1;
    static private final int RECORD_STOP = 2;
    static private final int RECORD_UPLOAD = 3;

    static private final int IDENTITY_OK = 1;
    static private final int IDENTITY_REPEAT = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        methodRequiresTwoPermission();

        if(hasAllPermission)
            // Initialize Firebase Auth
            mAuth = FirebaseAuth.getInstance();
        //uid
        Uid = mAuth.getUid();
        System.out.println(Uid);

        nameEdt = findViewById(R.id.name);
        recordBtn = findViewById(R.id.record);
        sendBtn = findViewById(R.id.ok);
        nextBtn = findViewById(R.id.next);

        nextBtn.setText("已新增身份");
        nextBtn.setBackgroundResource(R.drawable.button_white);

        init();

        recordBtn.setOnClickListener(v -> startRecord());

        sendBtn.setOnClickListener(v -> getPreUser());

        nextBtn.setOnClickListener(v -> {
            Intent intent = new Intent(StartActivity.this, MainActivity.class);
            startActivity(intent);
        });

    }

    //設定UI狀態
    private void setUiState(int state) {
        switch (state) {
            case RECORD_START:
                nameEdt.setVisibility(View.INVISIBLE);
                recordBtn.setBackgroundResource(R.drawable.ic_voice_recording_24);
                break;
            case RECORD_STOP:
                recordBtn.setBackgroundResource(R.drawable.ic_voice_24);
                sendBtn.setEnabled(true);
                break;
            case RECORD_UPLOAD:
                nextBtn.setText("開始聊天");
                nextBtn.setBackgroundResource(R.drawable.button_green);
                init();
                break;

        }
    }

    private void init() {
        nameEdt.setVisibility(View.INVISIBLE);
        sendBtn.setEnabled(false);
    }

    //確認有無重複身分
    private void checkIdentity(int identity) {

        switch (identity) {
            case IDENTITY_OK:
                uploadAudio();
                storeUserName();
                break;
            case IDENTITY_REPEAT:
                Toast.makeText(StartActivity.this,"名稱已被使用",Toast.LENGTH_SHORT).show();
                System.out.println("名稱已被使用");
                break;
        }

    }


    private void getPreUser() {
        Query checkUser = FirebaseDatabase.getInstance().getReference().child("UserName").orderByChild("name");
        checkUser.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long i = 0;
                for (DataSnapshot Snapshot:snapshot.getChildren()) {
                    String value=Snapshot.getValue().toString();
                    Log.d("showuser", value);

                    name = nameEdt.getText().toString();
                    String chname = "{name="+name+"}";
                    Log.d("myname",chname);

                    if(chname.equals(value)) {
                        checkIdentity(IDENTITY_REPEAT);
                        break;
                    }
                    i++;
                    if(i==snapshot.getChildrenCount()) {
                        checkIdentity(IDENTITY_OK);
                        break;
                    }

                }
            }


            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

    }

    //上傳錄音
    private void uploadAudio() {
        Uri file = Uri.fromFile(new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "new_test.wav"));
        StorageReference reference = FirebaseStorage.getInstance().getReference()
                .child("new_Audio")
                .child("new_audio.wav");

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

    //把使用者資料存起來
    private void storeUserName() {
        FirebaseDatabase rootNode = FirebaseDatabase.getInstance();
        DatabaseReference reference = rootNode.getReference("UserName");

        int id = (int)(Math.random()* 999 + 1);
        String userId = Integer.toString(id);

        reference.child(Uid+ userId).child("name").setValue(name);

    }

    //上傳新增資料
    private void updateData() {
        new_audio = "true";
        new_status = "true";

        FirebaseDatabase rootNode = FirebaseDatabase.getInstance();
        DatabaseReference reference = rootNode.getReference("New");

        reference.child("new_name").setValue(name);
        reference.child("new_audio").setValue(new_status);
        reference.child("new_status").setValue(new_status);

        setUiState(RECORD_UPLOAD);
    }



    //開始錄音
    private void startRecord() {
        setUiState(RECORD_START);
        countRecord();
        final int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_INHZ, CHANNEL_CONFIG, AUDIO_FORMAT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE_INHZ,
                CHANNEL_CONFIG, AUDIO_FORMAT, minBufferSize);

        final byte data[] = new byte[minBufferSize];
        final File file = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "new_test.pcm");
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
        setUiState(RECORD_STOP);
        isRecording = false;
        pcmToWav();
        //釋放資源
        if (null != audioRecord) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
        nameEdt.setVisibility(View.VISIBLE);
    }

    private void pcmToWav() {
        PcmToWavUtil pcmToWavUtil = new PcmToWavUtil(SAMPLE_RATE_INHZ, CHANNEL_CONFIG, AUDIO_FORMAT);
        File pcmFile = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "new_test.pcm");
        File wavFile = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "new_test.wav");
        if (!wavFile.mkdirs()) {
            Log.e(TAG, "wavFile Directory not created");
        }
        if (wavFile.exists()) {
            wavFile.delete();
        }
        pcmToWavUtil.pcmToWav(pcmFile.getAbsolutePath(), wavFile.getAbsolutePath());
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

        if(hasAllPermission) {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            updateUI(currentUser);
            mAuth.signInAnonymously();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
}