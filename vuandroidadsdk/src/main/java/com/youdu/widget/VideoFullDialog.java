package com.youdu.widget;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.youdu.activity.AdBrowserActivity;
import com.youdu.adutil.LogUtils;
import com.youdu.adutil.Utils;
import com.youdu.constant.SDKConstant;
import com.youdu.core.video.VideoAdSlot.AdSDKSlotListener;
import com.youdu.module.AdValue;
import com.youdu.report.ReportManager;
import com.youdu.vuandroidadsdk.R;
import com.youdu.widget.CustomVideoView.ADVideoPlayerListener;

/**
 * @author: qndroid
 * @function: 全屏显示视频
 * @date: 16/6/7
 */
public class VideoFullDialog extends Dialog implements ADVideoPlayerListener {

    private static final String TAG = VideoFullDialog.class.getSimpleName();
    private CustomVideoView mVideoView;

    private Context mContext;
    private RelativeLayout mRootView;
    private ViewGroup mParentView;
    private ImageView mBackButton;

    private AdValue mXAdInstance;
    private int mPosition;//从小屏到全屏时视频的播放位置
    private FullToSmallListener mListener;
    private boolean isFirst = true;
    //动画要执行的平移值
    private int deltaY;
    private AdSDKSlotListener mSlotListener;
    private Bundle mStartBundle;
    private Bundle mEndBundle; //用于Dialog出入场动画

    public VideoFullDialog(Context context,
                           CustomVideoView mraidView,
                           AdValue instance,
                           int position) {
        super(context, R.style.dialog_full_screen);//通过style的设置，保证我们的dialog全屏
        mContext = context;
        mXAdInstance = instance;
        mPosition = position;
        mVideoView = mraidView;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.xadsdk_dialog_video_layout);
        initVideoView();
    }

    public void setViewBundle(Bundle bundle) {
        mStartBundle = bundle;
    }

    /**
     * 注入事件监听类
     */
    public void setListener(FullToSmallListener listener) {
        this.mListener = listener;
    }

//    public void setFrameLoadListener(XADFrameImageLoadListener listener) {
//        this.mFrameLoadListener = listener;
//    }

    public void setSlotListener(AdSDKSlotListener slotListener) {
        this.mSlotListener = slotListener;
    }

    private void initVideoView() {
        mParentView = (RelativeLayout) findViewById(R.id.content_layout);
        mBackButton = (ImageView) findViewById(R.id.xadsdk_player_close_btn);
        mBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClickBackBtn();
            }
        });
        mRootView = (RelativeLayout) findViewById(R.id.root_view);
        mRootView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClickVideo();
            }
        });
        mRootView.setVisibility(View.INVISIBLE);

        mVideoView.setListener(this);
        mVideoView.mute(false);
        mParentView.addView(mVideoView);
        mParentView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mParentView.getViewTreeObserver().removeOnPreDrawListener(this);
                prepareScene();
                runEnterAnimation();
                return true;
            }
        });
    }

    /**
     * dialog 焦点发生改变时的回调
     * @param hasFocus
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        LogUtils.i(TAG, "onWindowFocusChanged");
        mVideoView.isShowFullBtn(false); //防止第一次，有些手机仍显示全屏按钮
        if (!hasFocus) {//未取得焦点时的逻辑
            mPosition = mVideoView.getCurrentPosition();
            mVideoView.pauseForFullScreen();
        } else {//取得焦点时的逻辑
            //isFist ==true时是dialog首次创建且首次获得焦点
            if (isFirst) { //为了适配某些手机不执行seekandresume中的播放方法
                mVideoView.seekAndResume(mPosition);
            } else {
                mVideoView.resume();
            }
        }
        isFirst = false;
    }

    /**
     * 销毁时调用
     * 从小屏到全屏时，是将videoview从小屏的根部局移出，添加到dialog的跟布局，如果在dialog销毁时不一除，
     * 又添加到小屏的跟布局中，这样就会报错，因为一个view只能有一个父布局
     */
    @Override
    public void dismiss() {
        LogUtils.e(TAG, "dismiss");
        mParentView.removeView(mVideoView);
        super.dismiss();
    }

    @Override
    public void onBackPressed() {
        onClickBackBtn();
        //super.onBackPressed(); 禁止掉返回键本身的关闭功能,转为自己的关闭效果
    }

    @Override
    public void onClickFullScreenBtn() {
        onClickVideo();
    }

    @Override
    public void onClickVideo() {
        String desationUrl = mXAdInstance.clickUrl;
        if (mSlotListener != null) {
            if (mVideoView.isFrameHidden() && !TextUtils.isEmpty(desationUrl)) {
                mSlotListener.onClickVideo(desationUrl);
                try {
                    ReportManager.pauseVideoReport(mXAdInstance.clickMonitor, mVideoView.getCurrentPosition()
                            / SDKConstant.MILLION_UNIT);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            //走默认样式
            if (mVideoView.isFrameHidden() && !TextUtils.isEmpty(desationUrl)) {
                Intent intent = new Intent(mContext, AdBrowserActivity.class);
                intent.putExtra(AdBrowserActivity.KEY_URL, mXAdInstance.clickUrl);
                mContext.startActivity(intent);
                try {
                    ReportManager.pauseVideoReport(mXAdInstance.clickMonitor, mVideoView.getCurrentPosition()
                            / SDKConstant.MILLION_UNIT);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 全屏返回关闭按钮点击事件
     */
    @Override
    public void onClickBackBtn() {
        runExitAnimator();
    }

    //准备动画所需数据
    private void prepareScene() {
        mEndBundle = Utils.getViewProperty(mVideoView);
        /**
         * 将desationview移到originalview位置处
         */
        deltaY = (mStartBundle.getInt(Utils.PROPNAME_SCREENLOCATION_TOP)
                - mEndBundle.getInt(Utils.PROPNAME_SCREENLOCATION_TOP));
        mVideoView.setTranslationY(deltaY);
    }

    //准备入场动画
    private void runEnterAnimation() {
        mVideoView.animate()
                .setDuration(200)
                .setInterpolator(new LinearInterpolator())
                .translationY(0)
                .withStartAction(new Runnable() {
                    @Override
                    public void run() {
                        mRootView.setVisibility(View.VISIBLE);
                    }
                })
                .start();
    }

    //准备出场动画
    private void runExitAnimator() {
        mVideoView.animate()
                .setDuration(200)
                .setInterpolator(new LinearInterpolator())
                .translationY(deltaY)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        dismiss();
                        try {
                            ReportManager.exitfullScreenReport(mXAdInstance.event.exitFull.content, mVideoView.getCurrentPosition()
                                    / SDKConstant.MILLION_UNIT);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        if (mListener != null) {
                            mListener.getCurrentPlayPosition(mVideoView.getCurrentPosition());
                        }
                    }
                }).start();
    }

    @Override
    public void onAdVideoLoadSuccess() {
        if (mVideoView != null) {
            mVideoView.resume();
        }
    }

    @Override
    public void onAdVideoLoadFailed() {
    }

    @Override
    public void onAdVideoLoadComplete() {
        try {
            int position = mVideoView.getDuration() / SDKConstant.MILLION_UNIT;
            ReportManager.sueReport(mXAdInstance.endMonitor, true, position);
        } catch (Exception e) {
            e.printStackTrace();
        }

        dismiss();
        if (mListener != null) {
            mListener.playComplete();
        }
    }

    @Override
    public void onBufferUpdate(int time) {
        try {
            if (mXAdInstance != null) {
                ReportManager.suReport(mXAdInstance.middleMonitor, time / SDKConstant.MILLION_UNIT);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClickPlay() {

    }

    /**
     * 与我们的业务逻辑层（VideoAdSlot）进行通信
     */
    public interface FullToSmallListener {
        void getCurrentPlayPosition(int position);//全屏播放中点击关闭按钮或者back键时回调

        void playComplete();//全屏播放结束时回调
    }
}
