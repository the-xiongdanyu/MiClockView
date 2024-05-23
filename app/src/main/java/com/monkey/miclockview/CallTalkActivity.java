package com.azl.a100.ui.call;

import static com.azl.app_call.bean.TalkState.CALLIN;
import static com.azl.app_call.bean.TalkState.CALLOUTING;
import static com.azl.app_call.bean.TalkState.PAUSED;
import static com.azl.app_call.bean.TalkState.RINGING;
import static com.azl.app_call.bean.TalkState.TALKING;
import static com.azl.lib_datastore.connect.struct.SipMessage.Status.DEVICE_TALKING;
import static com.azl.lib_datastore.connect.struct.SipMessage.Status.DEVICE_TALK_END;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.android.codec.AudioVideoCodec;
import com.android.codec.VideoConfig;
import com.android.domain.CallInfo;
import com.android.ipconvifsdk.IPCConstant;
import com.android.ipconvifsdk.IPCSetting;
import com.android.ipconvifsdk.JniIPCOnvif;
import com.android.param.SIPConstant;
import com.android.sipintercomsdk.SIPIntercom;
import com.android.system.IpResult;
import com.android.system.SystemUtils;
import com.azl.a100.BuildConfig;
import com.azl.a100.MainActivity;
import com.azl.a100.R;
import com.azl.a100.SipService;
import com.azl.a100.WorkService;
import com.azl.a100.databinding.ActivityCallTalkBinding;
import com.azl.a100.ui.call.face.AckBean;
import com.azl.a100.ui.call.face.AckData;
import com.azl.a100.ui.call.face.FaceData;
import com.azl.a100.ui.call.face.FileH264SaveUtil;
import com.azl.a100.ui.call.face.RequestBean;
import com.azl.a100.web.util.GsonUtil;
import com.azl.a100.web.util.HttpUtil;
import com.azl.app_base.BaseActivity;
import com.azl.app_call.adapter.CallDevAdapter;
import com.azl.app_call.adapter.CallDialogAdapter;
import com.azl.app_call.bean.CallDevBean;
import com.azl.app_call.bean.CallType;
import com.azl.app_call.bean.DevState;
import com.azl.app_call.bean.TalkState;
import com.azl.app_call.view.CallDevViewModel;
import com.azl.lib_datastore.connect.CustomThreadFactory;
import com.azl.lib_datastore.connect.LocalStatePool;
import com.azl.lib_datastore.freeswitch.InboundClient;
import com.azl.lib_datastore.room.entity.Device;
import com.azl.lib_datastore.room.entity.History;
import com.azl.lib_datastore.room.repository.DeviceRepository;
import com.azl.lib_datastore.utils.DateUtil;
import com.azl.lib_datastore.utils.RxTimerUtil;
import com.azl.lib_datastore.utils.SystemUtilsHelper;
import com.azl.lib_datastore.utils.TTSUtils;
import com.dpower.VideoCore.RenderCallBackJni;
import com.dpower.VideoCore.VideoRender;
import com.dpower.commontools.NetworkTools;
import com.dpower.commontools.SoundTools;
import com.dpower.gpiologicsdk.GpioTools;
import com.elvishew.xlog.XLog;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BasePopupView;
import com.lxj.xpopup.enums.PopupAnimation;
import com.lxj.xpopup.interfaces.OnCancelListener;
import com.lxj.xpopup.interfaces.OnConfirmListener;
import com.tencent.mmkv.MMKV;

import org.dpower.GenernalGpioSet.UtilConst;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.http.HttpHeaders;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * AZL_8
 * 2022/11/7 11:18
 */
public class CallTalkActivity extends BaseActivity implements View.OnClickListener, WorkService.HeadStateCallback, CallDevAdapter.HistoryListener {

    private ActivityCallTalkBinding binding;
    public static CallTalkActivity activity;
    private DeviceRepository repository;
    private CallDevViewModel viewModel;

    private static final long CALL_TIMEOUT = 20000; // 振铃超时时间
    private static final long CALLING_TIMEOUT = 120 * 1000; // 通话超时时间
    private static final int MSG_NEW_CALL = 101;
    private static final int MSG_CALL_START = 102;
    private static final int MSG_CALL_END = 103;
    private static final int MSG_CALL_ING = 104;
    private static final int MSG_LOGIN = 105;
    private static final int MSG_LOGOUT = 106;
    private static final int MSG_LOGIN_CHANGE = 107;
    private static final int MSG_TIMEOUT = 108;
    private static final int MSG_TIME = 109;
    private static final int HEAD_UP = 110;     //手柄提起
    private static final int HEAD_DOWN = 111;   //手柄放下
    private static final int HEAD_STATE_CHANGE = 112;       //手柄状态改变
    private static final int BTN_CALL = 113;
    private static final int NOTIFY_VIEW = 11;      //修改视图
    private static final int NOTIFY_VIEW1 = 12;
    private static final int MSG_TOAST = 13;
    private static final int MSG_FUNCTION = 14;
    private static final int MSG_FACE = 15;         //人脸识别返回
    private static final int MSG_SENDFACE = 16;     //是否发送人脸识别
    private static final int MSG_SCREEN = 17;       //通话开启，当存在图片时隐藏一个imageView
    private static final int MSG_IPC_CONNECT = 201;     //连接ipc
    private static final int MSG_IPC_TIMEOUT = 202;     //连接超时
    private static final int MSG_VIDEO_TRANSFER = 300;      //分机解码视频与ipc互换
    private static final int MSG_VIDEO_TRANSFER1 = 301;     //ipc视频与分机解码视频互换
    private static final int MSG_LOOP_CALL_START = 302;
    private static final int MSG_LOOP_CALL_END = 303;       //用于循环通话测试
    private static final int MSG_DESTROY = 304;         //destr

    private boolean isTalking = false;      //是否通话状态
    private int ringTime = 0;
    private MediaPlayer mMediaPlayer = null;
    private AudioManager mAudioManager = null;
    private int currentVolume = 3; //记录当前的音量
    private int mCallID;            //当前选择的设备的呼叫id
    private int[] mVideoArea = {0, 0, 1440, 810};
    private int[] mCameraArea = {10, 10, 400, 240};

    private CallDevAdapter adapter;
    private List<CallDevBean> callDevLst;
    private int mCurrentIndex = 0;

    private String ring_file;       //响铃铃声文件
    private String alarm_file;      //报警铃声文件
    private int ring_volume;        //响铃音量
    private int chat_client_volume; //通话音量-对方音量
    private int chat_local_volume;  //通话音量-本机音量
    private int talk_time;          //通话时长
    private int chat_time;          //通话时长由分钟转秒后
    private MyHandler mHandler = null;
    private String local_id;
    Random rand;
    private boolean isBtn = false;      //是否通过一键按钮呼叫进来的
    private boolean isConference = false;       //true代表从会议转过来

    private Map<String, CallType> saveType;      //使用map保存呼叫上来的设备的呼叫类型，使用之后删除
    private int last_call = -1;
    private long last_time = 0;
    private boolean isChip = false;
    private boolean isRecord = true;
    private String fullKey1 = "";
    //private boolean isIpc = true;       //true为ipc1， false为ipc2
    //private BasePopupView popup;
    private GLSurfaceView mGlSurface;
    private GLSurfaceView mPreSurface;
    private int current_media_state;
    private CallDialogAdapter dialogAdapter;
    private int nVideoPack = 0;
    private boolean isVideoSend = true;
    private ExecutorService poolExecutor;
    private String platform_server = "";
    private int platform_port = 3866;
    private int platform_face = 3866;
    private OkHttpClient mOkHttpClient = null;

    private GLSurfaceView ipcSurface;
    private int ip_camera_id = -1;
    private int ipc_decoder_id = 0;
    private boolean mConnected;
    private boolean mUseOnvif;
    private IPCSetting mIPCSetting;
    private VideoConfig mVideoConfig;
    private boolean isFace = false;
    private boolean isScreen = false;
    private WorkService mService;
    private boolean isBound;
    //private FileH264SaveUtil fileH264SaveUtil;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            XLog.w("-----------------call--onService connected");
            WorkService.MyBind binder = (WorkService.MyBind) service;
            mService = binder.getService();
            mService.setHeadStateCallback(CallTalkActivity.this);
            if (callDevLst != null && mCurrentIndex != -1 && callDevLst.size() > mCurrentIndex) {
                XLog.w("----bean: " + callDevLst.get(mCurrentIndex).toString());
                if (callDevLst.get(mCurrentIndex).getType().getCall_type() == 1 || callDevLst.get(mCurrentIndex).getType().getCall_type() == 2) {

                } else {
                    XLog.w("---------------connect send head state");
                    if (mService.getHandle() == 1) {      //手柄提起
                        mHandler.sendEmptyMessage(HEAD_UP);
                    } else {
                        mHandler.sendEmptyMessage(HEAD_DOWN);
                    }
                }

            }
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            XLog.w("------------------call service null");
            mService.setHeadStateCallback(null);
            mService = null;
            isBound = false;
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        XLog.i("call talk activity onCreate---------");
        hideSystemUI();
        binding = ActivityCallTalkBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        callDevLst = new ArrayList<>();     //需要在回调之前new
        mHandler = new MyHandler();         //同上
        saveType = new HashMap<>();
        repository = new DeviceRepository(getApplication());
        viewModel = new ViewModelProvider(this).get(CallDevViewModel.class);
        viewModel.setHistoryListener(this);
        SipService.getIns(this).setCallback(sipCallback);
        //SIPIntercom.getInstance().registerCallback(mSipCallback);
        XLog.w("-------service : " + (mService == null) + ", activity: " + (this == null));
        //mService.setHeadStateCallback(this);
        poolExecutor = Executors.newFixedThreadPool(3, new CustomThreadFactory("CallTalkSingle"));
        platform_server = MMKV.defaultMMKV().decodeString("platform_server", "");
        platform_face = MMKV.defaultMMKV().decodeInt("platform_port_face", 3866);
        isFace = MMKV.defaultMMKV().decodeBool("dev_face", false);
        if (isFace) {
            mOkHttpClient = new OkHttpClient
                    .Builder()
                    .connectTimeout(800, TimeUnit.MILLISECONDS)
                    .readTimeout(1500, TimeUnit.MILLISECONDS)
                    .writeTimeout(800, TimeUnit.MILLISECONDS)
                    .connectionPool(new ConnectionPool(32, 30, TimeUnit.SECONDS))
                    .build();
        }
        Intent serviceIntent = new Intent(this, WorkService.class);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        initConfig();
        initUI();
        initCamera();
        initNetworkCamera();
        initParam();
        init();

        initTransform();
        activity = this;
        //fileH264SaveUtil = new FileH264SaveUtil();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);       //屏幕不进入屏保
    }

    @Override
    protected void onStop() {
        super.onStop();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();
        XLog.w("call talk onResume");
            //SIPIntercom.getInstance().startPreview(mCameraArea[0], mCameraArea[1], mCameraArea[2], mCameraArea[3]);
    }

    @Override
    protected void onPause() {
        super.onPause();
        XLog.w("call talk onPause");
            SIPIntercom.getInstance().stopPreview();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        XLog.i("call talk activity onDestroy----");
        //long time = System.currentTimeMillis();
        /*if (MMKV.defaultMMKV().decodeBool("dev_tts", false) && callDevLst.get(mCurrentIndex).getType().getCall_type() != 0) {
            TTSUtils.getIns().ttsStop();
        }*/
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }

        if(callDevLst != null) {
            callDevLst.clear();
        }
        if(saveType != null){
            saveType.clear();
        }
        if (adapter != null) {
            adapter.clearData();
            adapter.cancelAllTimers();
            adapter.setTimeoutListener(null);
            adapter = null;
        }
        if (isBound) {
            XLog.w("----------clear service");
            if(mService != null){
                mService.setHeadStateCallback(null);
            }
            unbindService(serviceConnection);
            isBound = false;
        }

        SIPIntercom.getInstance().setTxVolume(0);
        SipService.getIns(this).setCallback(null);
        //SIPIntercom.getInstance().unregisterCallback(mSipCallback);

        GpioTools.getIns().writeListSingleGpio(UtilConst.GPIO_L7, 0);           //全部挂断时再设置一次
        GpioTools.getIns().writeListSingleGpio(UtilConst.GPIO_G12, 0);
        if (ip_camera_id != -1) {
            JniIPCOnvif.getIns().jniStopMonitor(ip_camera_id);
            ip_camera_id = -1;
        }
        if (ipc_decoder_id > 0) {
            AudioVideoCodec.getIns().stopVideoDecoder(ipc_decoder_id);
            ipc_decoder_id = 0;
        }
        JniIPCOnvif.getIns().setCallback(null);
        if (mGlSurface != null) {
            XLog.i("call mGlSurface----");
            VideoRender.Detach(this, mGlSurface);
            mGlSurface = null;
        }
        if (mPreSurface != null) {
            XLog.i("call mPreSurface----");
            VideoRender.Detach(this,  mPreSurface);
            mPreSurface = null;
        }
        if (ipcSurface != null) {
            XLog.i("call ipcSurface----");
            VideoRender.Detach(this,  ipcSurface);
            ipcSurface = null;
        }
        // 清空线程池
        if (poolExecutor != null) {
            poolExecutor.shutdownNow();
            poolExecutor = null;
        }
        if (mAudioManager != null) {
            mAudioManager.setSpeakerphoneOn(false);
            mAudioManager = null;
        }
        dialogAdapter = null;
        viewModel.setHistoryListener(null);
        if(mOkHttpClient !=null){
            mOkHttpClient = null;
        }
        if(mHandler != null){
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }
        mIPCSetting = null;
        mVideoConfig = null;
        repository = null;
        activity = null;
        XLog.i("call talk activity onDestroy-----++++");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        XLog.i("新的信息");     //在打开当前页面时有新的呼叫进来，但当前页面还没有准备好
        Bundle bundle = intent.getBundleExtra("bundle");
        CallDevBean bean = bundle.getParcelable("dev");
        if (bean != null) {
            if (callDevLst.size() > 0) {
                for (CallDevBean dev : callDevLst) {
                    if (dev.getCall_id() == bean.getCall_id())
                        return;
                }
            }
            isBtn = bundle.getBoolean("isBtn", false);
            XLog.w("--------on newIntent----call talk bean: " + bean.toString());
            bean.setState(RINGING);
            bean.setRing_time(ringTime);

            callDevLst.add(bean);
            viewModel.add(bean);

            if (mCurrentIndex >= 0 && mCurrentIndex < callDevLst.size()) {
                //有新的报警呼入
                if (bean.getType().getCall_type() == 2) {        //若新呼入为报警，则判断当前正在通话的呼叫类型
                    CallDevBean tempBean = callDevLst.get(mCurrentIndex);
                    if (tempBean.getType().getCall_type() == 0) {
                        //本机呼出
                        XLog.w("onNewIntent TalkingState = " + tempBean.getState());
                        if (tempBean.getState() == TalkState.TALKING || tempBean.getState() == TalkState.PAUSED) {
                            //暂停
                            toNextSession(tempBean.getCall_id(), false);
                        } else {
                            //挂断
                            /*if (MMKV.defaultMMKV().decodeBool("dev_tts", false) && callDevLst.get(mCurrentIndex).getType().getCall_type() != 0) {
                                TTSUtils.getIns().ttsStop();
                            }*/
                            if (mMediaPlayer != null) {
                                mMediaPlayer.stop();
                                mMediaPlayer.release();
                                mMediaPlayer = null;
                            }
                            //hangUp(tempBean.getCall_id());
                            SIPIntercom.getInstance().hangup(tempBean.getCall_id());
                            toNextSession(tempBean.getCall_id(), true);
                        }
                    } else {
                        //呼入
                        if (tempBean.getType().getCall_type() == 1) {
                            XLog.w("onNewIntent TalkingState = " + tempBean.getState());
                            if (tempBean.getState() == TalkState.TALKING || tempBean.getState() == TalkState.PAUSED) {
                                //暂停
                                toNextSession(tempBean.getCall_id(), false);
                            } else {
                                //挂断
                                /*if (MMKV.defaultMMKV().decodeBool("dev_tts", false) && callDevLst.get(mCurrentIndex).getType().getCall_type() != 0) {
            TTSUtils.getIns().ttsStop();
        }*/
                                if (mMediaPlayer != null) {
                                    mMediaPlayer.stop();
                                    mMediaPlayer.release();
                                    mMediaPlayer = null;
                                }
                                //hangUp(tempBean.getCall_id());
                                SIPIntercom.getInstance().hangup(tempBean.getCall_id());
                                toNextSession(tempBean.getCall_id(), true);
                            }
                        }
                    }
                }
            }
            mHandler.sendEmptyMessage(MSG_NEW_CALL);
        }
    }

    //初始化按钮事件
    private void initUI() {
        binding.accept.setOnClickListener(this);
        //binding.accept.setEnabled(true);
        if(!BuildConfig.CORPORATION_NAME.equals("dahua")) {
            binding.keep.setOnClickListener(this);
        }
        binding.hangup.setOnClickListener(this);
        binding.transform.setOnClickListener(this);
        binding.hangup1.setOnClickListener(this);
        binding.back1.setOnClickListener(this);
        //binding.ipcToggle.setOnClickListener(this);
        binding.function.setOnClickListener(this);
        binding.transform.setVisibility(View.INVISIBLE);
        binding.cancel.setOnClickListener(this);
        binding.transformTo.setOnClickListener(this);
        binding.door1.setOnClickListener(this);
        binding.door2.setOnClickListener(this);
        binding.perspnClose.setOnClickListener(this);
        binding.overlayLayout.setOnClickListener(this);
        binding.chip.setOnClickListener(this);

        //保留按键需要等通话开始后才能按
        btnStateChange(false, false);

        binding.ringVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                ring_volume = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                MMKV.defaultMMKV().encode("ring_volume", ring_volume);
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, ring_volume, 0);
            }
        });
        if (ring_volume > 15) ring_volume = 15;
        binding.ringVolume.setProgress(ring_volume);

        binding.clientVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                XLog.i("client volume: " + progress);
                chat_client_volume = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                MMKV.defaultMMKV().encode("chat_client_volume", chat_client_volume);
                setClientVol();
            }
        });
        binding.clientVolume.setProgress(chat_client_volume);
        binding.localVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                XLog.i("local volume: " + progress);
                chat_local_volume = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                MMKV.defaultMMKV().encode("chat_local_volume", chat_local_volume);
                SIPIntercom.getInstance().setRxVolume(chat_local_volume);
            }
        });
        binding.localVolume.setProgress(chat_local_volume);

        int hours = chat_time / 3600;
        int minutes = (chat_time % 3600) / 60;
        int seconds = chat_time % 60;
        String hoursStr = (hours > 9 ? "" : "0") + hours;
        String minutesStr = (minutes > 9 ? "" : "0") + minutes;
        String secondsStr = (seconds > 9 ? "" : "0") + seconds;
        binding.time.setText(hoursStr + ":" + minutesStr + ":" + secondsStr);
        /*util = new RxTimerUtil();
        //计时器，每隔一秒会进入一次doNext
        util.interval(1000, new RxTimerUtil.IRxNext() {
            @Override
            public void doNext(long number) {
                if (callDevLst != null && callDevLst.size() > 0 && mCurrentIndex >= 0 && callDevLst.get(mCurrentIndex).getState() == TALKING) {
                    chat_time++;
                    *//*if (chat_time == 0) {
                        XLog.w("---------------通话时间为零，挂断通话");
                        phoneHangup();
                        return;
                    }*//*
                    int hour = chat_time / 60;
                    int min = chat_time % 60;
                    //XLog.w("text: " + "" + hour + ":" + min);
                    Message msg = new Message();
                    msg.what = MSG_TIME;
                    msg.obj = ("" + hour + ":" + min);
                    mHandler.sendMessage(msg);
                }
            }
        });*/
    }

    //初始化配置
    private void initConfig() {
        ring_file = MMKV.defaultMMKV().decodeString("call_ring", "ring_City.mp3");
        alarm_file = MMKV.defaultMMKV().decodeString("call_alarm", "ring_AlarmS.mp3");
        ringTime = MMKV.defaultMMKV().decodeInt("ring_time", 10);
        ring_volume = MMKV.defaultMMKV().decodeInt("ring_volume", 3);
        chat_client_volume = MMKV.defaultMMKV().decodeInt("chat_client_volume", 3);
        chat_local_volume = MMKV.defaultMMKV().decodeInt("chat_local_volume", 3);
        talk_time = MMKV.defaultMMKV().decodeInt("chat_time", 10);
        boolean def = BuildConfig.CORPORATION_NAME.equals("aozhili");
        isRecord = MMKV.defaultMMKV().decodeBool("is_record", def);
        chat_time = 0;
        rand = new Random();
        local_id = MMKV.defaultMMKV().decodeString("dev_id", "M001001");
    }

    //初始化呼叫数据
    private void initParam() {
        adapter = new CallDevAdapter(this);
        adapter.setHasStableIds(false);
        binding.callDevList.setLayoutManager(new LinearLayoutManager(this));
        binding.callDevList.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        binding.callDevList.setAdapter(adapter);
        adapter.setClickListener(new CallDevAdapter.ClickListener() {
            @Override
            public void onClick(int position) {
                if (mCurrentIndex == position) return;
                toPauseTalking(mCurrentIndex, position);      //如果有多台设备在通话列表，而现在点击了其他设备，需要先将当前设备进行中的通话暂停(这会导致重启，重启原因为：原视频未加载完成时进行暂停导致)
                stopMonitorIpc();
                mCurrentIndex = position;
                adapter.setPosition(position);
                adapter.notifyDataSetChanged();
                binding.screenImage.setVisibility(View.VISIBLE);

                CallDevBean tempBean = callDevLst.get(mCurrentIndex);
                boolean isHost = tempBean.getId().indexOf("M") == 0;
                mCallID = tempBean.getCall_id();
                XLog.w("------pause---temp: " + tempBean.toString());
                XLog.w("------is talking: : " + isTalking);
                if (tempBean.getType().getCall_type() == 3) {
                    binding.layoutBtn.setVisibility(View.GONE);
                    binding.layoutBtn1.setVisibility(View.VISIBLE);
                } else {
                    binding.layoutBtn.setVisibility(View.VISIBLE);
                    binding.layoutBtn1.setVisibility(View.GONE);
                }
                if (tempBean.getState() == RINGING || tempBean.getState() == CALLIN || tempBean.getState() == CALLOUTING) {
                    callIn();
                    btnStateChange(false, isHost);
                    if (!isTalking) {
                        binding.accept.setText(R.string.call_btn_accept);
                        binding.accept.setClickable(true);
                        binding.accept.setBackgroundResource(R.drawable.border_line_blue);
                    } else {
                        binding.accept.setText(R.string.call_btn_accept);
                        binding.accept.setClickable(false);
                        binding.accept.setBackgroundResource(R.drawable.border_line_canal);
                    }
                    binding.keep.setText("保留");
                } else if (tempBean.getState() == TALKING || tempBean.getState() == PAUSED) {
                    /*if (MMKV.defaultMMKV().decodeBool("dev_tts", false) && callDevLst.get(mCurrentIndex).getType().getCall_type() != 0) {
                        TTSUtils.getIns().ttsStop();
                    }*/
                    if (mMediaPlayer != null) {
                        mMediaPlayer.stop();
                        mMediaPlayer.release();
                        mMediaPlayer = null;
                    }
                    binding.accept.setClickable(false);
                    if (tempBean.getState() == TALKING) {
                        binding.accept.setText("通话中");
                        binding.accept.setBackgroundResource(R.drawable.border_line_calling);
                        binding.keep.setText("保留");
                        btnStateChange(true, isHost);
                    } else if (tempBean.getState() == PAUSED) {
                        binding.accept.setText("通话暂停");
                        binding.accept.setBackgroundResource(R.drawable.border_line_blue);
                        binding.keep.setText("唤醒");
                        btnStateChange(!isTalking, isHost);
                    }
                }

                if (tempBean.getId().indexOf("E") == 0) {
                    //isIpc = true;
                    startMonitorIpc(tempBean.getUrl1());
                }

                if (tempBean.getType().getCall_type() == 3) {
                    //setChipInCall(true);
                    //phoneKeepTalking();
                } else {
                    //setChipInCall(false);
                    setClientVol();
                    binding.clientVolume.setProgress(chat_client_volume);
                }

            }
        });
        adapter.setTimeoutListener(new CallDevAdapter.TimeOutListener() {
            @Override
            public void onTimeOut(CallDevBean dev) {       //未接听自动挂断需要使用忙时未接听
                XLog.w("响铃超时------------" + dev.toString());
                timeHangup(dev);
            }
        });
        adapter.setPosition(0);
        viewModel.getCallBean().observe(this, new Observer<List<CallDevBean>>() {
            @Override
            public void onChanged(List<CallDevBean> callDevBeans) {
                adapter.setList(callDevBeans);
                adapter.notifyDataSetChanged();
            }
        });

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.setSpeakerphoneOn(true);

        binding.videoPlayer1.setOnClickListener(click -> {
            mHandler.sendEmptyMessage(MSG_VIDEO_TRANSFER1);

        });

        if(!BuildConfig.CORPORATION_NAME.equals("dahua")) {
            binding.decoderLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    isScreen = !isScreen;
                    if (callDevLst.size() > 0
                            && callDevLst.size() > mCurrentIndex
                            && callDevLst.get(mCurrentIndex).getId().indexOf("M") == 0) return;
                    if (binding.ipcImage.isShown() && !binding.videoPlayer1.isShown()) {
                        binding.ipcImage.setVisibility(View.GONE);
                    } else if (!binding.ipcImage.isShown() && !binding.videoPlayer1.isShown()) {
                        binding.ipcImage.setVisibility(View.VISIBLE);
                    }
                    mHandler.sendEmptyMessage(MSG_VIDEO_TRANSFER);
                }
            });
        }
    }

    private void setClientVol() {
        if (!BuildConfig.CORPORATION_NAME.equals("dahua")) {
            int i = rand.nextInt(1000) + 1;
            String message = "<Notify>" + "<CmdType>SetVolume</CmdType>" + "<SN>" + i + "</SN>" + "<DeviceID>" + local_id + "</DeviceID>" + "<Volume>" + chat_client_volume / 6.6 + "</Volume>" + "</Notify>";
            //if (isTalking)
            if (SIPIntercom.getInstance().isOnline()) {
                if (callDevLst != null && callDevLst.size() > 0)
                    SIPIntercom.getInstance().sendMessage(callDevLst.get(mCurrentIndex).getId(), message, false);
            } else {
                if (callDevLst != null && callDevLst.size() > 0) {
                    if (callDevLst.get(mCurrentIndex).getIpaddr() != null && !"".equals(callDevLst.get(mCurrentIndex).getIpaddr()))
                        SIPIntercom.getInstance().sendMessage(callDevLst.get(mCurrentIndex).getIpaddr(), message, true);
                }
            }
            message = null;
        }
    }

    private void init() {
        Intent intent = getIntent();
        Bundle bundle = intent.getBundleExtra("bundle");
        isConference = bundle.getBoolean("isConference", false);
        CallDevBean dev = null;
        if (isConference) {
            ArrayList devLst = bundle.getParcelableArrayList("devLst");
            XLog.i("-----------list size: " + devLst.size());
            if (devLst != null && devLst.size() > 0) {
                List<CallDevBean> DevBeans = (List<CallDevBean>) devLst.get(0);
                if (callDevLst != null) {
                    for (CallDevBean bean : DevBeans) {
                        if (!bean.getId().equals(local_id)) {
                            callDevLst.add(bean);
                            viewModel.add(bean);
                        }
                    }
                    if (callDevLst.size() > 0) dev = callDevLst.get(0);
                }
            }
        } else {
            dev = bundle.getParcelable("dev");
            callDevLst.add(dev);
            viewModel.add(dev);
        }
        isBtn = bundle.getBoolean("isBtn", false);

        XLog.w("------------call talk bean: " + dev.toString());
        if (dev.getId().indexOf("E") != 0) {
            binding.videoPlayer1.setVisibility(View.GONE);
            binding.ipcImage.setVisibility(View.GONE);
            binding.ipcToggle.setVisibility(View.GONE);
        } else {
            startMonitorIpc(dev.getUrl1());
        }
        GpioTools.getIns().writeListSingleGpio(UtilConst.GPIO_L7, 1);       //默认打开mic
        if (dev.getType().getCall_type() == 1 || dev.getType().getCall_type() == 2) {     //如果为呼入

        } else {
            /*if (mService.getHandle() == 1) {      //手柄提起
                mHandler.sendEmptyMessage(HEAD_UP);
            } else {
                mHandler.sendEmptyMessage(HEAD_DOWN);
            }*/
        }

        if (dev.getType().getCall_type() == 0) {
            callIn();
            callOut(dev, false);
            binding.layoutBtn.setVisibility(View.VISIBLE);
            binding.layoutBtn1.setVisibility(View.GONE);
        } else if (dev.getType().getCall_type() == 3) {
            callOut(dev, true);
            binding.layoutVolume1.setVisibility(View.GONE);
            binding.layoutBtn.setVisibility(View.GONE);
            binding.layoutBtn1.setVisibility(View.VISIBLE);
        } else if (dev.getType().getCall_type() == 1 || dev.getType().getCall_type() == 2) {
            callIn();
            binding.layoutBtn.setVisibility(View.VISIBLE);
            binding.layoutBtn1.setVisibility(View.GONE);
        } else if (dev.getType().getCall_type() == 5) {
            callIn();
            callOut(dev, false);
            binding.layoutBtn.setVisibility(View.VISIBLE);
            binding.layoutBtn1.setVisibility(View.GONE);
        }
        /*List<CallInfo> list = SIPIntercom.getInstance().getCallList();
        if(list != null && list.size() != callDevLst.size()){
            List<CallDevBean> toAddList = new ArrayList<>();
            for (CallInfo info : list) {
                boolean callIDExists = false;
                for (CallDevBean bean : callDevLst) {
                    if (info.callID == bean.getCall_id()) {
                        callIDExists = true;
                        break;
                    }
                }
                if (!callIDExists) {
                    // callID不存在于callDevLst中，将info.callID对应的CallDevBean对象添加到toAddList中
                    XLog.w("-------------info: " + info.toString());
                    CallDevBean newBean = new CallDevBean();
                    newBean.setCall_id(info.callID);
                    newBean.setId(info.remoteAccount);
                    toAddList.add(newBean);
                }
            }
            callDevLst.addAll(toAddList);
        }*/
    }

    private void initCamera() {
        mGlSurface = binding.glDecoderView;
        VideoRender.Attach(this,  mGlSurface, "MainVideoSurface", new RenderCallBackJni());
        mGlSurface.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mGlSurface.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                int[] location = new int[2];
                mGlSurface.getLocationOnScreen(location);
                mVideoArea = new int[]{0, 0, mGlSurface.getWidth(), mGlSurface.getHeight()};
                SIPIntercom.getInstance().setDisplayArea(1, 1, mVideoArea[2], mVideoArea[3]);
                XLog.i("解码视频显示区域 " + mVideoArea[0] + " " + mVideoArea[1] + " " + mVideoArea[2] + " " + mVideoArea[3]);
            }
        });

        mPreSurface = binding.glPreview;
        VideoRender.Attach(this,  mPreSurface, "PreviewVideoSurface", new RenderCallBackJni());
        mPreSurface.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mPreSurface.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                int[] location = new int[2];
                mPreSurface.getLocationOnScreen(location);
                mCameraArea = new int[]{location[0], location[1], mPreSurface.getWidth(), mPreSurface.getHeight()};
                XLog.i("本地视频显示区域 " + mCameraArea[0] + " " + mCameraArea[1] + " " + mCameraArea[2] + " " + mCameraArea[3]);
                SIPIntercom.getInstance().startPreview(mCameraArea[0], mCameraArea[1], mCameraArea[2], mCameraArea[3]);
            }
        });

    }

    @Override
    public void headState(boolean state) {
        //手柄， true为提起， false为放下
        XLog.i("手柄状态改变， 当前为" + (state ? "提起" : "放下"));
        headsetStateChange(state);
    }

    @Override
    public void onMessage(History history, int type) {
        String text = "有未接听呼叫";
        if (type == 2) {
            text = "有未接听报警";
        }
        String str = history.getRemoteName() + text;
        MainActivity.activity.addMessage(str);
    }

    private class MyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_TIME:
                    XLog.d("time text:" + msg.obj);
                    binding.time.setText((String) msg.obj);
                    chat_time++;
                    int hours = chat_time / 3600;
                    int minutes = (chat_time % 3600) / 60;
                    int seconds = chat_time % 60;
                    String hoursStr = (hours > 9 ? "" : "0") + hours;
                    String minutesStr = (minutes > 9 ? "" : "0") + minutes;
                    String secondsStr = (seconds > 9 ? "" : "0") + seconds;

                    Message sendmsg = new Message();
                    sendmsg.what = MSG_TIME;
                    sendmsg.obj = (hoursStr + ":" + minutesStr + ":" + secondsStr);
                    mHandler.sendMessageDelayed(sendmsg, 1000);
                    break;
                case MSG_NEW_CALL:
                    //callIn();
                    break;
                case MSG_CALL_START:
                    mHandler.sendEmptyMessage(MSG_TIME);
                    if(callDevLst.size() > mCurrentIndex && mCurrentIndex != -1){}else {
                        return;
                    }
                    calling();
                    CallDevBean temp = callDevLst.get(mCurrentIndex);
                    String str = temp.getName() + "有未接听呼叫";
                    MainActivity.activity.removeMessage(str);

                    updateDevStatus(temp);
                    binding.ringLayout.setVisibility(View.GONE);
                    binding.chatVolume.setVisibility(View.VISIBLE);
                    binding.transform.setVisibility(View.VISIBLE);
                    boolean isServer = SIPIntercom.getInstance().isOnline();
                    if (mService != null && mService.getHandle() == 1) {
                        binding.accept.setText(R.string.call_btn_talking1);
                        binding.accept.setBackground(getDrawable(com.azl.app_call.R.drawable.border_line_blue));
                        SIPIntercom.getInstance().setTxVolume(100);
                        String smsg = "<Notify><CmdType>SetAecStatus</CmdType><SN>0</SN><DeviceID>" + local_id +
                                "</DeviceID><Status>0</Status></Notify>";
                        if (!BuildConfig.CORPORATION_NAME.equals("dahua")) {
                            if (isServer)
                                SIPIntercom.getInstance().sendMessage(temp.getId(), smsg, false);
                            else {
                                if (temp.getIpaddr() != null && !"".equals(temp.getIpaddr()))
                                    SIPIntercom.getInstance().sendMessage(temp.getIpaddr(), smsg, true);
                            }
                        }
                        smsg = null;
                        SIPIntercom.getInstance().setAec(false);
                    } else {
                        binding.accept.setText(com.azl.app_call.R.string.call_btn_talking);
                        binding.accept.setBackground(getDrawable(com.azl.app_call.R.drawable.border_line_calling));
                        SIPIntercom.getInstance().setTxVolume(100);
                        String smsg = "<Notify><CmdType>SetAecStatus</CmdType><SN>1</SN><DeviceID>" + local_id +
                                "</DeviceID><Status>0</Status></Notify>";
                        if (!BuildConfig.CORPORATION_NAME.equals("dahua")) {
                            if (isServer) {
                                SIPIntercom.getInstance().sendMessage(temp.getId(), smsg, false);
                            } else {
                                if (temp.getIpaddr() != null && !"".equals(temp.getIpaddr()))
                                    SIPIntercom.getInstance().sendMessage(temp.getIpaddr(), smsg, true);
                            }
                        }
                        smsg = null;
                        SIPIntercom.getInstance().setAec(true);
                    }

                    //if (temp.getId().indexOf("M") != 0) {
                        //sendStartTalk(temp);
                    //} else {
                        //if (temp.getType().getCall_type() == 2 || temp.getType().getCall_type() == 1)
                            //sendStartTalk(temp);
                    //}
                    phoneAccept(temp.getCall_id());
                    if (temp.getId().indexOf("E") != 0 || temp.getType().getCall_type() == 3) {
                        binding.layoutVolume1.setVisibility(View.GONE);
                    } else {
                        setClientVol();
                        binding.layoutVolume1.setVisibility(View.VISIBLE);
                    }

                    mHandler.sendEmptyMessageDelayed(MSG_CALL_ING, 800);
                    List<CallInfo> list = SIPIntercom.getInstance().getCallList();
                    for (CallInfo info : list) {
                        if (info.callID == temp.getCall_id()) {
                            temp.setTsName(info.getTs_file_name());
                            XLog.w("-----------activity-----ts file name: " + info.getTs_file_name());
                        }
                    }
                    break;
                case MSG_CALL_END:
                    //hangUp(msg.arg1);
                    //XLog.w("-------------talk end");
                    phoneHanguped(msg.arg1);
                    //DeviceStateThread.getInstance(getApplication()).SipSendStatusUdp(DEVICE_TALK_END.getValue(), 0, 0, 0, 0);
                    LocalStatePool.getInstance().SipSendStatusUdp(DEVICE_TALK_END.getValue(), 0, 0, 0, 0);
                    break;
                case MSG_CALL_ING:
                    if (callDevLst.size() > 0 && callDevLst.size() > mCurrentIndex && callDevLst.get(mCurrentIndex).getId().indexOf("M") == 0) {
                        btnStateChange(true, true);
                    } else {
                        btnStateChange(true, false);
                    }
                    break;
                case MSG_LOGIN:

                    break;
                case MSG_LOGOUT:
                    if (SIPIntercom.getInstance().isOnline()) {
                        SIPIntercom.getInstance().logout();
                        hangUp();
                    }
                    break;
                case MSG_LOGIN_CHANGE:

                    break;
                case MSG_TIMEOUT:
                    XLog.w("--sip Time Out");
                    phoneHangup();
                    break;
                case HEAD_UP:
                    XLog.w("call 话柄提起");
                    mService.headsetUsr(true);

                    if (callDevLst.size() > 0 ){
                        if( callDevLst.get(mCurrentIndex).getType().getCall_type() != 3) {
                            SIPIntercom.getInstance().setTxVolume(100);
                        }
                        String smsg1 = "<Notify><CmdType>SetAecStatus</CmdType><SN>0</SN><DeviceID>" + local_id +
                                "</DeviceID><Status>1</Status></Notify>";
                        if (!BuildConfig.CORPORATION_NAME.equals("dahua")) {
                            if (SIPIntercom.getInstance().isOnline())
                                SIPIntercom.getInstance().sendMessage(callDevLst.get(mCurrentIndex).getId(), smsg1, false);
                            else {
                                if (callDevLst.get(mCurrentIndex).getIpaddr() != null && !"".equals(callDevLst.get(mCurrentIndex).getIpaddr()))
                                    SIPIntercom.getInstance().sendMessage(callDevLst.get(mCurrentIndex).getIpaddr(), smsg1, true);
                            }
                        }
                        smsg1 = null;
                    }

                    binding.accept.setText(R.string.call_btn_talking1);
                    binding.accept.setClickable(true);
                    binding.accept.setBackgroundResource(R.drawable.border_line_blue);
                    GpioTools.getIns().writeListSingleGpio(UtilConst.GPIO_L7, 0);
                    break;
                case HEAD_DOWN:
                    XLog.w("call 话柄放下 ,chip: " + isChip);
                    mService.headsetUsr(false);
                    String smsg2 = "<Notify><CmdType>SetAecStatus</CmdType><SN>0</SN><DeviceID>" + local_id +
                            "</DeviceID><Status>0</Status></Notify>";
                    if (mCurrentIndex != -1 && callDevLst.size() > mCurrentIndex) {
                        if (!BuildConfig.CORPORATION_NAME.equals("dahua")) {
                            if (SIPIntercom.getInstance().isOnline()) {
                                SIPIntercom.getInstance().sendMessage((String) callDevLst.get(mCurrentIndex).getId(), smsg2, false);
                            } else {
                                if (callDevLst.get(mCurrentIndex).getIpaddr() != null && !"".equals(callDevLst.get(mCurrentIndex).getIpaddr()))
                                    SIPIntercom.getInstance().sendMessage((String) callDevLst.get(mCurrentIndex).getIpaddr(), smsg2, true);
                            }
                        }
                    }
                    smsg2 = null;
                    if (callDevLst.size() > mCurrentIndex && mCurrentIndex != -1 && callDevLst.get(mCurrentIndex).getType().getCall_type() != 3) {
                        SIPIntercom.getInstance().setTxVolume(100);
                    } else {
                            SIPIntercom.getInstance().setTxVolume(0);
                    }
                    if (binding.accept.getText().toString().equals(getString(R.string.call_btn_talking1)) && callDevLst.size() > mCurrentIndex && mCurrentIndex != -1 && callDevLst.get(mCurrentIndex).getType().getCall_type() != 3) {
                        phoneHangup();
                    }
                    binding.accept.setText(R.string.call_btn_talking);
                    binding.accept.setClickable(false);
                    binding.accept.setBackgroundResource(R.drawable.border_line_calling);
                    GpioTools.getIns().writeListSingleGpio(UtilConst.GPIO_L7, 1);
                    break;
                case HEAD_STATE_CHANGE:
                    if (binding.accept.getText().toString().equals(R.string.call_btn_talking)) {
                        mService.headsetUsr(true);
                        binding.accept.setText(R.string.call_btn_talking1);
                        binding.accept.setClickable(false);
                        binding.accept.setBackgroundResource(R.drawable.border_line_blue);
                    } else {
                        mService.headsetUsr(false);
                        binding.accept.setText(R.string.call_btn_talking);
                        binding.accept.setClickable(false);
                        binding.accept.setBackgroundResource(R.drawable.border_line_calling);
                    }
                    break;
                case BTN_CALL:
                    if (mService == null) {
                        mHandler.sendMessageDelayed(msg, 300);
                    } else {
                        mService.setBtn_call_id(msg.arg1);
                    }
                    break;
                case NOTIFY_VIEW:
                    /*if (callDevLst.size() > 0 && mCurrentIndex >= 0 && mCurrentIndex < callDevLst.size() && callDevLst.get(mCurrentIndex).getId().indexOf("E") == 0) {
                        isIpc = true;
                        ipcPlay(callDevLst.get(mCurrentIndex).getUrl1());
                    }*/
                    adapter.setList(callDevLst);
                    adapter.notifyDataSetChanged();
                    break;
                case NOTIFY_VIEW1:
                    int pos = msg.arg1;
                    adapter.setPosition(pos);
                    XLog.w("------NOTIFY_VIEW1 pos= " + pos + ",callDevLst size: " + callDevLst.size());
                    if (callDevLst.size() > pos && callDevLst.get(pos) != null) {
                        if (callDevLst.get(pos).getId().indexOf("E") != 0) {
                            binding.videoPlayer1.setVisibility(View.GONE);
                            binding.ipcImage.setVisibility(View.GONE);
                            binding.ipcToggle.setVisibility(View.GONE);
                        }
                        XLog.w("---dev info: " + callDevLst.get(pos).toString());
                        btnStateChange(true, callDevLst.get(pos).getId().indexOf("M") == 0);
                        if (callDevLst.get(pos).getState() == PAUSED) {
                            binding.accept.setText("通话暂停");
                            binding.accept.setBackgroundResource(R.drawable.border_line_blue);
                            binding.keep.setText("唤醒");
                        } else if (callDevLst.get(pos).getState() == TALKING) {
                            binding.accept.setText("通话中");
                            binding.accept.setBackgroundResource(R.drawable.border_line_calling);
                            binding.keep.setText("保留");
                        } else if (callDevLst.get(pos).getState() == CALLIN || callDevLst.get(pos).getState() == RINGING) {
                            //callIn();
                            binding.accept.setClickable(true);
                            binding.accept.setText("接听");
                            binding.accept.setBackgroundResource(R.drawable.border_line_blue);
                            binding.keep.setText("保留");
                        }
                    }
                    adapter.notifyDataSetChanged();
                    break;
                case MSG_TOAST:
                    Toast.makeText(activity, (String) msg.obj, Toast.LENGTH_SHORT).show();
                    break;
                case MSG_FUNCTION:
                    binding.functionView.setVisibility(View.GONE);
                    break;
                case MSG_FACE:
                    String json = (String) msg.obj;
                    XLog.w("json: " + json);
                    AckBean bean = GsonUtil.GsonToBean(json, AckBean.class);
                    if(bean!= null) {
                        if (bean.status == 0) {
                            AckData data = bean.data.get(0);
                            binding.personCard.setVisibility(View.VISIBLE);
                            byte[] valueDecoded = new byte[0];
                            try {
                                valueDecoded = Base64.decode(data.base64_jpg.getBytes("UTF-8"), Base64.DEFAULT);
                                XLog.i("valueDecoded.length:" + valueDecoded.length);
                            } catch (UnsupportedEncodingException e) {
                            }
                            Bitmap bitmap = BitmapFactory.decodeByteArray(valueDecoded, 0, valueDecoded.length, null);
                            //saveBitmap(bitmap, 1);
                            binding.personFace.setImageBitmap(bitmap);
                            binding.personId.setText("人员编号： " + data.person_id);
                            binding.personName.setText("人员姓名： " + data.person_name);
                            binding.personRoon.setText("人员位置： " + data.room_name);

                            //XLog.w("-------video send curr time: " + System.currentTimeMillis());
                        }
                    }
                    break;
                case MSG_SENDFACE:
                    isVideoSend = !isVideoSend;
                    break;
                case MSG_SCREEN:
                    binding.screenImage.setVisibility(View.GONE);
                    break;
                case MSG_IPC_CONNECT:
                    // 使用url监视
                    mUseOnvif = false;
                    mIPCSetting.bUseOnvif = false;
                    ipc_decoder_id = AudioVideoCodec.getIns().startVideoDecoder(mVideoConfig);
                    XLog.w("startVideoDecoder " + ipc_decoder_id);
                    ip_camera_id = JniIPCOnvif.getIns().jniStartMonitor(mIPCSetting);
                    XLog.w("打开网络摄像头 ip_camera_id " + ip_camera_id);
                    if (ip_camera_id == -1) {
                        if (ipc_decoder_id > 0) {
                            AudioVideoCodec.getIns().stopVideoDecoder(ipc_decoder_id);
                            ipc_decoder_id = 0;
                        }
                        XLog.e("监视设备失败！");
                    } else {
                        XLog.i("正在连接设备！");
                        mHandler.sendEmptyMessageDelayed(MSG_IPC_TIMEOUT, 5 * 1000);
                    }
                    break;
                case MSG_IPC_TIMEOUT:
                    stopMonitorIpc();
                    XLog.w("无法连接该设备");
                    break;
                case MSG_VIDEO_TRANSFER:
                    ViewGroup.LayoutParams lp1 = binding.videoPlayer1.getLayoutParams();
                    ViewGroup.LayoutParams lp2 = binding.decoderLayout.getLayoutParams();
                    binding.videoPlayer1.setLayoutParams(lp2);
                    binding.decoderLayout.setLayoutParams(lp1);
                    break;
                case MSG_VIDEO_TRANSFER1:
                    ViewGroup.LayoutParams lp3 = binding.videoPlayer1.getLayoutParams();
                    ViewGroup.LayoutParams lp4 = binding.decoderLayout.getLayoutParams();
                    binding.decoderLayout.setLayoutParams(lp3);
                    binding.videoPlayer1.setLayoutParams(lp4);
                    break;
                case MSG_LOOP_CALL_START:
                    callOut(callDevLst.get(0), false);
                    break;
                case MSG_LOOP_CALL_END:
                    XLog.w("测试连续呼叫 ，挂断");
                    hangUp();
                    break;
                case MSG_DESTROY:
                    finish();
                    break;

            }
            super.handleMessage(msg);
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        //XLog.w("-------单击");
        if (id == R.id.accept) {
            if (callDevLst.get(mCurrentIndex).getState() != TALKING) {
                accept();
                binding.ringLayout.setVisibility(View.GONE);
                binding.chatVolume.setVisibility(View.VISIBLE);
                binding.accept.setText(R.string.call_btn_talking);
            } else {
                binding.accept.setText(R.string.call_btn_talking2);
            }
            binding.accept.setClickable(false);
            binding.accept.setBackground(getDrawable(R.drawable.border_line_calling));
        } else if (id == R.id.hangup || id == R.id.back1) {
            XLog.w("-------单击挂断，挂断通话------currentIndex=" + mCurrentIndex);
            /*if(fileH264SaveUtil != null)
                fileH264SaveUtil.closeFile();*/
            phoneHangup();
        } else if (id == R.id.keep) {
            keepTalking();
        } else if (id == R.id.transform) {
            //transformHost();
            transform();
        } else if (id == R.id.hangup1) {       //插话强制挂断
            //phoneHangup();
            if (!BuildConfig.CORPORATION_NAME.equals("dahua")) {
                String message = "<Notify>" + "<CmdType>NotifyHangupReason</CmdType>" + "<SN>" + rand.nextInt(1000) + 1 + "</SN>" + "<DeviceID>" + local_id + "</DeviceID>" + "<HangupReason>HR_FORCEHUNGUP</HangupReason>" + "</Notify>";
                if (SIPIntercom.getInstance().isOnline())
                    SIPIntercom.getInstance().sendMessage(callDevLst.get(mCurrentIndex).getId(), message, false);
                else {
                    if (callDevLst.get(mCurrentIndex).getIpaddr() != null && !"".equals(callDevLst.get(mCurrentIndex).getIpaddr()))
                        SIPIntercom.getInstance().sendMessage(callDevLst.get(mCurrentIndex).getIpaddr(), message, true);
                }
                message = null;
            }
        } else if (id == R.id.ipc_toggle) {
            //XLog.w("ipc switch: " + true + ", id: " + callDevLst.get(mCurrentIndex).getName() + ", url1: " + callDevLst.get(mCurrentIndex).getUrl1() + ", url2: " + callDevLst.get(mCurrentIndex).getUrl2());
            /*if (isIpc) {
                isIpc = false;
                //ipcPlay(callDevLst.get(mCurrentIndex).getUrl2());
                startMonitorIpc(callDevLst.get(mCurrentIndex).getUrl2());
            } else {
                isIpc = true;
                //ipcPlay(callDevLst.get(mCurrentIndex).getUrl1());
                startMonitorIpc(callDevLst.get(mCurrentIndex).getUrl1());
            }*/
        } else if (id == R.id.function) {
            popupFunction();
            mHandler.removeMessages(MSG_FUNCTION);
            mHandler.sendEmptyMessageDelayed(MSG_FUNCTION, 5000);
            //popup.show();
        } else if (id == R.id.cancel) {
            binding.transformView.setVisibility(View.GONE);
        } else if (id == R.id.transform_to) {
            Device dev = dialogAdapter.getSelectItem();
            if (dev != null) {
                XLog.i("通话转移，" + dev.toString());
                String message = "<Notify>" + "<CmdType>NotifyCallTransfer</CmdType>"
                        + "<SN>" + rand.nextInt(1000) + 1 + "</SN>"
                        + "<DeviceID>" + local_id + "</DeviceID>"
                        + "</Notify>";
                if (!BuildConfig.CORPORATION_NAME.equals("dahua")) {
                    if (SIPIntercom.getInstance().isOnline()) {
                        SIPIntercom.getInstance().sendMessage(callDevLst.get(mCurrentIndex).getId(), message, false);
                    } else {
                        if (callDevLst.get(mCurrentIndex).getIpaddr() != null && !"".equals(callDevLst.get(mCurrentIndex).getIpaddr()))
                            SIPIntercom.getInstance().sendMessage(callDevLst.get(mCurrentIndex).getIpaddr(), message, true);
                    }
                }
                SIPIntercom.getInstance().transferCall(callDevLst.get(mCurrentIndex).getCall_id(), dev.getId());
                //hangUp(callDevLst.get(mCurrentIndex).getCall_id());
                //SIPIntercom.getInstance().hangup(callDevLst.get(mCurrentIndex).getCall_id());
                toNextSession(callDevLst.get(mCurrentIndex).getCall_id(), true);
            } else {
                Toast.makeText(this, "请选择需要转移通话的主机！", Toast.LENGTH_SHORT).show();
                return;
            }
            binding.transformView.setVisibility(View.GONE);
        } else if (id == R.id.door1) {
            binding.functionView.setVisibility(View.GONE);
            unlock(1);
        } else if (id == R.id.door2) {
            binding.functionView.setVisibility(View.GONE);
            unlock(2);
        } else if (id == R.id.perspn_close) {
            binding.personCard.setVisibility(View.GONE);
        }else if(id == R.id.overlay_layout){

        }else if(id == R.id.chip){
            if(!isChip){
                isChip = true;
                binding.chip.setText("结束插话");
                binding.chip.setBackgroundResource(R.drawable.border_line_calling);
                SIPIntercom.getInstance().setTxVolume(100);
            }else {
                isChip = false;
                binding.chip.setText("插话");
                binding.chip.setBackgroundResource(R.drawable.border_line_blue);
                SIPIntercom.getInstance().setTxVolume(0);
            }
        }
    }

    //sip回调 包括注册回调，这里主要使用呼叫回调，以及音视频回调
    //需要注意其他地方如果注册了回调，也要进行判断
    BasePopupView popupview;

    private SipService.MonitorSipCallback sipCallback = new SipService.MonitorSipCallback() {
        @Override
        public void onCallState(int callID, int callState, int reason, String url) {
            switch (callState) {
                case SIPConstant.CALLSTATE_CALLING:
                    XLog.i("CALLSTATE_CALLING");
                    phoneRinged(callID);
                    break;
                case SIPConstant.CALLSTATE_INCOMING:
                    XLog.i("CALLSTATE_INCOMING");
                    break;
                case SIPConstant.CALLSTATE_EARLY:
                    XLog.i("CALLSTATE_EARLY");
                    break;
                case SIPConstant.CALLSTATE_CONNECTING:
                    XLog.i("CALLSTATE_CONNECTING");
                    break;
                case SIPConstant.CALLSTATE_CONFIRMED:
                    XLog.i("通话开始 url = " + url);
                    String remoteAccount = url;
                    XLog.i("remoteAccount = " + remoteAccount);
                    //在calling中设置不起效，DPAudio返回SetAudioPlayVolume Failed, Unavaliable Device!
                    //SIPIntercom.getInstance().setTxVolume(100);     //设置收音音量
                    boolean flag = SIPIntercom.getInstance().setRxVolume(chat_local_volume);
                    XLog.w("----------------set play volume: " + flag + ", size: " + chat_local_volume);
                    //DeviceStateThread.getInstance(getApplication()).SipSendStatusUdp(DEVICE_TALKING.getValue(), 0, 0, 0, 0);
                    LocalStatePool.getInstance().SipSendStatusUdp(DEVICE_TALKING.getValue(), 0, 0, 0, 0);
                    mHandler.sendEmptyMessage(MSG_CALL_START);
                    break;
                case SIPConstant.CALLSTATE_DISCONNECTED:
                    switch (reason) {
                        case SIPConstant.REASON_TEMPORARILY_UNAVAILABLE: // 暂时不可用（对方不在线）
                            XLog.i("REASON_TEMPORARILY_UNAVAILABLE 对方不在线");
                            if (mService != null)
                                mService.reasonPopView("对方不在线");
                            //Toast.makeText(activity, "对方不在线",Toast.LENGTH_SHORT).show();
                            //hangUp(callID);
                            break;
                        case SIPConstant.REASON_NORMAL: // 正常挂断（通话过程中挂断）
                            XLog.i("REASON_NORMAL 通话过程中挂断");
                            //hangUp(callID);
                            break;
                        case SIPConstant.REASON_BUSY: // 对方忙（对方正在通话中）
                            XLog.i("REASON_BUSY 对方正在通话中");
                            //hangUp(callID);
                            break;
                        case SIPConstant.REASON_REQUEST_TERMINATED: // 终止请求（来电中挂断、本地挂断）
                            XLog.i("REASON_REQUEST_TERMINATED 来电中挂断、本地挂断");
                            //hangUp(callID);
                            break;
                        case SIPConstant.REASON_REJECT: // 拒绝接听（呼叫中挂断）
                            XLog.i("REASON_REJECT 呼叫中挂断");
                            //hangUp(callID);
                            break;
                        default:
                            break;
                    }

                    Message msg = new Message();
                    msg.what = MSG_CALL_END;
                    msg.arg1 = callID;
                    mHandler.sendMessage(msg);
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onIncoming(int accID, int callID, String url) {
            XLog.i("onIncoming 新来电 time:" + System.currentTimeMillis());
            XLog.i("onIncoming accID = " + accID + " callID = " + callID + " url = " + url);
            if (callDevLst.size() > 0) {
                for (CallDevBean bean : callDevLst) {
                    if (bean.getCall_id() == callID)
                        return;
                }
            }
            if (url.equals("0000000000")) {
                isConference = true;
                List<CallDevBean> conferenceLst = new ArrayList<>();
                popupview = new XPopup.Builder(activity).hasNavigationBar(false) //设置是否显示导航栏，默认是显示的。如果你希望弹窗隐藏导航栏，就设置为true
                        .isViewMode(true).isDestroyOnDismiss(true).maxWidth(650).hasBlurBg(true)//是否设置背景为高斯模糊背景。默认为false
                        .dismissOnTouchOutside(false)//设置点击弹窗外面是否关闭弹窗，默认为true
                        .autoDismiss(false)//设置当操作完毕后是否自动关闭弹窗，默认为true。比如：点击Confirm弹窗的确认按钮默认是关闭弹窗，如果为false，则不关闭
                        .popupAnimation(PopupAnimation.NoAnimation)// 为弹窗设置内置的动画器，默认情况下，已经为每种弹窗设置了效果最佳的动画器；如果你不喜欢，仍然可以修改。
                        .asConfirm("会议邀请", "是否挂断会话，前往会议？", "拒绝会议", "参加会议", new OnConfirmListener() {
                            @Override
                            public void onConfirm() {
                                popupview.dismiss();
                                isConference = false;
                                activity.finish();
                                XLog.w("----------------参加会议");
                                if (callDevLst != null) {
                                    for (CallDevBean callDevBean : callDevLst) {
                                        XLog.w("----------------jangup: " + callDevBean.getId() + ", callid: " + callDevBean.getCall_id());
                                        if (callDevBean.getState() == RINGING) {
                                            SIPIntercom.getInstance().hangupForBusy(callDevBean.getCall_id());
                                        } else
                                            SIPIntercom.getInstance().hangup(callDevBean.getCall_id());
                                    }
                                }
                                Intent intent = new Intent();
                                intent.setClass(MainActivity.activity, ConferenceActivity.class);
                                Bundle bundle = new Bundle();
                                String url_id = MMKV.defaultMMKV().getString("conference_id", "default");
                                bundle.putString("conference", url_id);
                                bundle.putInt("call_id", callID);
                                bundle.putBoolean("callout", false);
                                if (MMKV.defaultMMKV().decodeBool("dev_tts", false)) {
                                    SoundTools.getIns().setMusicSound(6);
                                    TTSUtils.getIns().ttsSpeak("有会议邀请");
                                }
                                ArrayList list = new ArrayList();//这个arraylist是可以直接在bundle里传的，所以我们可以借用一下它的功能
                                if (conferenceLst != null && conferenceLst.size() > 0) {
                                    list.add(conferenceLst);//这个list2才是你真正想要传过去的list。我们把它放在arraylis中，借助它传过去
                                }
                                bundle.putParcelableArrayList("devLst", list);
                                intent.putExtra("bundle", bundle);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                                conferenceLst.clear();
                            }

                        }, new OnCancelListener() {
                            @Override
                            public void onCancel() {
                                popupview.dismiss();
                                XLog.w("----------------拒绝会议");
                                SIPIntercom.getInstance().hangup(callID);
                            }
                        }, false);
                popupview.show();
                return;
            }
            Device dev= null;
            if(url.contains("<sip:")){
                String ip = url.substring(5, url.length() - 1);
                dev = repository.getDevByIp(ip);
            }else {
                dev = repository.getDevById(url);
            }
            CallDevBean bean = new CallDevBean();
            List<CallInfo> list = SIPIntercom.getInstance().getCallList();
            if (list != null) {
                for (CallInfo info : list) {
                    if (info.callID == callID) {
                        boolean isRec = MMKV.defaultMMKV().decodeBool("is_upload", false);
                        if (isRec) {
                            info.setLocalName(MMKV.defaultMMKV().decodeString("dev_name", "01主控机"));
                            if (dev != null && dev.getName() != null && !"".equals(dev.getName()))
                                info.setRemoteName(dev.getName());
                            else info.setRemoteName("未知设备");
                            info.upload_ip1 = MMKV.defaultMMKV().decodeString("upload_ip1", "");
                            info.upload_ip2 = MMKV.defaultMMKV().decodeString("upload_ip2", "");
                            info.upload_ip3 = MMKV.defaultMMKV().decodeString("upload_ip3", "");
                            info.isRec = true;
                        } else {
                            info.isRec = false;
                        }
                        if(!isRecord)
                            info.type = 2;
                        SIPIntercom.getInstance().updateCallInfo(info);
                    }
                }
            }
            bean.setCall_id(callID);
            if (dev != null) {
                bean.setId(dev.getId());
                bean.setIpaddr(dev.getIpaddr());
                bean.setName(dev.getName());
                if (dev.getCamera() != null) {
                    bean.setUrl1(dev.getCamera().getPath1());
                    bean.setUrl2(dev.getCamera().getPath2());
                }

            } else {
                bean.setId(url);
                bean.setName("未知设备");
            }
            CallType type = new CallType(1);
            if (saveType.containsKey(url)) {
                type = saveType.get(url);
                saveType.remove(url);
            }

            bean.setType(type);
            bean.setState(RINGING);
            bean.setRing_time(ringTime);

            callDevLst.add(bean);
            viewModel.add(bean);

            if (mCurrentIndex >= 0 && mCurrentIndex < callDevLst.size()) {
                //有新的报警呼入
                if (type.getCall_type() == 2) {        //若新呼入为报警，则判断当前正在通话的呼叫类型
                    CallDevBean tempBean = callDevLst.get(mCurrentIndex);
                    if (tempBean.getType().getCall_type() == 0) {
                        //本机呼出
                        XLog.w("onNewIntent TalkingState = " + tempBean.getState());
                        if (tempBean.getState() == TalkState.TALKING || tempBean.getState() == TalkState.PAUSED) {
                            //暂停
                            toNextSession(tempBean.getCall_id(), false);
                        } else {
                            //挂断
                            /*if (MMKV.defaultMMKV().decodeBool("dev_tts", false) && callDevLst.get(mCurrentIndex).getType().getCall_type() != 0) {
                                TTSUtils.getIns().ttsStop();
                            }*/
                            if (mMediaPlayer != null) {
                                mMediaPlayer.stop();
                                mMediaPlayer.release();
                                mMediaPlayer = null;
                            }
                            //hangUp(tempBean.getCall_id());
                            SIPIntercom.getInstance().hangup(tempBean.getCall_id());
                            toNextSession(tempBean.getCall_id(), true);
                        }
                    } else {
                        //呼入
                        if (tempBean.getType().getCall_type() == 1) {
                            XLog.w("onNewIntent TalkingState = " + tempBean.getState());
                            if (tempBean.getState() == TalkState.TALKING || tempBean.getState() == TalkState.PAUSED) {
                                //暂停
                                toNextSession(tempBean.getCall_id(), false);
                            } else {
                                //挂断
                                if (mMediaPlayer != null) {
                                    mMediaPlayer.stop();
                                    mMediaPlayer.release();
                                    mMediaPlayer = null;
                                }
                                //hangUp(tempBean.getCall_id());
                                SIPIntercom.getInstance().hangup(tempBean.getCall_id());
                                toNextSession(tempBean.getCall_id(), true);
                            }
                        }
                    }
                }
            }
            mHandler.sendEmptyMessage(MSG_NEW_CALL);
        }

        @Override
        public void onPager(String from, String body) {
            XLog.i("onPager from = " + from + " body = " + body);
            if (body.contains("<CallReason>CR_NORMAL</CallReason>")) {
                saveType.put(from, new CallType(1));
            } else if (body.contains("<CallReason>CR_ALARM</CallReason>")) {
                if (body.contains("<CallReason>CR_ALARM</CallReason>")
                        || body.contains("<CallReason>CR_PRESS_ALARM</CallReason>")
                        || body.contains("<CallReason>CR_VOICE_ALARM</CallReason>")
                        || body.contains("<CallReason>CR_PHONE_ALARM</CallReason>")
                        || body.contains("<CallReason>CR_TAMPER_ALARM</CallReason>")
                        || body.contains("<CallReason>CR_TOW_DOOR_OPEN_ALARM</CallReason>")
                        || body.contains("<CallReason>CR_BDOOR_FORCEOPEN</CallReason>")
                        || body.contains("<CallReason>CR_KW_KEYWORD</CallReason>")) {       //关键词报警
                    XmlPullParserFactory factory = null;
                    XmlPullParser xpp = null;
                    try {
                        String text = "";
                        String tag = "";
                        factory = XmlPullParserFactory.newInstance();
                        factory.setNamespaceAware(true);
                        xpp = factory.newPullParser();
                        xpp.setInput(new StringReader(body));
                        int eventType = xpp.getEventType();
                        Map<String, String> xmlMpa = new HashMap<>();
                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_DOCUMENT) {
                                //XLog.d("Start document");
                            } else if (eventType == XmlPullParser.START_TAG) {
                                //XLog.d("Start tag " + xpp.getName() + ", text = " + text + "|||");
                            } else if (eventType == XmlPullParser.END_TAG) {
                                //XLog.d("End tag " + xpp.getName() + ", text= " + text + "|||");
                                tag = xpp.getName();
                                xmlMpa.put(tag, text);
                            } else if (eventType == XmlPullParser.TEXT) {
                                //XLog.d("Text " + xpp.getText() + ", tag: " + tag);
                                text = xpp.getText();
                            }
                            eventType = xpp.next();
                        }
                        String keyText = xmlMpa.get("KeywordText");
                        if (keyText != null && !"".equals(keyText)) {
                            CallType ct = new CallType(2, keyText);
                            String voice_url = xmlMpa.get("KeywordAudioFile");
                            ct.setVoice_url(voice_url);
                            saveType.put(from, ct);
                        }
                    } catch (XmlPullParserException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }finally {
                        factory = null;
                        xpp = null;
                    }

                } else {
                    saveType.put(from, new CallType(2));
                }
            }
            if (body.contains("<CmdType>NotifyCallPause</CmdType>")) {
                if (body.contains("<CallState>1</CallState>")) {          //收到暂停通话消息

                } else if (body.contains("<CallState>0</CallState>")) {        //收到恢复通话消息

                }
            }
        }

        @Override
        public void callbackVideoDecData(byte[] data, int len, int type) {
            if(len > 0){
                mHandler.sendEmptyMessage(MSG_SCREEN);
                /*if (fileH264SaveUtil.getFos() == null) {
                    fileH264SaveUtil.openFileForWrite(filePath);
                }
                fileH264SaveUtil.saveVideoStreamData(data);*/
            }
            if (isFace && data != null && data.length > 4 && !"".equals(platform_server) && !BuildConfig.CORPORATION_NAME.equals("dahua")) {
                String iframe = String.format("%02X", data[4] & 0xFF);
                if (iframe.equals("67")) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - oldTime > 2000) {
                        oldTime = currentTime;
                        poolExecutor.execute(new Runnable() {
                            @Override
                            public void run() {
                                nVideoPack++;
                                if (nVideoPack > 1 && isVideoSend) {
                                    XLog.w("--------------------video send time: " + System.currentTimeMillis());
                                    isVideoSend = false;
                                    mHandler.sendEmptyMessageDelayed(MSG_SENDFACE, 1000);

                                    RequestBean req = new RequestBean();
                                    req.time = DateUtil.longTime2String(System.currentTimeMillis());
                                    req.type = "intercom_face_info";
                                    List<FaceData> faceDatas = new ArrayList<>();
                                    FaceData faceData = new FaceData();
                                    faceData.image_type = "H264";
                                    faceData.image = Base64.encodeToString(data, Base64.NO_WRAP);
                                    faceDatas.add(faceData);
                                    req.data = faceDatas;
                                    //XLog.w("----req json: " + GsonUtil.BeanToJson(req));
                                    RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), GsonUtil.BeanToJson(req));
                                    String path = "http://" + platform_server + ":" + platform_face + "/api/intercom_face_info";
                                    XLog.w("path: " + path);
                                    //if (callDevLst != null && mCurrentIndex >= 0 && mCurrentIndex < callDevLst.size())
                                    //XLog.w("call id: " + callDevLst.get(mCurrentIndex).getCall_id() + ", call state: " + callDevLst.get(mCurrentIndex).getState());
                                    Request request = new Request.Builder().url(path).post(body).build();

                                    executeNetworkRequest(request);
                                }

                            }
                        });
                    }
                }
            }
        }
    };
    /*private SipCallback mSipCallback = new SipCallback() {
        @Override
        public void onCallState(int callID, int callState, int reason, String url) {
            switch (callState) {
                case SIPConstant.CALLSTATE_CALLING:
                    XLog.i("CALLSTATE_CALLING");
                    phoneRinged(callID);
                    break;
                case SIPConstant.CALLSTATE_INCOMING:
                    XLog.i("CALLSTATE_INCOMING");
                    break;
                case SIPConstant.CALLSTATE_EARLY:
                    XLog.i("CALLSTATE_EARLY");
                    break;
                case SIPConstant.CALLSTATE_CONNECTING:
                    XLog.i("CALLSTATE_CONNECTING");
                    break;
                case SIPConstant.CALLSTATE_CONFIRMED:
                    XLog.i("通话开始 url = " + url);
                    String remoteAccount = url;
                    XLog.i("remoteAccount = " + remoteAccount);
                    //在calling中设置不起效，DPAudio返回SetAudioPlayVolume Failed, Unavaliable Device!
                    //SIPIntercom.getInstance().setTxVolume(100);     //设置收音音量
                    boolean flag = SIPIntercom.getInstance().setRxVolume(chat_local_volume);
                    XLog.w("----------------set play volume: " + flag + ", size: " + chat_local_volume);
                    //DeviceStateThread.getInstance(getApplication()).SipSendStatusUdp(DEVICE_TALKING.getValue(), 0, 0, 0, 0);
                    LocalStatePool.getInstance().SipSendStatusUdp(DEVICE_TALKING.getValue(), 0, 0, 0, 0);
                    mHandler.sendEmptyMessage(MSG_CALL_START);
                    break;
                case SIPConstant.CALLSTATE_DISCONNECTED:
                    switch (reason) {
                        case SIPConstant.REASON_TEMPORARILY_UNAVAILABLE: // 暂时不可用（对方不在线）
                            XLog.i("REASON_TEMPORARILY_UNAVAILABLE 对方不在线");
                            if (mService != null)
                                mService.reasonPopView("对方不在线");
                            //Toast.makeText(activity, "对方不在线",Toast.LENGTH_SHORT).show();
                            //hangUp(callID);
                            break;
                        case SIPConstant.REASON_NORMAL: // 正常挂断（通话过程中挂断）
                            XLog.i("REASON_NORMAL 通话过程中挂断");
                            //hangUp(callID);
                            break;
                        case SIPConstant.REASON_BUSY: // 对方忙（对方正在通话中）
                            XLog.i("REASON_BUSY 对方正在通话中");
                            //hangUp(callID);
                            break;
                        case SIPConstant.REASON_REQUEST_TERMINATED: // 终止请求（来电中挂断、本地挂断）
                            XLog.i("REASON_REQUEST_TERMINATED 来电中挂断、本地挂断");
                            //hangUp(callID);
                            break;
                        case SIPConstant.REASON_REJECT: // 拒绝接听（呼叫中挂断）
                            XLog.i("REASON_REJECT 呼叫中挂断");
                            //hangUp(callID);
                            break;
                        default:
                            break;
                    }

                    Message msg = new Message();
                    msg.what = MSG_CALL_END;
                    msg.arg1 = callID;
                    mHandler.sendMessage(msg);
                    break;
                default:
                    break;
            }
        }

        private List<CallDevBean> conferenceLst = new ArrayList<>();

        @Override
        public void onIncoming(int accID, int callID, String url) {
            XLog.i("onIncoming 新来电 time:" + System.currentTimeMillis());
            XLog.i("onIncoming accID = " + accID + " callID = " + callID + " url = " + url);
            if (callDevLst.size() > 0) {
                for (CallDevBean bean : callDevLst) {
                    if (bean.getCall_id() == callID)
                        return;
                }
            }
            if (url.equals("0000000000")) {
                isConference = true;
                popupview = new XPopup.Builder(activity).hasNavigationBar(false) //设置是否显示导航栏，默认是显示的。如果你希望弹窗隐藏导航栏，就设置为true
                        .isViewMode(true).isDestroyOnDismiss(true).maxWidth(650).hasBlurBg(true)//是否设置背景为高斯模糊背景。默认为false
                        .dismissOnTouchOutside(false)//设置点击弹窗外面是否关闭弹窗，默认为true
                        .autoDismiss(false)//设置当操作完毕后是否自动关闭弹窗，默认为true。比如：点击Confirm弹窗的确认按钮默认是关闭弹窗，如果为false，则不关闭
                        .popupAnimation(PopupAnimation.NoAnimation)// 为弹窗设置内置的动画器，默认情况下，已经为每种弹窗设置了效果最佳的动画器；如果你不喜欢，仍然可以修改。
                        .asConfirm("会议邀请", "是否挂断会话，前往会议？", "拒绝会议", "参加会议", new OnConfirmListener() {
                            @Override
                            public void onConfirm() {
                                popupview.dismiss();
                                isConference = false;
                                activity.finish();
                                XLog.w("----------------参加会议");
                                if (callDevLst != null) {
                                    for (CallDevBean callDevBean : callDevLst) {
                                        XLog.w("----------------jangup: " + callDevBean.getId() + ", callid: " + callDevBean.getCall_id());
                                        if (callDevBean.getState() == RINGING) {
                                            SIPIntercom.getInstance().hangupForBusy(callDevBean.getCall_id());
                                        } else
                                            SIPIntercom.getInstance().hangup(callDevBean.getCall_id());
                                    }
                                }
                                Intent intent = new Intent();
                                intent.setClass(MainActivity.activity, ConferenceActivity.class);
                                Bundle bundle = new Bundle();
                                String url_id = MMKV.defaultMMKV().getString("conference_id", "default");
                                bundle.putString("conference", url_id);
                                bundle.putInt("call_id", callID);
                                bundle.putBoolean("callout", false);
                                if (MMKV.defaultMMKV().decodeBool("dev_tts", false)) {
                                    SoundTools.getIns().setMusicSound(6);
                                    TTSUtils.getIns().ttsSpeak("有会议邀请");
                                }
                                ArrayList list = new ArrayList();//这个arraylist是可以直接在bundle里传的，所以我们可以借用一下它的功能
                                if (conferenceLst != null && conferenceLst.size() > 0) {
                                    list.add(conferenceLst);//这个list2才是你真正想要传过去的list。我们把它放在arraylis中，借助它传过去
                                }
                                bundle.putParcelableArrayList("devLst", list);
                                intent.putExtra("bundle", bundle);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                                conferenceLst.clear();
                            }

                        }, new OnCancelListener() {
                            @Override
                            public void onCancel() {
                                popupview.dismiss();
                                XLog.w("----------------拒绝会议");
                                SIPIntercom.getInstance().hangup(callID);
                            }
                        }, false);
                popupview.show();
                return;
            }
            Device dev= null;
            if(url.contains("<sip:")){
                String ip = url.substring(5, url.length() - 1);
                dev = repository.getDevByIp(ip);
            }else {
                dev = repository.getDevById(url);
            }
            CallDevBean bean = new CallDevBean();
            List<CallInfo> list = SIPIntercom.getInstance().getCallList();
            if (list != null) {
                for (CallInfo info : list) {
                    if (info.callID == callID) {
                        boolean isRec = MMKV.defaultMMKV().decodeBool("is_upload", false);
                        if (isRec) {
                            info.setLocalName(MMKV.defaultMMKV().decodeString("dev_name", "01主控机"));
                            if (dev != null && dev.getName() != null && !"".equals(dev.getName()))
                                info.setRemoteName(dev.getName());
                            else info.setRemoteName("未知设备");
                            info.upload_ip1 = MMKV.defaultMMKV().decodeString("upload_ip1", "");
                            info.upload_ip2 = MMKV.defaultMMKV().decodeString("upload_ip2", "");
                            info.upload_ip3 = MMKV.defaultMMKV().decodeString("upload_ip3", "");
                            info.isRec = true;
                        } else {
                            info.isRec = false;
                        }
                        if(!isRecord)
                            info.type = 2;
                        SIPIntercom.getInstance().updateCallInfo(info);
                    }
                }
            }
            bean.setCall_id(callID);
            if (dev != null) {
                bean.setId(dev.getId());
                bean.setIpaddr(dev.getIpaddr());
                bean.setName(dev.getName());
                if (dev.getCamera() != null) {
                    bean.setUrl1(dev.getCamera().getPath1());
                    bean.setUrl2(dev.getCamera().getPath2());
                }

            } else {
                bean.setId(url);
                bean.setName("未知设备");
            }
            CallType type = new CallType(1);
            if (saveType.containsKey(url)) {
                type = saveType.get(url);
                saveType.remove(url);
            }

            bean.setType(type);
            bean.setState(RINGING);
            bean.setRing_time(ringTime);

            callDevLst.add(bean);
            viewModel.add(bean);

            if (mCurrentIndex >= 0 && mCurrentIndex < callDevLst.size()) {
                //有新的报警呼入
                if (type.getCall_type() == 2) {        //若新呼入为报警，则判断当前正在通话的呼叫类型
                    CallDevBean tempBean = callDevLst.get(mCurrentIndex);
                    if (tempBean.getType().getCall_type() == 0) {
                        //本机呼出
                        XLog.w("onNewIntent TalkingState = " + tempBean.getState());
                        if (tempBean.getState() == TalkState.TALKING || tempBean.getState() == TalkState.PAUSED) {
                            //暂停
                            toNextSession(tempBean.getCall_id(), false);
                        } else {
                            //挂断
                            *//*if (MMKV.defaultMMKV().decodeBool("dev_tts", false) && callDevLst.get(mCurrentIndex).getType().getCall_type() != 0) {
                                TTSUtils.getIns().ttsStop();
                            }*//*
                            if (mMediaPlayer != null) {
                                mMediaPlayer.stop();
                                mMediaPlayer.release();
                                mMediaPlayer = null;
                            }
                            //hangUp(tempBean.getCall_id());
                            SIPIntercom.getInstance().hangup(tempBean.getCall_id());
                            toNextSession(tempBean.getCall_id(), true);
                        }
                    } else {
                        //呼入
                        if (tempBean.getType().getCall_type() == 1) {
                            XLog.w("onNewIntent TalkingState = " + tempBean.getState());
                            if (tempBean.getState() == TalkState.TALKING || tempBean.getState() == TalkState.PAUSED) {
                                //暂停
                                toNextSession(tempBean.getCall_id(), false);
                            } else {
                                //挂断
                                if (mMediaPlayer != null) {
                                    mMediaPlayer.stop();
                                    mMediaPlayer.release();
                                    mMediaPlayer = null;
                                }
                                //hangUp(tempBean.getCall_id());
                                SIPIntercom.getInstance().hangup(tempBean.getCall_id());
                                toNextSession(tempBean.getCall_id(), true);
                            }
                        }
                    }
                }
            }
            mHandler.sendEmptyMessage(MSG_NEW_CALL);
        }

        @Override
        public void onRegState(int accID, int state, String reason) {
            XLog.i("Activity onRegState accID = " + accID + " state = " + state + " reason = " + reason);
            //mHandler.sendEmptyMessage(MSG_LOGIN_CHANGE);
        }

        @Override
        public void onPager(String from, String body) {
            XLog.i("onPager from = " + from + " body = " + body);
            if (body.contains("<CallReason>CR_NORMAL</CallReason>")) {
                saveType.put(from, new CallType(1));
            } else if (body.contains("<CallReason>CR_ALARM</CallReason>")) {
                if (body.contains("<CallReason>CR_ALARM</CallReason>")
                        || body.contains("<CallReason>CR_PRESS_ALARM</CallReason>")
                        || body.contains("<CallReason>CR_VOICE_ALARM</CallReason>")
                        || body.contains("<CallReason>CR_PHONE_ALARM</CallReason>")
                        || body.contains("<CallReason>CR_TAMPER_ALARM</CallReason>")
                        || body.contains("<CallReason>CR_TOW_DOOR_OPEN_ALARM</CallReason>")
                        || body.contains("<CallReason>CR_BDOOR_FORCEOPEN</CallReason>")
                        || body.contains("<CallReason>CR_KW_KEYWORD</CallReason>")) {       //关键词报警
                    XmlPullParserFactory factory = null;
                    XmlPullParser xpp = null;
                    try {
                        String text = "";
                        String tag = "";
                        factory = XmlPullParserFactory.newInstance();
                        factory.setNamespaceAware(true);
                        xpp = factory.newPullParser();
                        xpp.setInput(new StringReader(body));
                        int eventType = xpp.getEventType();
                        Map<String, String> xmlMpa = new HashMap<>();
                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_DOCUMENT) {
                                //XLog.d("Start document");
                            } else if (eventType == XmlPullParser.START_TAG) {
                                //XLog.d("Start tag " + xpp.getName() + ", text = " + text + "|||");
                            } else if (eventType == XmlPullParser.END_TAG) {
                                //XLog.d("End tag " + xpp.getName() + ", text= " + text + "|||");
                                tag = xpp.getName();
                                xmlMpa.put(tag, text);
                            } else if (eventType == XmlPullParser.TEXT) {
                                //XLog.d("Text " + xpp.getText() + ", tag: " + tag);
                                text = xpp.getText();
                            }
                            eventType = xpp.next();
                        }
                        String keyText = xmlMpa.get("KeywordText");
                        if (keyText != null && !"".equals(keyText)) {
                            CallType ct = new CallType(2, keyText);
                            String voice_url = xmlMpa.get("KeywordAudioFile");
                            ct.setVoice_url(voice_url);
                            saveType.put(from, ct);
                        }
                    } catch (XmlPullParserException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }finally {
                        factory = null;
                        xpp = null;
                    }

                } else {
                    saveType.put(from, new CallType(2));
                }
            }
            if (body.contains("<CmdType>NotifyCallPause</CmdType>")) {
                if (body.contains("<CallState>1</CallState>")) {          //收到暂停通话消息

                } else if (body.contains("<CallState>0</CallState>")) {        //收到恢复通话消息

                }
            }
        }

        @Override
        public void onDtmfCallback(int callID, int dfmf, String url) {
            XLog.i("onDtmfCallback callID = " + callID + " dfmf = " + dfmf + " url = " + url);
        }

        @Override
        public void callbackVideoEncData(byte[] data, int len, int type) {
            //XLog.w("sip call video data len: " + len);
            //AudioVideoCodec.getIns().pushVideo(data, len);
        }

        @Override
        public void callbackVideoDecData(byte[] data, int len, int type) {
            //XLog.w("sip call dec video data len: " + len + ", type: " + type);
            if (isFace && data != null && data.length > 4 && !"".equals(platform_server) && !BuildConfig.CORPORATION_NAME.equals("dahua")) {
                String iframe = String.format("%02X", data[4] & 0xFF);
                if (iframe.equals("67")) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - oldTime > 2000) {
                        oldTime = currentTime;
                        poolExecutor.execute(new Runnable() {
                            @Override
                            public void run() {
                                nVideoPack++;
                                if (nVideoPack > 1 && isVideoSend) {
                                    XLog.w("--------------------video send time: " + System.currentTimeMillis());
                                    isVideoSend = false;
                                    mHandler.sendEmptyMessageDelayed(MSG_SENDFACE, 1000);

                                    RequestBean req = new RequestBean();
                                    req.time = DateUtil.longTime2String(System.currentTimeMillis());
                                    req.type = "intercom_face_info";
                                    List<FaceData> faceDatas = new ArrayList<>();
                                    FaceData faceData = new FaceData();
                                    faceData.image_type = "H264";
                                    faceData.image = Base64.encodeToString(data, Base64.NO_WRAP);
                                    faceDatas.add(faceData);
                                    req.data = faceDatas;
                                    //XLog.w("----req json: " + GsonUtil.BeanToJson(req));
                                    RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), GsonUtil.BeanToJson(req));
                                    String path = "http://" + platform_server + ":" + platform_face + "/api/intercom_face_info";
                                    XLog.w("path: " + path);
                                    //if (callDevLst != null && mCurrentIndex >= 0 && mCurrentIndex < callDevLst.size())
                                    //XLog.w("call id: " + callDevLst.get(mCurrentIndex).getCall_id() + ", call state: " + callDevLst.get(mCurrentIndex).getState());
                                    Request request = new Request.Builder().url(path).post(body).build();

                                    executeNetworkRequest(request);
                                }

                            }
                        });
                    }
                }
            }
        }

        @Override
        public void audioPlaybackCB(byte[] data, int dlen) {
            XLog.w("sip call audio data len: " + dlen);
            //AudioVideoCodec.getIns().pushAudio(data, dlen);
        }

        @Override
        public void setRecode(int callID) {
        }

        @Override
        public void onMediaState(int index, int type, int state) {
        }

        @Override
        public void onBuddyState(String account, int state) {

        }
    };*/

    long oldTime = 0;

    private void executeNetworkRequest(Request request) {
        poolExecutor.execute(new Runnable() {
            @Override
            public void run() {
                Response response = null;
                try {
                    response = mOkHttpClient.newCall(request).execute();
                    if (response != null) {
                        String result = response.body().string();
                        Message message = new Message();
                        message.what = MSG_FACE;
                        message.obj = result;
                        mHandler.sendMessage(message);
                        //XLog.w("res: " + result);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (response != null) {
                        response.close();
                    }
                }
            }
        });
    }

    /**
     * 跳到下个会话
     */
    private boolean toNextSession(final int session, boolean bRemove) {
        XLog.w("toNextSession mCurrentIndex = " + mCurrentIndex + ",size = " + callDevLst.size());
        if (last_call == session && (System.currentTimeMillis() - last_time < 1000)) {
            return false;
        }
        last_time = System.currentTimeMillis();
        last_call = session;
        int count = callDevLst.size();
        if (count > 0 && mCurrentIndex >= 0) {
            //判断是否挂断的是当前会话，如果是则需要切换会话
            boolean bchange = false;
            //XLog.w("toNextSession mCurrentIndex = " + mCurrentIndex + ",size = " + count);
            if (mCurrentIndex < count && callDevLst.get(mCurrentIndex).getCall_id() == session) {
                bchange = true;
            }
            //XLog.w("toNextSession bchange = " + bchange + ",bRemove = " + bRemove);
            if (bRemove) {
                //从列表中删除
                Iterator<CallDevBean> iter = callDevLst.iterator();
                int i = 0;
                while (iter.hasNext()) {
                    i++;
                    CallDevBean tempbean = (CallDevBean) iter.next();
                    /*if(tempbean.isLoop()){
                        mHandler.sendEmptyMessageDelayed(MSG_LOOP_CALL_START, 1000);
                        return true;
                    }*/
                    if (tempbean.getCall_id() == session) {
                        if (i < mCurrentIndex) mCurrentIndex--;
                        XLog.w("--------------------temp bean state: " + tempbean.getState() + ", i=" + i + ", index=" + mCurrentIndex);
                        if (tempbean.getState() == TALKING || tempbean.getState() == PAUSED) {
                            tempbean.setState(TalkState.TALKEND);
                            /*if (tempbean.getId().indexOf("M") != 0) {
                                //sendStopTalk(tempbean);
                            } else {
                                if (tempbean.getType().getCall_type() == 2 || tempbean.getType().getCall_type() == 1)
                                    sendStopTalk(tempbean);
                            }*/

                        }
                        updateDevStatus(tempbean);
                        viewModel.delete(tempbean);
                        iter.remove();
                        break;
                    }
                }
                mHandler.sendEmptyMessage(NOTIFY_VIEW);
            }
            int sessioncount = callDevLst.size();
            //XLog.w("toNextSession sessioncount = " + sessioncount + ",b change = " + bchange +", remove= "+ bRemove);
            if (sessioncount != 0) {
                if (bchange && bRemove) {
                    mCurrentIndex = 0;
                    adapter.setPosition(0);
                }
                for (int i = 0; i < callDevLst.size(); i++) {
                    CallDevBean tempbean = callDevLst.get(i);
                    if (tempbean.getCall_id() != session) {
                        Message m = new Message();
                        m.what = NOTIFY_VIEW1;
                        m.arg1 = i;
                        mHandler.sendMessage(m);
                        break;
                    }
                }
                return true;
            }
        }
        //这里关闭了所有会话，如果是会议转过来的或者有会议邀请过的，需要在这里询问
        if (isConference) {
            popupview = new XPopup.Builder(activity).hasNavigationBar(false) //设置是否显示导航栏，默认是显示的。如果你希望弹窗隐藏导航栏，就设置为true
                    .isViewMode(true).isDestroyOnDismiss(true).maxWidth(650).hasBlurBg(true)//是否设置背景为高斯模糊背景。默认为false
                    .dismissOnTouchOutside(false)//设置点击弹窗外面是否关闭弹窗，默认为true
                    .autoDismiss(false)//设置当操作完毕后是否自动关闭弹窗，默认为true。比如：点击Confirm弹窗的确认按钮默认是关闭弹窗，如果为false，则不关闭
                    .popupAnimation(PopupAnimation.NoAnimation)// 为弹窗设置内置的动画器，默认情况下，已经为每种弹窗设置了效果最佳的动画器；如果你不喜欢，仍然可以修改。
                    .asConfirm("会议", "是否前往会议？", "否", "是", new OnConfirmListener() {
                        @Override
                        public void onConfirm() {
                            popupview.dismiss();
                            finish();
                            XLog.w("---------------------应答前往会议");
                            RxTimerUtil.getInstance().timer(800, new RxTimerUtil.IRxNext() {
                                @Override
                                public void doNext(long number) {
                                    String server = MMKV.defaultMMKV().decodeString("dev_server", "192.168.8.254");
                                    int port = MMKV.defaultMMKV().decodeInt("conference_port", 8021);
                                    String conference_id = MMKV.defaultMMKV().decodeString("conference_id", "default");
                                    String addr = server + ":" + port;
                                    InboundClient.getInstance().sendAsyncApiCommand(addr, "originate", "user/" + local_id + " &conference(" + conference_id + ")");

                                }
                            });

                        }

                    }, new OnCancelListener() {
                        @Override
                        public void onCancel() {
                            popupview.dismiss();
                            XLog.w("----------------拒绝会议");
                            mHandler.sendEmptyMessageDelayed(MSG_DESTROY, 100);
                            //finish();
                        }
                    }, false);
            popupview.show();
        } else {
            mHandler.sendEmptyMessageDelayed(MSG_DESTROY, 100);
            //finish();
        }

        return false;
    }

    /**
     * 呼出
     */
    private void callOut(CallDevBean bean, boolean isChin) {
        XLog.i("call out-------");

        Random rand = new Random();
        int i;
        boolean flag = false;
        i = rand.nextInt(1000) + 1;
        String reason = "CR_NORMAL";
        if (isChin) {
            reason = "CR_CHIMEIN";
        }
        String message = "<Notify>" + "<CmdType>NotifyCallReason</CmdType>" + "<SN>" + i + "</SN>" + "<DeviceID>" + local_id + "</DeviceID>" + "<CallReason>" + reason + "</CallReason>" + "</Notify>";
        if (!BuildConfig.CORPORATION_NAME.equals("dahua")) {
            if (SIPIntercom.getInstance().isOnline()) {
                flag = SIPIntercom.getInstance().sendMessage(bean.getId(), message, false);
            } else {
                if (bean.getIpaddr() != null && !"".equals(bean.getIpaddr()))
                    flag = SIPIntercom.getInstance().sendMessage(bean.getIpaddr(), message, true);
            }
        }
        message = null;
        XLog.w("send message flag: " + flag);
        String dev_name = MMKV.defaultMMKV().decodeString("dev_name", "01主控机");
        String ip1 = MMKV.defaultMMKV().decodeString("upload_ip1", "");
        String ip2 = MMKV.defaultMMKV().decodeString("upload_ip2", "");
        String ip3 = MMKV.defaultMMKV().decodeString("upload_ip3", "");
        CallInfo info = new CallInfo();
        info.remoteName = bean.getName();
        if (isChin) info.type = 2;
        else info.type = 0;
        if(!isRecord)
            info.type = 2;
        info.isRec = MMKV.defaultMMKV().decodeBool("is_upload", false);
        //info.dateStr = DateUtil.toymdhms(System.currentTimeMillis());
        info.localName = dev_name;
        info.upload_ip1 = ip1;
        info.upload_ip2 = ip2;
        info.upload_ip3 = ip3;
        binding.accept.setText("呼叫中");
        binding.accept.setClickable(false);
        if (bean.getType().getCall_type() == 5) {
            int acc = MMKV.defaultMMKV().decodeInt("voip_acc", -1);
            if (acc != -1) {
                info.remoteAccount = bean.getId().replace(" ", "");
                String ser = MMKV.defaultMMKV().decodeString("voip_server", "");
                if (!"".equals(ser)) flag = SIPIntercom.getInstance().callOut(acc, ser, info);
                return;
            }
        }
        if (SIPIntercom.getInstance().isOnline() || BuildConfig.CORPORATION_NAME.equals("dahua")) {
            XLog.i("==============sip 在线， 通过id呼叫， id = " + bean.getId());
            info.remoteAccount = bean.getId();
            flag = SIPIntercom.getInstance().callOut(info);
        } else {
            info.remoteAccount = bean.getIpaddr();
            flag = SIPIntercom.getInstance().callOutForIP(info);
        }
        if(bean.isLoop()) {
            mHandler.removeMessages(MSG_DESTROY);
            mHandler.removeMessages(MSG_LOOP_CALL_END);
            mHandler.sendEmptyMessageDelayed(MSG_LOOP_CALL_END, 15 * 1000);
        }
        XLog.w("sip make call flag: " + flag);
    }

    private void calling() {
        XLog.i("calling---------");
        /*if (MMKV.defaultMMKV().decodeBool("dev_tts", false) && callDevLst.get(mCurrentIndex).getType().getCall_type() != 0) {
            TTSUtils.getIns().ttsStop();
        }*/
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }

        if (callDevLst.size() <= mCurrentIndex) return;
        if (callDevLst.get(mCurrentIndex).getState() == TALKING) return;

        //callDevLst.get(mCurrentIndex).setState(TALKING);;
        int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        //记录当前的音量
        int currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        XLog.w("------------system current vol: " + currentVolume + ", max vol: " + maxVolume);
        if (maxVolume != currentVolume) {
            //通话中要把系统音量调节到最大，否则门口机听到的声音会很小，如果还想再调整声音则修改AECpara.inf文件
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0);
        }
        //boolean result = SIPIntercom.getInstance().setDisplayArea(mVideoArea[0], mVideoArea[1], mVideoArea[2], mVideoArea[3]);

        if (callDevLst.get(mCurrentIndex).getType().getCall_type() == 3) {
            SIPIntercom.getInstance().setTxVolume(0);     //设置收音音量
        } else {
            //SIPIntercom.getInstance().setTxVolume(100);
        }
        boolean flag = SIPIntercom.getInstance().setRxVolume(chat_local_volume);
        XLog.w("----------------set play volume: " + flag + ", size: " + chat_local_volume);

    }

    /**
     * 呼出或呼入之后，进行通话之前需要执行的函数，主要功能为播放铃声，更改ui显示
     */
    private void callIn() {
        XLog.i("call in ----");
        // mMediaPlayer = MediaPlayer.create(this, R.raw.ring_you);
        String content = "呼叫";
        if (callDevLst.get(mCurrentIndex).getType().getCall_type() == 2) {
            content = "报警";
            if (callDevLst.get(mCurrentIndex).getType().getCall_content() != null && !"".equals(callDevLst.get(mCurrentIndex).getType().getCall_content())) {
                content = callDevLst.get(mCurrentIndex).getType().getCall_content() + content;
            }
        }
        if (MMKV.defaultMMKV().decodeBool("dev_tts", false) && callDevLst.get(mCurrentIndex).getType().getCall_type() != 0) {
            SoundTools.getIns().setMusicSound(ring_volume);
            TTSUtils.getIns().ttsSpeak(callDevLst.get(mCurrentIndex).getName() + content);
        }else {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setVolume(ring_volume, ring_volume);
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, ring_volume, AudioManager.FLAG_PLAY_SOUND);
            mMediaPlayer.setLooping(true);
            try {
                String path = "/sdcard/UserDev/mp3/";
                if (callDevLst.get(mCurrentIndex).getType().getCall_type() == 1 || callDevLst.get(mCurrentIndex).getType().getCall_type() == 0) {
                    mMediaPlayer.setDataSource(path + ring_file);
                } else if (callDevLst.get(mCurrentIndex).getType().getCall_type() == 2) {
                    mMediaPlayer.setDataSource(path + alarm_file);
                }
                //mMediaPlayer.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
                mMediaPlayer.prepare();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mMediaPlayer.start();
        }
    }

    /**
     * 对方振铃，主动呼叫的时候才会回振铃
     */
    private void phoneRinged(final int seesion) {
        XLog.w("phoneRinged");
        if (mCurrentIndex >= 0 && mCurrentIndex < callDevLst.size()) {
            if (isBtn) {
                Message msg = new Message();
                msg.what = BTN_CALL;
                msg.arg1 = seesion;
                mHandler.sendMessage(msg);
            }
            callDevLst.get(mCurrentIndex).setCall_id(seesion);
            callDevLst.get(mCurrentIndex).setState(RINGING);
            viewModel.update(mCurrentIndex, callDevLst.get(mCurrentIndex));
            UpdateState();
        }
    }

    //  主动接听
    private boolean accept() {
        XLog.w("phoneAccept");
        /*if (MMKV.defaultMMKV().decodeBool("dev_tts", false) && callDevLst.get(mCurrentIndex).getType().getCall_type() != 0) {
            TTSUtils.getIns().ttsStop();
        }*/
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        if (callDevLst.size() == 0) return false;
        if (callDevLst.get(mCurrentIndex).getState() == TALKING) return true;
        calling();
        SIPIntercom.getInstance().answer(callDevLst.get(mCurrentIndex).getCall_id());

        callDevLst.get(mCurrentIndex).setState(TALKING);
        viewModel.update(callDevLst.get(mCurrentIndex));
        //adapter.updateItem(mCurrentIndex, callDevLst.get(mCurrentIndex));
        return true;
    }

    //对方接听
    private void phoneAccept(int callId) {
        XLog.w("phoneAccepted");
        calling();
        if (callDevLst.size() == 0 || mCurrentIndex >= callDevLst.size()) return;
        callDevLst.get(mCurrentIndex).setCall_id(callId);
        callDevLst.get(mCurrentIndex).setState(TALKING);
        isTalking = true;
        viewModel.update(mCurrentIndex, callDevLst.get(mCurrentIndex));

    }

    //主动挂断
    private void phoneHangup() {
        XLog.w("phoneHangup-----");
        /*if (MMKV.defaultMMKV().decodeBool("dev_tts", false) && callDevLst.get(mCurrentIndex).getType().getCall_type() != 0) {
            SoundTools.getIns().setMusicSound(ring_volume);
            TTSUtils.getIns().ttsStop();
        }*/
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        if (mCurrentIndex >= 0 && mCurrentIndex < callDevLst.size()) {
            CallDevBean temp = callDevLst.get(mCurrentIndex);
            XLog.w("----------------temp. state = " + temp.getState() + ", call id = " + temp.getCall_id());
            if (temp.getState() == TALKING || temp.getState() == PAUSED) {
                isTalking = false;
                SIPIntercom.getInstance().hangup(temp.getCall_id());
            } else {
                SIPIntercom.getInstance().hangupForBusy(temp.getCall_id());
            }

            if (toNextSession(temp.getCall_id(), true)) {
                resetTalkingTime();
                UpdateState();
            }
        }
    }

    private void timeHangup(CallDevBean dev) {
        XLog.w("timeHangup-----" + dev.toString());
        /*if (MMKV.defaultMMKV().decodeBool("dev_tts", false) && callDevLst.get(mCurrentIndex).getType().getCall_type() != 0) {
            TTSUtils.getIns().ttsStop();
        }*/
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }

        Random r = new Random();
        int sn = r.nextInt(10000) + 1;
        String message = "<Notify>" + "<CmdType>NotifyHangupReason</CmdType>" + "<SN>" + sn + "</SN>" + "<DeviceID>" + local_id + "</DeviceID>" + "<HangupReason>HR_TIMEOUT</HangupReason>" + "</Notify>";
        if (!BuildConfig.CORPORATION_NAME.equals("dahua")) {
            if (SIPIntercom.getInstance().isOnline()) {
                SIPIntercom.getInstance().sendMessage(dev.getId(), message, false);
            } else {
                if (dev.getIpaddr() != null && !"".equals(dev.getIpaddr()))
                    SIPIntercom.getInstance().sendMessage(dev.getIpaddr(), message, true);
            }
        }
        message = null;
        SIPIntercom.getInstance().hangupForBusy(dev.getCall_id());
        if (toNextSession(dev.getCall_id(), true)) {
            UpdateState();
        }

    }

    /**
     * 对方挂断
     */
    private void phoneHanguped(final int session) {
        XLog.w("phoneHanguped session = " + session);
        /*if (MMKV.defaultMMKV().decodeBool("dev_tts", false) && callDevLst.get(mCurrentIndex).getType().getCall_type() != 0) {
            SoundTools.getIns().setMusicSound(ring_volume);
            TTSUtils.getIns().ttsStop();
        }*/
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        if (callDevLst != null) {         //多线程删除列表内元素出了问题，这边先加个判断
            for (CallDevBean bean : callDevLst) {
                if (session == bean.getCall_id()) {
                    if (bean.getState() == TALKING) isTalking = false;
                    toNextSession(session, true);
                    return;
                }
            }
        }
    }

    private void hangUp() {
        XLog.i("--on hangup 1");

        if (mCurrentIndex != -1 && callDevLst != null && callDevLst.size() > mCurrentIndex && callDevLst.get(mCurrentIndex).getState() == TALKING) {
            isTalking = false;
            ArrayList<CallInfo> list = SIPIntercom.getInstance().getCallList();
            if (list != null) {
                SIPIntercom.getInstance().hangup(callDevLst.get(mCurrentIndex).getCall_id());
                //viewModel.delete(callDevLst.get(mCurrentIndex));
                //removeCall(callDevLst.get(mCurrentIndex));
                //callDevLst.remove(mCurrentIndex);
            } else {
                SIPIntercom.getInstance().hangupAll();
            }
        } else {
            if (callDevLst.size() > 0) {
                SIPIntercom.getInstance().hangupForReject(callDevLst.get(mCurrentIndex).getCall_id());
            } else {
                SIPIntercom.getInstance().hangupAll();
            }
        }
        //isTalking = false;
        if(callDevLst.size() > 0) {
            if (toNextSession(callDevLst.get(mCurrentIndex).getCall_id(), true)) {
                resetTalkingTime();
                UpdateState();
            }
        }

    }

    /**
     * 按钮状态修改
     *
     * @param isEnable 是否启用
     * @param isHost   是否主机
     */
    private void btnStateChange(boolean isEnable, boolean isHost) {
        if (isEnable) {
            if (isHost) {
                binding.transform.setEnabled(false);
                binding.transform.setBackground(getDrawable(R.drawable.border_line_canal));
            } else {
                binding.transform.setEnabled(true);
                binding.transform.setBackground(getDrawable(R.drawable.border_line_blue));
            }
            binding.function.setEnabled(true);
            binding.function.setBackground(getDrawable(R.drawable.border_line_blue));
            if(!BuildConfig.CORPORATION_NAME.equals("dahua")) {
                binding.keep.setEnabled(true);
                binding.keep.setBackground(getDrawable(R.drawable.border_line_blue));
            }
        } else {
            binding.transform.setEnabled(false);
            binding.transform.setBackground(getDrawable(R.drawable.border_line_canal));
            binding.function.setEnabled(false);
            binding.function.setBackground(getDrawable(R.drawable.border_line_canal));
            binding.keep.setEnabled(false);
            binding.keep.setBackground(getDrawable(R.drawable.border_line_canal));
        }
    }


    /**
     * 更新状态栏
     */
    public void UpdateState() {
        int size = callDevLst.size();
        if (size == 0) return;
        else {
            for (int i = 0; i < size; i++) {
                CallDevBean bean = callDevLst.get(i);
                //XLog.w(bean.toString());
                int type = callDevLst.get(i).getType().getCall_type();
                if (type == 0) {
                    bean.setTitle("呼叫 " + bean.getName());
                } else if (type == 1) {
                    bean.setTitle(bean.getName() + "请求通话");
                } else if (type == 2) {
                    bean.setTitle(bean.getName() + "报警");
                } else if (type == 3) {
                    bean.setTitle("监听 " + bean.getName());
                } else if (type == 4) {
                    bean.setTitle("会议");
                }

                switch (callDevLst.get(i).getState()) {
                    case CALLIN:
                    case RINGING:
                    case CALLOUTING:
                        bean.setTitle(bean.getTitle() + "响铃中");
                        break;
                    case TALKING:
                        bean.setTitle(bean.getName() + "通话中");
                        break;
                    case PAUSED:
                        bean.setTitle(bean.getName() + "通话保留");
                        break;
                    default:
                        break;
                }
            }
        }
        //adapter.notifyDataSetChanged();
    }

    /**
     * 复位通话时间
     */
    @SuppressLint("DefaultLocale")
    public void resetTalkingTime() {
        //talk_time = MMKV.defaultMMKV().decodeInt("chat_time", 10);      //这里是重新赋值
        chat_time = 0;
        binding.time.setText("00:00:00");
    }

    /**
     * 开锁
     *
     * @param doorNumber 1 门锁A 2 门锁B
     */
    private void unlock(int doorNumber) {
        if (mCurrentIndex < callDevLst.size()) {
            XLog.w("unlock +++++++++++++++++++++++");
            String cid = callDevLst.get(mCurrentIndex).getId();
            Random rand = new Random();
            int i;
            i = rand.nextInt(1000) + 1;
            String message = "<Notify>" + "<CmdType>NotifyOpenLock</CmdType>" + "<SN>" + i + "</SN>" + "<DeviceID>" + local_id + "</DeviceID>" + "<RecvID>" + cid + "</RecvID>" + "<DoorNo>" + doorNumber + "</DoorNo>" + "</Notify>";
            if (!BuildConfig.CORPORATION_NAME.equals("dahua")) {
                if (SIPIntercom.getInstance().isOnline()) {
                    SIPIntercom.getInstance().sendMessage(callDevLst.get(mCurrentIndex).getId(), message, false);
                } else {
                    if (callDevLst.get(mCurrentIndex).getIpaddr() != null && !"".equals(callDevLst.get(mCurrentIndex).getIpaddr()))
                        SIPIntercom.getInstance().sendMessage(callDevLst.get(mCurrentIndex).getIpaddr(), message, true);
                }
            }
            cid = null;
            message = null;
        }
    }

    /**
     * 恢复通话
     *
     * @param index 当前通话索引
     */
    private void toResumeTalking(int index) {
        if (index >= 0 && index < callDevLst.size()) {
            SIPIntercom.getInstance().reInvite(callDevLst.get(mCurrentIndex).getCall_id());
            callDevLst.get(index).setState(TalkState.TALKING);
            binding.keep.setText("保留");
            UpdateState();
        }
    }

    /**
     * 停止通话
     *
     * @param index     当前通话索引
     * @param nextIndex 下一个通话索引
     */
    private void toPauseTalking(int index, int nextIndex) {
        if (index >= 0 && index < callDevLst.size()) {
            CallDevBean tempbean = callDevLst.get(index);
            int i = rand.nextInt(1000) + 1;
            if (tempbean.getState() == TalkState.TALKING) {
                if (!BuildConfig.CORPORATION_NAME.equals("dahua")) {
                    String message = "<Notify>" + "<CmdType>NotifyCallPause</CmdType>" + "<SN>" + i + "</SN>" + "<CallState>1</CallState>" + "<DeviceID>" + local_id + "</DeviceID>" + "<CallReason>CR_NORMAL</CallReason>" + "</Notify>";
                    if (SIPIntercom.getInstance().isOnline()) {
                        SIPIntercom.getInstance().sendMessage(tempbean.getId(), message, false);
                    } else {
                        if (tempbean.getIpaddr() != null && !"".equals(tempbean.getIpaddr()))
                            SIPIntercom.getInstance().sendMessage(tempbean.getIpaddr(), message, true);
                    }
                    message = null;
                }
                if (isScreen) {
                    isScreen = false;
                    if (binding.ipcImage.isShown() && !binding.videoPlayer1.isShown()) {
                        binding.ipcImage.setVisibility(View.GONE);
                    } else if (!binding.ipcImage.isShown() && !binding.videoPlayer1.isShown()) {
                        binding.ipcImage.setVisibility(View.VISIBLE);
                    }
                    mHandler.sendEmptyMessage(MSG_VIDEO_TRANSFER);
                }
                if(!BuildConfig.CORPORATION_NAME.equals("dahua")){
                    keepTalking();          //dahua设备不支持通话保留
                }
                //SIPIntercom.getInstance().callHold(tempbean.getCall_id());        //需要手动保留后才能进行下一个

                /*tempbean.setState(TalkState.PAUSED);
                if (nextIndex == index) {
                    binding.keep.setText("唤醒");
                } else {
                    if (callDevLst.get(nextIndex).getState() == TalkState.PAUSED)
                        binding.keep.setText("唤醒");
                    resetTalkingTime();
                    binding.accept.setClickable(true);
                }*/
            }
            //UpdateState();
        }
    }

    /**
     * 保留通话
     */
    private void keepTalking() {
        XLog.w("phoneKeepTalking");
        Random rand = new Random();
        int i;
        i = rand.nextInt(1000) + 1;
        if (callDevLst.size() > mCurrentIndex && callDevLst.get(mCurrentIndex).getState() == TalkState.PAUSED) {
            //toResumeTalking(mCurrentIndex);
            if (!BuildConfig.CORPORATION_NAME.equals("dahua")) {
                String message = "<Notify>" + "<CmdType>NotifyCallPause</CmdType>" + "<SN>" + i + "</SN>" + "<CallState>0</CallState>" + "<DeviceID>" + local_id + "</DeviceID>" + "<CallReason>CR_NORMAL</CallReason>" + "</Notify>";
                if (SIPIntercom.getInstance().isOnline()) {
                    SIPIntercom.getInstance().sendMessage(callDevLst.get(mCurrentIndex).getId(), message, false);
                } else {
                    if (callDevLst.get(mCurrentIndex).getIpaddr() != null && !"".equals(callDevLst.get(mCurrentIndex).getIpaddr()))
                        SIPIntercom.getInstance().sendMessage(callDevLst.get(mCurrentIndex).getIpaddr(), message, true);
                }
                message = null;
            }
            callDevLst.get(mCurrentIndex).setState(TALKING);
            SIPIntercom.getInstance().reInvite(callDevLst.get(mCurrentIndex).getCall_id());
            setClientVol();
            binding.keep.setText("保留");
            binding.accept.setText("通话中");
            binding.accept.setBackground(getDrawable(R.drawable.border_line_calling));
            isTalking = true;
            if (isScreen) {
                isScreen = false;
                mHandler.sendEmptyMessage(MSG_VIDEO_TRANSFER);
                if (binding.ipcImage.isShown() && !binding.videoPlayer1.isShown()) {
                    binding.ipcImage.setVisibility(View.GONE);
                } else if (!binding.ipcImage.isShown() && !binding.videoPlayer1.isShown()) {
                    binding.ipcImage.setVisibility(View.VISIBLE);
                }
            }
        } else if (callDevLst.size() > mCurrentIndex && callDevLst.get(mCurrentIndex).getState() == TALKING) {
            //toPauseTalking(mCurrentIndex, mCurrentIndex);
            if (!BuildConfig.CORPORATION_NAME.equals("dahua")) {
                String message = "<Notify>" + "<CmdType>NotifyCallPause</CmdType>" + "<SN>" + i + "</SN>" + "<CallState>1</CallState>" + "<DeviceID>" + local_id + "</DeviceID>" + "<CallReason>CR_NORMAL</CallReason>" + "</Notify>";
                if (SIPIntercom.getInstance().isOnline()) {
                    SIPIntercom.getInstance().sendMessage(callDevLst.get(mCurrentIndex).getId(), message, false);
                } else {
                    SIPIntercom.getInstance().sendMessage(callDevLst.get(mCurrentIndex).getIpaddr(), message, true);
                }
                message = null;
            }
            callDevLst.get(mCurrentIndex).setState(TalkState.PAUSED);
            SIPIntercom.getInstance().callHold(callDevLst.get(mCurrentIndex).getCall_id());
            binding.keep.setText("唤醒");
            binding.accept.setText("通话暂停");
            binding.accept.setBackground(getDrawable(R.drawable.border_line_blue));
            isTalking = false;
            if (isScreen) {
                isScreen = false;
                if (binding.ipcImage.isShown() && !binding.videoPlayer1.isShown()) {
                    binding.ipcImage.setVisibility(View.GONE);
                } else if (!binding.ipcImage.isShown() && !binding.videoPlayer1.isShown()) {
                    binding.ipcImage.setVisibility(View.VISIBLE);
                }
                mHandler.sendEmptyMessage(MSG_VIDEO_TRANSFER);
            }
        }
        if (callDevLst.size() > mCurrentIndex) viewModel.update(callDevLst.get(mCurrentIndex));
    }

    private void initTransform() {
        dialogAdapter = new CallDialogAdapter(new CallDialogAdapter.HostDiff(), this);
        poolExecutor.execute(new Runnable() {
            @Override
            public void run() {
                List<Device> list = repository.getHostDevice(local_id);
                dialogAdapter.submitList(list);
            }
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        binding.transformList.setLayoutManager(layoutManager);
        binding.transformList.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        binding.transformList.setAdapter(dialogAdapter);
    }

    private void transform() {
        binding.transformView.setVisibility(View.VISIBLE);
        binding.transformView.setElevation(10);
    }

    /**
     * 手柄状态改变
     * 手柄提起， 发起手柄提起通知
     * 手柄放下，发起手柄放下通知
     */
    public void headsetStateChange(boolean state) {
        if (callDevLst.size() == 0) return;
        XLog.w("headsetStateChange JniClass.HandsetUp = " + state + ",sta:" + callDevLst.get(mCurrentIndex).getState() + ", type: " + callDevLst.get(mCurrentIndex).getType().getCall_type());
        if (callDevLst.get(mCurrentIndex).getState() == TALKING) {
            if (state) {
                /*mService.headsetUsr(true);
                if (callDevLst.get(mCurrentIndex).getType() == 3) {
                    SIPIntercom.getInstance().setTxVolume(40);
                }*/
                //
                SIPIntercom.getInstance().setAec(true);
                mHandler.sendEmptyMessage(HEAD_UP);
            } else {
                /*if (callDevLst.get(mCurrentIndex).getType() == 3) {       //插话处理
                    mService.headsetUsr(false);
                    if (!isChip) {
                        SIPIntercom.getInstance().setTxVolume(0);
                    }

                } else {

                }*/
                SIPIntercom.getInstance().setAec(true);
                mHandler.sendEmptyMessage(HEAD_DOWN);
            }
        } else if (callDevLst.get(mCurrentIndex).getState() == RINGING ||
                callDevLst.get(mCurrentIndex).getState() == CALLIN) {
            if (state) {
                if (callDevLst.get(mCurrentIndex).getType().getCall_type() == 1 ||
                        callDevLst.get(mCurrentIndex).getType().getCall_type() == 2) {
                    if (accept()) {
                        headsetStateChange(state);
                    }
                }
            }
        }
    }


    public void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }


    /**
     * 修改设备状态信息
     *
     * @param bean 通话设备
     */
    private void updateDevStatus(CallDevBean bean) {
        poolExecutor.execute(new Runnable() {
            @Override
            public void run() {
                Device device = repository.getDevById(bean.getId());
                if (device != null) {
                    if (bean.getState() == TALKING || bean.getState() == PAUSED) {
                        device.setState(DevState.DEVICE_TALKING.getValue());
                    } else if (bean.getState() == RINGING || bean.getState() == CALLIN || bean.getState() == CALLOUTING) {
                        if (bean.getType().getCall_type() == 1) {
                            device.setState(DevState.DEVICE_CALL_MISS.getValue());
                        } else if (bean.getType().getCall_type() == 2) {
                            device.setState(DevState.DEVICE_ALARMING.getValue());
                        }
                    } else {
                        device.setState(DevState.DEVICE_ONLINE.getValue());
                    }
                    repository.update(device);
                }
            }
        });
    }

    private void initNetworkCamera() {
        XLog.w("------initNetworkCamera");
        /*JniIPCOnvif.getIns().initJni();
        JniIPCOnvif.getIns().jniInit();
        JniIPCOnvif.getIns().jniSetSelectVideoResolution(1280, 720);*/
        JniIPCOnvif.getIns().setCallback(new JniIPCOnvif.Callback() {
            @Override
            public void onVideoFrame(int id, byte[] buf, int len, boolean keyFrame) {
                //Log.i(TAG, "buf.length=" + buf.length + " len=" + len + " keyFrame=" + keyFrame);
                if (id != ip_camera_id) {
                    return;
                }
                if (buf != null && ipc_decoder_id > 0) {
                    //XLog.w( "onVideoFrame pushVideo");
                    AudioVideoCodec.getIns().pushVideo(ipc_decoder_id, buf, len);
                    //XLog.w( "onVideoFrame pushVideo end");
                }
                if (!mConnected) {
                    mConnected = true;
                    mHandler.removeMessages(MSG_IPC_TIMEOUT);
                }
            }

            @Override
            public void onStatus(int id, int type) {
                XLog.w("onStatus id " + id + " type " + type + " ip_camera_id " + ip_camera_id);
                switch (type) {
                    case IPCConstant.ERR_AUTHENTICATE:
                    case IPCConstant.ERR_REMOTE_OFFLINE:
                    case IPCConstant.ERR_ONVIF_SEARCH:
                        stopMonitorIpc();
                        if (mConnected) {
                            XLog.w("设备已断开");
                        } else {
                            if (mUseOnvif) {
                                mHandler.sendEmptyMessageDelayed(MSG_IPC_CONNECT, 250);
                            } else {
                                XLog.e("监视设备失败！");
                            }
                        }
                        break;
                }
            }
        });
        //AudioVideoCodec.getIns().init();
        ipcSurface = findViewById(R.id.video_player1);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            VideoRender.Attach(this,  ipcSurface, "IPCameraVideoSurface", new RenderCallBackJni());
        }
        ipcSurface.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                ipcSurface.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                //int[] location = new int[2];
                //ipcSurface.getLocationOnScreen(location);
            }
        });

    }

    private void startMonitorIpc(String url) {
        if ("".equals(url) || url == null) {
            binding.videoPlayer1.setVisibility(View.GONE);
            binding.ipcImage.setVisibility(View.VISIBLE);
            binding.ipcToggle.setVisibility(View.VISIBLE);
            return;
        } else {
            binding.videoPlayer1.setVisibility(View.VISIBLE);
            binding.ipcImage.setVisibility(View.GONE);
            binding.ipcToggle.setVisibility(View.VISIBLE);
        }
        poolExecutor.execute(new Runnable() {
            @Override
            public void run() {
                mConnected = false;
                mVideoConfig = new VideoConfig();
                mVideoConfig.dsp_left = mCameraArea[0];
                mVideoConfig.dsp_top = mCameraArea[1];
                mVideoConfig.dsp_width = mCameraArea[2];
                mVideoConfig.dsp_height = mCameraArea[3];
                mVideoConfig.decode_width = mCameraArea[2];
                mVideoConfig.decode_height = mCameraArea[3];
                mVideoConfig.devName = "IPCameraVideoSurface";
                ipc_decoder_id = AudioVideoCodec.getIns().startVideoDecoder(mVideoConfig);
                XLog.w("call startVideoDecoder " + ipc_decoder_id);

                mIPCSetting = new IPCSetting();
                // 使用Onvif协议监视
                mIPCSetting.bUseOnvif = mUseOnvif = false;
                mIPCSetting.rtspUrl = url;

                XLog.w("call xx mIPCSetting.rtspUrl:" + mIPCSetting.rtspUrl);
                ip_camera_id = JniIPCOnvif.getIns().jniStartMonitor(mIPCSetting);
                XLog.w("call 打开网络摄像头 ip_camera_id " + ip_camera_id);
                if (ip_camera_id == -1) {
                    AudioVideoCodec.getIns().stopVideoDecoder(ipc_decoder_id);
                    XLog.w("监视设备失败");
                } else {
                    XLog.w("正在连接设备");
                    mHandler.sendEmptyMessageDelayed(MSG_IPC_TIMEOUT, 10 * 1000);
                }
            }
        });

    }

    private void stopMonitorIpc() {
        XLog.w("call stopMonitorIpc ");
        mHandler.removeMessages(MSG_IPC_CONNECT);
        mHandler.removeMessages(MSG_IPC_TIMEOUT);
        if (ip_camera_id != -1) {
            JniIPCOnvif.getIns().jniStopMonitor(ip_camera_id);
            ip_camera_id = -1;
        }
        if (ipc_decoder_id > 0) {
            AudioVideoCodec.getIns().stopVideoDecoder(ipc_decoder_id);
            ipc_decoder_id = 0;
        }
    }

    private void popupFunction() {
        binding.functionView.setVisibility(View.VISIBLE);
    }

    /**
     * {
     * "deviceSn":"Abf5622222022",
     * "deviceName":"101分机",
     * "ip":"192.168.12.2",
     * "mac":"F2-4E-41-EB-B0-D6",
     * "uuid":"84949cc5-4701-4a84-895b-354c584a981b",
     * "eventName":"M001001",
     * "eventType":"talking",
     * "ts":"1639564686",
     * "data":[
     * {
     * "imageType":"H264",
     * "image":"=zI43Ycv990ou"
     * }
     * ]
     * }
     */

    private void sendStartTalk(CallDevBean devBean) {
        String platform_server = MMKV.defaultMMKV().decodeString("platform_server", "");
        if ("".equals(platform_server)) return;
        IpResult ipResult = SystemUtils.getIns().getIpAddress(NetworkTools.NETWORK_CARD_ETH0);

        Map<String, Object> jsonMap = new HashMap<>();
        Map<String, Object> dataMap = new HashMap<>();
        jsonMap.put("deviceSn", MMKV.defaultMMKV().decodeString("dev_serial", ""));
        jsonMap.put("deviceName", devBean.getName());
        jsonMap.put("ip", ipResult.ipAddress);
        jsonMap.put("mac", SystemUtilsHelper.getMacAddress());
        jsonMap.put("uuid", devBean.getUuid());
        jsonMap.put("eventName", "");
        jsonMap.put("eventType", "talking");
        jsonMap.put("ts", "" + System.currentTimeMillis());

        dataMap.put("imageType", "H264");
        dataMap.put("image", "");
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(dataMap);
        jsonMap.put("data", list);
        String json = GsonUtil.BeanToJson(jsonMap);
        XLog.w("send start talk json: " + json);
        String url = "http://" + platform_server + ":3866/api/face_cmp";
        poolExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(60);
                    Map<String, String> headers = new HashMap<>();
                    headers.put("accept-type", "Charsets.UTF_8");
                    headers.put(HttpHeaders.Names.CONTENT_TYPE, "application/json; charset=UTF-8");
                    HttpUtil.post(url, HttpUtil.buildJsonRequestBody(json), headers);
                } catch (IOException e) {
                    XLog.e("http io exception message: " + e.getMessage());
                    // e.printStackTrace();
                } catch (InterruptedException e) {
                    XLog.e("http InterruptedException message: " + e.getMessage());
                }
            }
        });
    }

    /**
     * {
     * "deviceSn":"Abf5622222022",
     * "data":[
     * {
     * "uuid":"84949cc5-4701-4a84-895b-354c584a981b",
     * "eventName":"talk end",
     * "eventType":"talk end",
     * "keywords":""
     * }
     * ]
     * }
     */
    private void sendStopTalk(CallDevBean devBean) {
        String platform_server = MMKV.defaultMMKV().decodeString("platform_server", "");
        if ("".equals(platform_server)) return;
        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("deviceSn", MMKV.defaultMMKV().decodeString("dev_serial", ""));
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("uuid", devBean.getUuid());
        dataMap.put("eventName", "talk end");
        dataMap.put("eventType", "talk end");
        dataMap.put("keywords", "");
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(dataMap);
        jsonMap.put("data", list);
        String json = GsonUtil.BeanToJson(jsonMap);
        XLog.w("send stop json: " + json);
        String url = "http://" + platform_server + ":3866/api/face_cmp";
        poolExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("accept-type", "Charsets.UTF_8");
                    headers.put(HttpHeaders.Names.CONTENT_TYPE, "application/json; charset=UTF-8");
                    HttpUtil.post(url, HttpUtil.buildJsonRequestBody(json), headers);
                } catch (IOException e) {
                    XLog.e("http io exception message: " + e.getMessage());
                    //e.printStackTrace();
                }
            }
        });
    }

    private void saveBitmap(Bitmap bitmap, int sss) {
        try {
            File dirFile = new File("/sdcard/UserDev/image");
            if (!dirFile.exists()) {              //如果不存在，那就建立这个文件夹
                dirFile.mkdirs();
            }
            File file = new File("/sdcard/UserDev/image", sss + ".jpg");
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private final static String filePath = "/sdcard/test.h264";
    public static void saveByteArrayToFile(byte[] data) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(filePath, true); // 使用追加模式
            fos.write(data);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
