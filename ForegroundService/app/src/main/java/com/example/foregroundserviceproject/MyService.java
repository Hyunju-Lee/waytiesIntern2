package com.example.foregroundserviceproject;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MyService extends Service implements BeaconConsumer {
    //서비스의 상태
    int sStateService;
    //노티피케이션 관련 변수
    private NotificationManager mNotificationManager;
    private final static String TAG = MyService.class.getSimpleName();
    private final static String FOREGROUND_CHANNEL_ID = "foreground_channel_id";
    private final static String FOREGROUND_CHANNEL_NAME = "foreground_channel_name";
    //센서 관련 변수
    private SensorManager mSensorManager = null;
    private SensorEventListener mAccLis;
    private Sensor mAccelometerSensor = null;
    private Sensor mMagneticSensor = null;
    private SensorEventListener mMagneticLis;
    private SensorEventListener mStepLis;
    private Sensor mStepSensor = null;
    public int count2 = 0;
    public double angleYZ = 0;
    private int stepnumber = 0;
    private float[] mGravity;
    private float[] mGeomagnetic;
    private float mAzimut, mPitch, mRoll;
    //브로드캐스트 관련 변수
    private BroadcastReceiver dangerReceiver;
    private BroadcastReceiver trafficReceiver;
    //윈도우매니저 및 UI 관련 변수
    private WindowManager wm;
    private View mView;
    private View mView2;
    private WindowManager.LayoutParams params;
    private WindowManager.LayoutParams params2;
    private TextView textView;
    private TextView textView2;
    private LinearLayout alarmbackground;
    private int addwindow = 0;
    private int addwindow2 = 0;
    //비콘 관련 변수
    private BeaconManager beaconManager;
    private List<Beacon> beaconList = new ArrayList<>();// 감지된 비콘들을 임시로 담을 리스트
    private int nearBeaconNumber = 0;
    //스레드 카운트
    private int count = 0;
    //트래픽 스레드 관련 변수
    private int normallyTrafficChanged = 0; //0: 아직 신호를 안받음 1: 정상 2: 비정상
    //------스레드 단계 수집데이터 전역변수-----
    private ArrayList<Integer> stepAL;
    private ArrayList<Double> accAL;
    private float curArcdegree;
    private int nearBeaconNum;
    private List<Beacon> beaconAL;
    //----------수집데이터에 추가된 전역변수-----
    private double dangerValue;//위험지수
    private List<BeaconAddedData> beaconAddedDataList = new ArrayList<>();
    private int wantedValue;//가깝지수
    private Beacon nearestBeacon;
    private BeaconAddedData nearestBeaconAddedData;
    private String trafficLight; //현재 신호 상태: "초록불" or "빨간불"
    private String trafficUUID = "yyyyyyyy-yyyy-yyyy-yyyy-yyyyyyyyyyyy"; //받은 신호의 비콘 UUID
    private String curUUID = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"; //현재 비콘 UUID
    //-------------------------------------------
    //위험도 판단 기준(사용자 정의 가능)
    private final int dangerLevelValue = 22;
    private final int moreDangerValue = 6;

    public MyService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(MyService.class.getSimpleName(), "onCreate()");

        sStateService = ServiceConstants.STATE_SERVICE.NOT_INIT;
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        //브로드캐스트 위험 알림
        dangerReceiver = new DangerAlarmReceiver();
        IntentFilter dangerFilter = new IntentFilter("broadDangerAlarmReceiver");
        registerReceiver(dangerReceiver, dangerFilter);

        //브로드캐스트 신호 알림
        trafficReceiver = new TrafficAlarmReceiver();
        IntentFilter trafficFilter = new IntentFilter("broadTrafficAlarmReceiver");
        registerReceiver(trafficReceiver, trafficFilter);

        //센서 관련 변수
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        //가속도 센서
        mAccelometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mAccLis = new AccelometerListener();
        //만보기 센서
        mStepSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        mStepLis = new StepListener();
        //자기 센서(나침반)
        mMagneticSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mMagneticLis = new MagneticListener();

        //비콘 관련 변수
        beaconManager = BeaconManager.getInstanceForApplication(this);
        // 여기가 중요한데, 기기에 따라서 setBeaconLayout 안의 내용을 바꿔줘야 하는듯 싶다.
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));

        //최상단 레이아웃 설정
        LayoutInflater inflate = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        //위험 알림 레이아웃
        params = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
//                        |WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP;
        mView = inflate.inflate(R.layout.view_in_service, null);
        textView = (TextView) mView.findViewById(R.id.textView);
        alarmbackground = (LinearLayout) mView.findViewById(R.id.alarmbackground);

        //신호 알림 레이아웃 (디버깅용)
        params2 = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT);
        params2.gravity = Gravity.BOTTOM;
        mView2 = inflate.inflate(R.layout.traffic_alarm_wm, null);
        textView2 = (TextView) mView2.findViewById(R.id.textView2);
    }

    @SuppressLint("WrongConstant")
    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        //intent가 존재하지 않을 때
        if (intent == null || intent.getAction() == null) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        switch (intent.getAction()) {
            case ServiceConstants.ACTION.START_ACTION:
                mSensorManager.registerListener(mAccLis, mAccelometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
                mSensorManager.registerListener(mStepLis, mStepSensor, SensorManager.SENSOR_DELAY_NORMAL);
                mSensorManager.registerListener(mMagneticLis, mMagneticSensor, SensorManager.SENSOR_DELAY_NORMAL);
                beaconManager.bind(this);
                startForeground(ServiceConstants.NOTIFICATION_ID_FOREGROUND_SERVICE, prepareNotification());
                collectData();
                sStateService = ServiceConstants.STATE_SERVICE.PLAY;

                break;

            case ServiceConstants.ACTION.PAUSE_ACTION:
                sStateService = ServiceConstants.STATE_SERVICE.PAUSE;
                Log.i(TAG, "Clicked Pause");
                unregisterReceiver(dangerReceiver);
                unregisterReceiver(trafficReceiver);
                mSensorManager.unregisterListener(mAccLis);
                mSensorManager.unregisterListener(mStepLis);
                mSensorManager.unregisterListener(mMagneticLis);
                stopForeground(ServiceConstants.NOTIFICATION_ID_FOREGROUND_SERVICE);
                mNotificationManager.cancel(ServiceConstants.NOTIFICATION_ID_FOREGROUND_SERVICE);
                //비콘 연결 해지
                beaconManager.removeAllRangeNotifiers();
                beaconManager.unbind(this);
                if (mView.getParent() != null) {
                    wm.removeView(mView);
                }
                if (mView2.getParent() != null) {
                    wm.removeView(mView2);
                }
//                stopSelf();
                break;

            case ServiceConstants.ACTION.MAIN_ACTION:
                Toast.makeText(getApplicationContext(), "메인화면으로 이동했습니다.", Toast.LENGTH_LONG).show();
                break;

        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        sStateService = ServiceConstants.STATE_SERVICE.PAUSE;
        unregisterReceiver(dangerReceiver);
        unregisterReceiver(trafficReceiver);
        mSensorManager.unregisterListener(mAccLis);
        mSensorManager.unregisterListener(mStepLis);
        mSensorManager.unregisterListener(mMagneticLis);
        stopForeground(true);
        mNotificationManager.cancel(ServiceConstants.NOTIFICATION_ID_FOREGROUND_SERVICE);
        //비콘 연결 해지
        beaconManager.removeAllRangeNotifiers();
        beaconManager.unbind(this);
        if (mView.getParent() != null) {
            wm.removeView(mView);
        }
        if (mView2.getParent() != null) {
            wm.removeView(mView2);
        }
        stopSelf();
        SharedPreferences pref;
        pref = getSharedPreferences("check", 0);
        SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean("checkedit", false);
        editor.commit();
        super.onDestroy();
    }

    //센서와 비콘을 통해 데이터를 수집하고 위험여부를 판단하기 위한 데이터를 준비한다.
    //step은 0.9초마다 수집, 3.6초간 데이터 저장
    //기울기는 0.3초마다 수집, 1.8초간 데이터 저장한다.
    private void collectData() {
        stepAL = new ArrayList<>(); //size 4
        accAL = new ArrayList<>(); //size 6
        //초기화 - 안전한 수준으로
        stepAL.add(0);
        stepAL.add(0);
        stepAL.add(0);
        stepAL.add(0);
        accAL.add(60.0);
        accAL.add(60.0);
        accAL.add(60.0);
        accAL.add(60.0);
        accAL.add(60.0);
        accAL.add(60.0);
        //스레드를 이용하여 주기적으로 실행하도록 한다.
        Thread sthread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (sStateService == ServiceConstants.STATE_SERVICE.PLAY) {
                    try {
                        Thread.sleep(300);//0.3초마다 수집(step은 예외적으로 0.9초)
                        //기울기 수집
                        accAL.remove(0);
                        accAL.add(angleYZ);
                        Log.d(TAG, "run accAL: " + accAL);
                        //각도 수집
                        curArcdegree = mAzimut;
                        Log.d(TAG, "run curArcdegree: " + curArcdegree);
                        //비콘 정보 수집
                        nearBeaconNum = nearBeaconNumber;
                        beaconAL = beaconList;
                        Log.d(TAG, "run nearBeaconNum: " + nearBeaconNum);
                        //step 수집, 0.9초마다
                        if (count > 2) {
                            if (stepAL.size() >= 4) {
                                stepAL.remove(0);
                            }
                            stepAL.add(stepnumber);
                            stepnumber = 0;
                            Log.d(TAG, "run stepAL: " + stepAL);

                            count = 0;
                        }
                        count++;

                        //0.5초마다
                        decideDangerousValue();
                        /*위 함수로 아래 값이 결정된다. (전역변수)
                        double dangerValue;*/
                        if (nearBeaconNum > 0) {//비콘 정보 해석, 가까이 있는 비콘 결정 및 가깝지수 결정
                            decideNearestBeaconAndValue();
                            //위 함수로 아래 값들이 결정된다. (전역변수)
                            /*int wantedValue;
                            Beacon nearestBeacon;
                            BeaconAddedData nearestBeaconAddedData;
                            String trafficLight;*/
                        }
                        //위험도 알림
                        Intent dangerAlarmintent = new Intent("broadDangerAlarmReceiver");
                        sendBroadcast(dangerAlarmintent);
                        //신호 정보 알림
                        if (nearBeaconNum > 0) {
                            Intent trafficAlarmintent = new Intent("broadTrafficAlarmReceiver");
                            sendBroadcast(trafficAlarmintent);
                        }

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                stepAL.clear();
                accAL.clear();
            }
        });
        sthread.start();

        return;
    }

    //주어진 데이터를 가지고 위험여부를 판단한다. 가까운 시간대에 가중치를 둔다.
    //step은 1초에 1.2걸음 이상이면 평균적으로 위험하다고 판단한다.
    //acc은 -60~60도면 평균적으로 위험하다고 판단한다.
    private void decideDangerousValue() {
        double stepValue; // 20이상 위험, accValue의 2배의 가중치
        double accValue; // 10이상 위험
        stepValue = stepAL.get(3) + (stepAL.get(2) * 0.8) + (stepAL.get(1) * 0.5) + (stepAL.get(0) * 0.2); // 평균 (3.6*2.5) 9
        stepValue = (stepValue / 9) * 20;
        accValue = accAL.get(5) + (accAL.get(4) * 0.9) + (accAL.get(3) * 0.8) + (accAL.get(2) * 0.6)
                + (accAL.get(1) * 0.4) + (accAL.get(0) * 0.2); // 평균 (60*3.9) 234 -> 230
        accValue = Math.abs(accValue);
        accValue = accValue / 23;
        accValue = 20 - accValue;
        if (accValue < 0) {
            accValue = 0.1;
        }
        dangerValue = accValue + stepValue;

        return;
    }

    private BeaconAddedData interpretBeacon(int major, int minor) {
        BeaconAddedData tmpbeaconAddedData = new BeaconAddedData();
        int tmpNP;
        if ((major >> 15) % 2 == 1) {
            tmpbeaconAddedData.setAvailable(true);
        } else {
            tmpbeaconAddedData.setAvailable(false);
        }
        if ((major >> 14) % 2 == 1) {
            tmpbeaconAddedData.setTrafficLight(true);
        } else {
            tmpbeaconAddedData.setTrafficLight(false);
        }
        if ((major >> 13) % 2 == 1) {
            tmpNP = -1; //음수 각도
        } else {
            tmpNP = 1; // 양수 각도
        }
        tmpbeaconAddedData.setCrosswalkRotate(((major >> 8) % (1 << 5)) * tmpNP * 6);
        tmpbeaconAddedData.setRemainingTime((major >> 0) % (1 << 8));

        Log.d(TAG, "interpretBeacon: " + tmpbeaconAddedData.getCrosswalkRotate());

        return tmpbeaconAddedData;
    }

    private int detectWantedBeacon() {
        int beaconListindex = 0;
        int tmpdistance;
        int tmprotate;
        wantedValue = 999999; //낮을수록 우선순위가 좋음

        //10도에 2m의 가중치를 둔다.
        //    5m 45도 -> 500 + 900
        //    7m 35도 -> 700 + 700
        for (int i = 0; i < beaconAL.size(); i++) {
            tmpdistance = (int) (beaconAL.get(i).getDistance() * 100);//m단위 곱하기 100
            tmprotate = beaconAddedDataList.get(i).getCrosswalkRotate();//-180~180도
            //현재 각도
            tmprotate = Math.abs((int) mAzimut - tmprotate);
            tmprotate = tmprotate % 360;
            tmprotate = tmprotate * 20;
            if (tmprotate + tmpdistance < wantedValue) {
                wantedValue = tmprotate + tmpdistance;
                beaconListindex = i;
            }
        }

        return beaconListindex;
    }


    private void decideNearestBeaconAndValue() {
        beaconAddedDataList.clear();
        // 비콘의 정보를 읽어와 비콘에 데이터를 추가적으로 부여한다.
        for (Beacon beacon : beaconAL) {
            BeaconAddedData tmpbeaconAddedData = interpretBeacon(beacon.getId2().toInt(), beacon.getId3().toInt());
            beaconAddedDataList.add(tmpbeaconAddedData);
        }
        //가깝지수가 제일낮은 비콘의 인덱스를 반환하고 가깝지수 또한 전역변수로 결정된다.
        int tmpBeaconIdx = detectWantedBeacon();
        nearestBeacon = beaconAL.get(tmpBeaconIdx);
        nearestBeaconAddedData = beaconAddedDataList.get(tmpBeaconIdx);
        if (nearestBeaconAddedData.isTrafficLightOn()) {
            trafficLight = "초록불";
        } else {
            trafficLight = "빨간불";
        }
    }

    class DangerAlarmReceiver extends BroadcastReceiver {
        //두 값의 합이 dangerLevelValue(default:24)이 넘는 경우 위험으로 판단, 임의로 정한 것.
        //브로드캐스팅을 이용하여 위험알림을 화면에 띄우거나 제거한다. dangerLevelValue를 기준으로.
        @Override
        public void onReceive(Context context, Intent intent) {
            textView.setText("위험지수: ");
            textView.append(String.format("%.3f", dangerValue));
            //빨간불도 추가하자
            if (nearBeaconNum > 0) {
                textView.append("\n주변에 도로가 있습니다. 조심하세요!");
                alarmbackground.setBackgroundColor(Color.argb(196, 255, 0, 0));
                if (dangerValue > dangerLevelValue - moreDangerValue && addwindow == 0) {
                    wm.addView(mView, params);
                    addwindow = 1;
                } else if (dangerValue <= dangerLevelValue - moreDangerValue && addwindow == 1) {
                    wm.removeView(mView);
                    addwindow = 0;
                }
            } else {
                alarmbackground.setBackgroundColor(Color.argb(196, 255, 255, 0));
                if (dangerValue > dangerLevelValue && addwindow == 0) {
                    wm.addView(mView, params);
                    addwindow = 1;
                } else if (dangerValue <= dangerLevelValue && addwindow == 1) {
                    wm.removeView(mView);
                    addwindow = 0;
                }
            }
        }
    }

    class TrafficAlarmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            //-------------디버깅용 오버레이아웃----------------
            if (addwindow2 == 0) {
                wm.addView(mView2, params2);
                addwindow2 = 1;
            }
            //초기화 후 다시 채워준다.
            textView2.setText("");
            for (Beacon beacon : beaconAL) {
                textView2.append("\nMajor: " + beacon.getId2() + " / Minor: " + beacon.getId3() + " / " +
                        "Distance : " + Double.parseDouble(String.format("%.3f", beacon.getDistance())) + "m\n" + beacon.getId1());
            }
            textView2.append("\n가까운 횡단보도\n" + "Major: " + nearestBeacon.getId2() + " / Minor: " + nearestBeacon.getId3() + " / " +
                    "거리 : " + Double.parseDouble(String.format("%.3f", nearestBeacon.getDistance())) + "m\n" + nearestBeacon.getId1()
                    + " 총 개수" + nearBeaconNum + "\n");
            textView2.append("남은 시간 " + nearestBeaconAddedData.getRemainingTime() + " / 각도: " + nearestBeaconAddedData.getCrosswalkRotate()
                    + " / 현재 각도: " + mAzimut + " /\n " + trafficLight + " / 가깝지수: " + wantedValue);
            //------------------------------------------------

            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View toastDesign = inflater.inflate(R.layout.toast_design, null);
            Toast toast = new Toast(context);
            TextView text = toastDesign.findViewById(R.id.TextView_toast_design);

            //멈춰있는 것을 판단. 가깝지수가 작으며(1800이하), step이 없을 때(1이하), 횡단보도를 건너려고 한다고 판단한다.
            int stepALSum = 0;
            for (int d : stepAL)
                stepALSum += d;
            curUUID = nearestBeacon.getId1().toString();
            if (wantedValue < 1800 && stepALSum <= 1 && trafficLight.equals("빨간불") && !curUUID.equals(trafficUUID)) {
                text.setText("건너려고합니다.");
                text.append("\n" + nearestBeaconAddedData.getRemainingTime() + "초 후에 초록불이 켜집니다.");
                text.setTextColor(Color.argb(196, 127, 0, 255));
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.setDuration(Toast.LENGTH_LONG);
                toast.setView(toastDesign);
                toast.show();
                trafficUUID = curUUID;
                //set traffic timer
                Thread timethread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Log.d(TAG, "set traffic time: " + nearestBeaconAddedData.getRemainingTime() * 1000);
                            Thread.sleep(nearestBeaconAddedData.getRemainingTime() * 1000);//
                            //제대로 기다렸다고 인식한 경우
                            if (curUUID.equals(trafficUUID)) {
                                normallyTrafficChanged = 1;
                            } else {
                                normallyTrafficChanged = 2;
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                });
                timethread.start();
            }
            if(normallyTrafficChanged == 1)
            {
                text.setText("신호가 바뀌었습니다. 건너세요.");
                text.setTextColor(Color.argb(196, 0, 255, 0));
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.setDuration(Toast.LENGTH_LONG);
                toast.setView(toastDesign);
                toast.show();
                normallyTrafficChanged = 0;
            } else if(normallyTrafficChanged == 2){
                //잘못 판단했음
                normallyTrafficChanged = 0;
            }
            //기다렸다고 잘못 인식할 수도 있기 때문에 초록불이 켜질 타이밍에 UUID를 다시한번 확인한다.
//            if(sendAlready == true && curUUID.equals(trafficUUID)) {
//                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
//                View toastDesign = inflater.inflate(R.layout.toast_design, null);
//
//                TextView text = toastDesign.findViewById(R.id.TextView_toast_design);
//                text.setText("신호가 바뀌었습니다. 건너세요.");
//                text.setTextColor(Color.argb(196, 0, 255,0));
//
//                Toast toast = new Toast(context);
//                toast.setGravity(Gravity.CENTER, 0, 0);
//                toast.setDuration(Toast.LENGTH_LONG);
//                toast.setView(toastDesign);
//                toast.show();
//                sendAlready = false;
//            } else if(trafficLight.equals("초록불") && sendAlready == true && !curUUID.equals(trafficUUID)){
//                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
//                View toastDesign = inflater.inflate(R.layout.toast_design, null);
//
//                TextView text = toastDesign.findViewById(R.id.TextView_toast_design);
//                text.setText("신호가 바뀌었습니다. 건너세요.");
//                text.setTextColor(Color.argb(196, 0, 255,0));
//
//                Toast toast = new Toast(context);
//                toast.setGravity(Gravity.CENTER, 0, 0);
//                toast.setDuration(Toast.LENGTH_LONG);
//                toast.setView(toastDesign);
//                toast.show();
//                sendAlready = false;
//            }
        }
    }

    //지금은 angleYZ만 수집한다. 전역변수로 처리
    private class AccelometerListener implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent event) {

            //사실상 큰 의미없지만 5번 count당 1번 수집, 1초에 약 30~40번 수집
            if (count2 < 5) {
                count2++;
                return;
            }

//            double accX = event.values[0];
            double accY = event.values[1];
            double accZ = event.values[2];
            mGravity = event.values;

//            double angleXZ = Math.atan2(accX,  accZ) * 180/Math.PI;
            angleYZ = Math.atan2(accY, accZ) * 180 / Math.PI;
//            Log.e("LOG", "ACCELOMETER           [X]:"
//                            + String.format("%.4f", event.values[0])
//                    + "           [Y]:" + String.format("%.4f", event.values[1])
//                    + "           [Z]:" + String.format("%.4f", event.values[2])
//                    + "           [angleXZ]: " + String.format("%.4f", angleXZ)
//                    + "           [angleYZ]: " + String.format("%.4f", angleYZ));
            Log.d(TAG, "AccelometerListener: angleYZ - " + angleYZ);
            count2 = 0;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    }

    private class StepListener implements SensorEventListener {

        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            stepnumber++;
            Log.d(TAG, "SensorEventListener: stepnumberInSecond - " + stepnumber);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }
    }

    private class MagneticListener implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            mGeomagnetic = sensorEvent.values;
            if (mGravity != null && mGeomagnetic != null) {
                float R[] = new float[9];
                float I[] = new float[9];
                boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
                if (success) {
                    float orientation[] = new float[3];
                    SensorManager.getOrientation(R, orientation);
                    mAzimut = (float) Math.toDegrees(orientation[0]);
                    mPitch = (float) Math.toDegrees(orientation[1]);
                    mRoll = (float) Math.toDegrees(orientation[2]);

                    String result;
                    result = "Azimut:" + mAzimut + "\n" + "Pitch:" + mPitch + "\n" + "Roll:" + mRoll;
                    Log.d(TAG, "MagneticListener: " + result);
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }
    }

    int preventDuplication = 0;

    @Override
    public void onBeaconServiceConnect() {
        beaconManager.addRangeNotifier(new RangeNotifier() {
            @Override
            // 비콘이 감지되면 해당 함수가 호출된다. Collection<Beacon> beacons에는 감지된 비콘의 리스트가,
            // region에는 비콘들에 대응하는 Region 객체가 들어온다.
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                nearBeaconNumber = 0;//감지된 비콘 수
                if (beacons.size() > 0) {
                    beaconList.clear();
                    for (Beacon beacon : beacons) {
                        for (Beacon tmpbeacon : beaconList) {
                            if (tmpbeacon.getId1().equals(beacon.getId1())) {//중복된 UUID는 제거
                                preventDuplication = 1;
                                break;
                            }
                        }
                        if (preventDuplication == 0) {
                            beaconList.add(beacon);
                            nearBeaconNumber++;
                        }
                        preventDuplication = 0;
                    }
                    Log.d(TAG, "didRangeBeaconsInRegion: beaconadded");
                }
            }
        });

        try {
            beaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", null, null, null));
        } catch (RemoteException e) {
        }

    }

    //1. notification manager에 채널의 ID(FOREGROUND_CHANNEL_ID)등을 이용하여 등록을 하고
    //2. notification을 (등록한 채널의 ID를 사용하여) build하고 반환하여 startforeground를 할 수 있도록 한다.
    private Notification prepareNotification() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                mNotificationManager.getNotificationChannel(FOREGROUND_CHANNEL_ID) == null) {
            // The user-visible name of the channel.
            NotificationChannel mChannel = new NotificationChannel(FOREGROUND_CHANNEL_ID, FOREGROUND_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            mChannel.setSound(null, null);
            mChannel.enableVibration(true);
            mNotificationManager.createNotificationChannel(mChannel);
            //notification manager에 channel을 추가
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(ServiceConstants.ACTION.MAIN_ACTION);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);//new task and activity

        //pendingIntent로 누르면 main화면으로 가도록 설정
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        RemoteViews lRemoteViews = new RemoteViews(getPackageName(), R.layout.app_notification);

        //notification의 view등의 ui설정을 해준다. FOREGROUND_CHANNEL_ID로 notification manager와 연결시켜준다.
        NotificationCompat.Builder lNotificationBuilder;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            lNotificationBuilder = new NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID);
        } else {
            lNotificationBuilder = new NotificationCompat.Builder(this);
        }
        lNotificationBuilder
                .setContent(lRemoteViews)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOnlyAlertOnce(true)
                .setOngoing(true)//계속 띄워주는 효과로 서비스가 실행중임을 필수적으로 알려야한다.
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            lNotificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        }
        return lNotificationBuilder.build();
    }
}
