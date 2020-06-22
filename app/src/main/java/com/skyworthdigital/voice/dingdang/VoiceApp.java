package com.skyworthdigital.voice.dingdang;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.support.multidex.MultiDex;
import android.support.multidex.MultiDexApplication;
import android.text.TextUtils;
import android.util.Log;

import com.baidu.duersdk.DuerSDK;
import com.baidu.duersdk.DuerSDKFactory;
import com.baidu.duersdk.DuerSDKImpl;
import com.baidu.duersdk.sdkconfig.SdkConfigInterface;
import com.baidu.duersdk.utils.AppLogger;
import com.baidu.duersdk.utils.FileUtil;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.forest.bigdatasdk.ForestDataReport;
import com.forest.bigdatasdk.hosttest.IErrorListener;
import com.forest.bigdatasdk.model.ForestInitParam;
import com.forest.bigdatasdk.util.BaseParamUtils;
import com.forest.bigdatasdk.util.SystemUtil;
import com.skyworthdigital.voice.baidu_module.AppConfig;
import com.skyworthdigital.voice.baidu_module.BdAsrTranslator;
import com.skyworthdigital.voice.baidu_module.BdController;
import com.skyworthdigital.voice.baidu_module.BdGuideAgent;
import com.skyworthdigital.voice.baidu_module.BdTvLiveController;
import com.skyworthdigital.voice.common.utils.Utils;
import com.skyworthdigital.voice.dingdang.service.AppUpgradeService;
import com.skyworthdigital.voice.dingdang.service.InstallUtils;
import com.skyworthdigital.voice.dingdang.service.RecognizeService;
import com.skyworthdigital.voice.dingdang.utils.GlobalVariable;
import com.skyworthdigital.voice.dingdang.utils.MLog;
import com.skyworthdigital.voice.iot.IoTService;
import com.skyworthdigital.voice.tencent_module.TxAsrTranslator;
import com.skyworthdigital.voice.tencent_module.TxController;
import com.skyworthdigital.voice.tencent_module.TxGuideAgent;
import com.skyworthdigital.voice.tencent_module.TxTvLiveController;
import com.skyworthdigital.voice.tencent_module.record.PcmRecorder;
import com.skyworthdigital.voice.wemust.WemustApi;
import com.tencent.ai.sdk.control.SpeechManager;
import com.tencent.ai.sdk.utils.ISSErrors;

import org.json.JSONObject;

import java.io.File;

import static com.forest.bigdatasdk.app.ForestAdvertCrossAppDataReport.HTTP_PREFIX;

public class VoiceApp extends MultiDexApplication {
    private static final String TAG = VoiceApp.class.getSimpleName();
    private static com.skyworthdigital.voice.VoiceApp sInstance;
    private static VoiceApp sVoiceApp;
    private static final int SDK_ID = 10021;
    private static final String SDK_KEY = "d5d2f64047526f4064845a3e964afbf9";

    @Override
    public void onCreate() {
        super.onCreate();
        setAccessibilityEnable();
        sInstance = new com.skyworthdigital.voice.VoiceApp();
        sInstance.onCreate(this);
        sVoiceApp = this;

        Log.i("VoiceApp", "voice type:" + sInstance.mAiType);
        if (sInstance.mAiType == GlobalVariable.AI_NONE) {
            System.exit(0);
        }
        initTencentSDK();
        initDuerSDK();
        initBaiduInstances();
        initTencentInstances();

        Fresco.initialize(this);

        initBigDataReport();

        startService(new Intent(this, IoTService.class));

        //启动 升级service
        startService(new Intent(this, AppUpgradeService.class));
//        Toast.makeText(this, "当前版本:" + InstallUtils.getVersionCode(this), Toast.LENGTH_LONG).show();
        Log.d(TAG, "skyTencentVoice current version:" + InstallUtils.getVersionCode(this));
        // 初始化威玛斯特相关秘钥
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WemustApi.initKeys(getBaseContext());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
        MultiDex.install(this);
    }

    public static void initTencentInstances(){
//        com.skyworthdigital.voice.VoiceApp.isDuer = false;
//        initTencentSDK();
//        AbsAsrTranslator.clearInstance();
        TxAsrTranslator.getInstance();

//        AbsTvLiveControl.clearInstance();
        TxTvLiveController.getInstance();

//        GuideTip.getInstance().setGuideAgent(new TxGuideAgent());
        TxGuideAgent.getInstance();

//        AbsController.clear();
        TxController.getInstance();
    }
    private void initTencentSDK(){
        int ret = SpeechManager.getInstance().startUp(this, getAppInfo());
        SpeechManager.getInstance().setAsrDomain(80);
        //SpeechManager.getInstance().aisdkSetConfig(7002, "1");
        //SpeechManager.getInstance().aisdkSetConfig(6007, "1");
        SpeechManager.getInstance().aisdkSetConfig(6011,"2048");

        //SpeechManager.getInstance().setDisplayLog(true);
        Log.i("VoiceApp", "app launch");
        if (ret != ISSErrors.ISS_SUCCESS) {
            System.exit(0);
        }
        if (sInstance.mAiType == GlobalVariable.AI_REMOTE) {
            SpeechManager.getInstance().setManualMode(true);
        } else {
            //SpeechManager.getInstance().setFullMode(true);
            SpeechManager.getInstance().setManualMode(false);
        }
        String sn = Utils.get("ro.serialno");
        SpeechManager.getInstance().setAiDeviceInfo(sn, "497a7402-7660-4eb6-844f-543b2c2f6777:111d7f7d4dc6460fafce8875efbe0474", null, null, null);

        //CrashHandler.getInstance().init(this);
        if (Utils.isQ3031Recoder()) {
            PcmRecorder.copyWcompTable(this);
        }
        Log.d("VoiceApp", "guid = " + SpeechManager.getInstance().getGuidStr());
    }

    public static void initBaiduInstances(){
//        com.skyworthdigital.voice.VoiceApp.isDuer = true;
//        initDuerSDK();
//        AbsAsrTranslator.clearInstance();
        BdAsrTranslator.getInstance();

//        AbsTvLiveControl.clearInstance();
        BdTvLiveController.getInstance();

//        GuideTip.getInstance().setGuideAgent(new BdGuideAgent());
        BdGuideAgent.getInstance();

//        AbsTTS.clearInstance();
//        BdTTS.getInstance();

//        AbsController.clear();
        BdController.getInstance();
    }

    private void initDuerSDK() {
        // 设置debug开关
        AppLogger.setDEBUG(true);
        AppLogger.setDEBUG_WRITEFILE(true);
        Log.i("MyApplication", "debug:" + AppLogger.getDEBUG());
        //初始化duersdk
        DuerSDK duerSDK = DuerSDKFactory.getDuerSDK();

        //测试appid,appkey,亲见的appid,appkey
        String appid = AppConfig.getAppid(com.skyworthdigital.voice.VoiceApp.getVoiceApp().isAudioBox());//APP_DEFAULTAPPID;
        String appkey = AppConfig.getAppkey(com.skyworthdigital.voice.VoiceApp.getVoiceApp().isAudioBox());//APP_DEFAULTAPPKEY;

        //统计测试，sd卡配置文件动态设置appid,appkey
        File sdkConfigFile = new File(SdkConfigInterface.APP_CONFIGFILE);
        if (sdkConfigFile.isFile() && sdkConfigFile.exists()) {
            try {
                String content = FileUtil.getFileOutputString(SdkConfigInterface.APP_CONFIGFILE);
                JSONObject contentJson = new JSONObject(content);
                String fileAppId = contentJson.optString("appid");
                String fileAppKey = contentJson.optString("appkey");
                if (!TextUtils.isEmpty(fileAppId) && !TextUtils.isEmpty(fileAppKey)) {
                    appid = fileAppId;
                    appkey = fileAppKey;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        //初始化sdk
        duerSDK.initSDK(this, appid, appkey);
        DuerSDKImpl.getConst().setCUID(com.skyworthdigital.voice.VoiceApp.deviceId);
        Log.i("MyApplication", "sn:" + com.skyworthdigital.voice.VoiceApp.deviceId);
    }

    private String getAppInfo() {
        String result = "";
        try {
            final JSONObject info = new JSONObject();
            info.put("appkey", "497a7402-7660-4eb6-844f-543b2c2f6777");
            info.put("token", "111d7f7d4dc6460fafce8875efbe0474");
            /**
             * 如果产品是车机，填入CAR
             * 如果产品是电视，填入TV
             * 如果产品是音箱，填入SPEAKER
             * 如果产品是手机，填入PHONE
             */
            info.put("deviceName", "TV");
            info.put("productName", "ottbox");
            info.put("vendor", "skyworthdigital");

            final JSONObject json = new JSONObject();
            json.put("info", info);

            result = json.toString();
        } catch (Exception e) {
            // do nothing
        }
        Log.d("VoiceApp", "info = " + result);
        return result;
    }

    public static Context getInstance() {
        return com.skyworthdigital.voice.VoiceApp.getInstance();
    }
    public static VoiceApp getVoiceApp(){
        return sVoiceApp;
    }
    private void setAccessibilityEnable() {
//        需要权限android.permission.WRITE_SECURE_SETTINGS（貌似不需要也可以）
        //变量enabled_accessibility_services可通过查看/data/system/users/0/settings_secure.xml文件
        MLog.d(TAG, "setAccessibilityEnable");
        String enabledServicesSetting = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        MLog.d(TAG, "voice pckname:"+getPackageName()+" class name:"+RecognizeService.class.getName());
        ComponentName selfComponentName = new ComponentName(getPackageName(),
                RecognizeService.class.getName());
        String flattenToString = ":" + selfComponentName.flattenToString();
        MLog.d(TAG, "originString:"+enabledServicesSetting);
        MLog.d(TAG, "flattenString:"+flattenToString);
        if (TextUtils.isEmpty(enabledServicesSetting)) {
            enabledServicesSetting = selfComponentName.flattenToString();
        }

        if (enabledServicesSetting.startsWith(selfComponentName.flattenToString())) {
            MLog.d(TAG, "recognizeService already on");
        } else if (!enabledServicesSetting.contains(flattenToString)) {
            enabledServicesSetting += flattenToString;
        }
        MLog.d(TAG, "finalString:"+enabledServicesSetting);
        boolean ret = Settings.Secure.putString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                enabledServicesSetting);
        MLog.d(TAG, "set result:" + ret);
        Settings.Secure.putInt(getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, 1);
    }

    private void initBigDataReport() {
        ForestInitParam skyInitParam = new ForestInitParam();
        String serialNum = BaseParamUtils.getSnno();
        String channel = BaseParamUtils.getCustomerid(serialNum);
        skyInitParam.setAppId(SDK_ID);
        skyInitParam.setAppKey(SDK_KEY);
        skyInitParam.setChannel(channel);
        skyInitParam.setDeviceId(BaseParamUtils.getDeviceNo());
        skyInitParam.setDeviceTypeId(BaseParamUtils.getDeviceTypeId(serialNum));
        skyInitParam.setSkySystemVersion(String.valueOf(BaseParamUtils.getSoftVersion()));
        skyInitParam.setSn(serialNum);
        String ipServer = HTTP_PREFIX + SystemUtil.getSystemProperties("ro.stb.adv.url");
        skyInitParam.setIpurl(ipServer);
        String appupgradeServer = HTTP_PREFIX + SystemUtil.getSystemProperties("ro.stb.appupgrade");
        skyInitParam.setUpdatejarurl(appupgradeServer);
        String bigdataServer = HTTP_PREFIX + SystemUtil.getSystemProperties("ro.stb.bigdata");
        skyInitParam.setBigdataurl(bigdataServer);

        ForestDataReport.getInstance()
                .initDataSdk(this, skyInitParam);
        ForestDataReport.getInstance()
                .setNeedDebug(false);
        ForestDataReport.getInstance()
                .registerErrorListener(new IErrorListener() {
                    @Override
                    public void onErrorOccur(String s, String s1) {
                        Log.i("MyApplication", s + "[=========]" + s1);
                    }
                });
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        ForestDataReport.getInstance().onTerminate();
    }

}
