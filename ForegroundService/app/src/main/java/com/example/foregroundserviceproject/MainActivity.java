package com.example.foregroundserviceproject;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences pref;
    private Boolean checked = false;
    private static final int ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE = 1;
    private int delaying = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermission();//오버레이뷰 퍼미션
        checkPermissions(MainActivity.this, this);//블루투스, 위치 퍼미션

        Switch serviceSwitch = findViewById(R.id.switch1);

        pref = getSharedPreferences("check", 0);
        Boolean alreadyChecked = pref.getBoolean("checkedit", false);
        serviceSwitch.setChecked(alreadyChecked);
        serviceSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean ischecked) {
                if(ischecked && delaying == 0){//활성화되었을 때
                    delaying = 1;
                    Intent startIntent = new Intent(getApplicationContext(), MyService.class);
                    startIntent.setAction(ServiceConstants.ACTION.START_ACTION);
                    startService(startIntent);
                }
                else if(delaying == 0){
                    delaying = 1;
                    Intent startIntent = new Intent(getApplicationContext(), MyService.class);
                    startIntent.setAction(ServiceConstants.ACTION.PAUSE_ACTION);
                    stopService(startIntent);
                }
                delaying = 0;
                checked = ischecked;
            }
        });
    }

    @Override
    protected void onDestroy() {
        SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean("checkedit", checked);
        editor.commit();
        super.onDestroy();
    }

    public void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {   // 마시멜로우 이상일 경우
            if (!Settings.canDrawOverlays(this)) {              // 체크
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("서비스를 이용하기 위해 오버레이 뷰 권한이 필요합니다. 설정 부탁드립니다.").setMessage("선택하세요.");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int id)
                    {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE);
                        Toast.makeText(getApplicationContext(), "OK Click", Toast.LENGTH_SHORT).show();
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int id)
                    {
                        Toast.makeText(getApplicationContext(), "Cancel Click", Toast.LENGTH_SHORT).show();
                    }
                });
                AlertDialog alertDialog = builder.create();
                alertDialog.show();
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(getApplicationContext(), "앱을 이용하기 위해 꼭 필요한 설정입니다.", Toast.LENGTH_LONG);
                checkPermission();
            } else {
//                startService(new Intent(MainActivity.this, MyService.class));
            }
        }
    }

    public static void checkPermissions(Activity activity, Context context){
        int PERMISSION_ALL = 1;
        String[] PERMISSIONS = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
        };
        if(!hasPermissions(context, PERMISSIONS)){
            ActivityCompat.requestPermissions(activity, PERMISSIONS, PERMISSION_ALL);
        }
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }
}
