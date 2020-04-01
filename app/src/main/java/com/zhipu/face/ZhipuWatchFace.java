package com.zhipu.face;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class ZhipuWatchFace extends CanvasWatchFaceService {
    private static final String TAG = ZhipuWatchFace.class.getSimpleName();
    private boolean mInAmbientMode;
    private static final int MSG_UPDATE_TIME = 0;
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    @Override
    public Engine onCreateEngine() {
        return new FaceEngine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<FaceEngine> mWeakReference;

        private EngineHandler(FaceEngine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            FaceEngine engine = mWeakReference.get();
            if (engine != null) {
                if (msg.what == MSG_UPDATE_TIME) {
                    engine.handleUpdateTimeMessage();
                }
            }
        }
    }

    private class FaceEngine extends CanvasWatchFaceService.Engine {
        private static final float POINT_RADIUS = 5.0f;//圆点半径
        private static final int POINTS = 60;//绘制60个点
        private final double RADIANS = Math.toRadians(1.0f * 360 / POINTS);//弧度值，Math.toRadians：度换算成弧度
        private Paint mPaintText;
        private Paint mPaintPoint;
        private Rect mRect = new Rect();
        private Paint mPaintPointer;
        private float mCenterX;//圆心X坐标
        private float mCenterY;//圆心Y坐标
        private float mMaxRadius;

        //生命周期：启动表盘，onCreate->onSurfaceChanged->onDraw；更换表盘，onDestroy
        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            Log.d(TAG, "---FaceEngine, onCreate---");
            //setAcceptsTapEvents(true)：注册onTapCommand(int tapType, int x, int y, long eventTime)监听
            setWatchFaceStyle(new WatchFaceStyle.Builder(ZhipuWatchFace.this).setAcceptsTapEvents(true)
                    .setAccentColor(Color.WHITE).setShowUnreadCountIndicator(true).build());

            this.initPaint();
        }

        private void initPaint() {
            mPaintText = new Paint();
            mPaintText.setColor(Color.WHITE);
            mPaintText.setAntiAlias(true);
            mPaintText.setTextSize(50f);
            mPaintText.setTypeface(Typeface.defaultFromStyle(Typeface.ITALIC));

            mPaintPoint = new Paint();
            mPaintPoint.setColor(Color.WHITE);
            mPaintPoint.setAntiAlias(true);

            mPaintPointer = new Paint();
            mPaintPointer.setAntiAlias(true);
            mPaintPointer.setDither(true);
        }

        @Override
        public void onDestroy() {
            Log.d(TAG, "---FaceEngine, onDestroy---");
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            int width = canvas.getWidth();
            int height = canvas.getHeight();
            Log.d(TAG, "---FaceEngine, onDraw---width = " + width + ", height = " + height);

            int hour = Calendar.getInstance().get(Calendar.HOUR);//时
            int minute = Calendar.getInstance().get(Calendar.MINUTE);//分
            int second = Calendar.getInstance().get(Calendar.SECOND);//秒
            //Log.d(TAG, "---FaceEngine, paintPointer---hour = " + hour + ", minute = " + minute + ", second = " + second);

            String text = "zhipu";
            float textPaintHeight;
            if (mInAmbientMode) {
                canvas.drawColor(Color.parseColor("#666666"));//画面背景

                mPaintText.setTextSize(64f);
                mPaintText.setTypeface(Typeface.defaultFromStyle(Typeface.NORMAL));
                String time = addZero(hour) + ":" + addZero(minute);
                mRect.setEmpty();
                mPaintText.getTextBounds(time, 0, time.length(), mRect);
                canvas.drawText(time, (width - mPaintText.measureText(time)) / 2, 1.0f * height / 2 - 12, mPaintText);

                //canvas.drawLine(0, height / 2, width, height / 2, mPaintText);//垂直居中线

                mPaintText.setTextSize(50f);
                mPaintText.setTypeface(Typeface.defaultFromStyle(Typeface.ITALIC));
                mRect.setEmpty();
                mPaintText.getTextBounds(text, 0, text.length(), mRect);
                //textPaintHeight = 1.0f * height / 2 + 1.0f * mRect.height() / 4;//文字垂直居中
                textPaintHeight = (height + 1.0f * mRect.height()) / 2 + 12;
                canvas.drawText(text, (width - mPaintText.measureText(text)) / 2, textPaintHeight, mPaintText);
            } else {
                canvas.drawColor(Color.parseColor("#00A3E5"));//画面背景

                textPaintHeight = (height - mPaintText.getTextSize() + 16);//文字到屏幕底部有一定间距

                mCenterX = 1.0f * width / 2;//圆心X坐标
                mCenterY = 1.0f * height / 2 - 28;//圆心Y坐标
                mMaxRadius = Math.min(mCenterX, mCenterY);

                this.paintPointer(canvas, hour, minute, second);
                this.paintScaleNumber(canvas);

                mPaintText.setTextSize(50f);
                mPaintText.setTypeface(Typeface.defaultFromStyle(Typeface.ITALIC));
                canvas.drawText(text, (width - mPaintText.measureText(text)) / 2, textPaintHeight, mPaintText);
            }

        }

        private String addZero(int number) {
            StringBuilder sBuilder = new StringBuilder(String.valueOf(number));
            if (sBuilder.length() < 2) {
                sBuilder.insert(0, "0");
            }
            return sBuilder.toString();
        }

        private void paintScaleNumber(Canvas canvas) {
            canvas.save();

            mPaintText.setTextSize(26f);
            mPaintText.setTypeface(Typeface.defaultFromStyle(Typeface.NORMAL));
            String digit = "0";
            mPaintText.getTextBounds(digit, 0, digit.length(), mRect);

            mPaintText.setStyle(Paint.Style.STROKE);
            float radius = mMaxRadius - 30;
            float radiusText = radius - mRect.height() - 4;

            mPaintText.setStyle(Paint.Style.FILL);
            float cxPoint, cyPoint, cxText, cyText;
            double sinValue, cosValue;
            for (int index = 0; index < POINTS; index++) {
                sinValue = Math.sin(RADIANS * index);
                cosValue = Math.cos(RADIANS * index);
                cxPoint = (float) (mCenterX + radius * sinValue);
                cyPoint = (float) (mCenterY - radius * cosValue);
                cxText = (float) (mCenterX - radiusText * sinValue);
                cyText = (float) (mCenterY - radiusText * cosValue);
                if (index % 5 == 0) {
                    canvas.drawCircle(cxPoint, cyPoint, POINT_RADIUS, mPaintPoint);

                    digit = String.valueOf((index / 5) == 0 ? 12 : 12 - (index / 5));
                    mRect.setEmpty();
                    mPaintText.getTextBounds(digit, 0, digit.length(), mRect);
                    canvas.drawText(digit, cxText - 1.0f * mRect.width() / 2, cyText + 1.0f * mRect.height() / 2, mPaintText);
                } else {
                    canvas.drawCircle(cxPoint, cyPoint, POINT_RADIUS / 2, mPaintPoint);
                }
            }

            canvas.restore();
        }

        private float mHourPointWidth = 4;//时针宽度
        private float mMinutePointWidth = 3;//分针宽度
        private float mSecondPointWidth = 2;//秒针宽度
        private int mHourPointColor = Color.RED; //时针的颜色
        private int mMinutePointColor = Color.BLACK;//分针的颜色
        private int mSecondPointColor = Color.WHITE;//秒针的颜色
        private float mPointRadius = 2;//指针圆角
        private float mPointEndLength = 20;//指针末尾长度

        private void paintPointer(Canvas canvas, int hour, int minute, int second) {
            float radius = mMaxRadius - 32;

            //转过的角度
            float angleHour = (hour + (float) minute / 60) * 360 / 12;
            float angleMinute = (minute + (float) second / 60) * 360 / 60;
            int angleSecond = second * 360 / 60;

            //绘制时针
            canvas.save();
            canvas.rotate(angleHour, mCenterX, mCenterY); // 旋转到时针的角度
            RectF rectHour = new RectF(mCenterX - mHourPointWidth / 2, mCenterY - radius * 3 / 6,
                    mCenterX + mHourPointWidth / 2, mCenterY + mPointEndLength);
            mPaintPointer.setColor(mHourPointColor);
            mPaintPointer.setStyle(Paint.Style.STROKE);
            mPaintPointer.setStrokeWidth(mHourPointWidth);
            canvas.drawRoundRect(rectHour, mPointRadius, mPointRadius, mPaintPointer);
            canvas.restore();
            //绘制分针
            canvas.save();
            canvas.rotate(angleMinute, mCenterX, mCenterY); //旋转到分针的角度
            RectF rectMinute = new RectF(mCenterX - mMinutePointWidth / 2, mCenterY - radius * 3.5f / 5,
                    mCenterX + mMinutePointWidth / 2, mCenterY + mPointEndLength);
            mPaintPointer.setColor(mMinutePointColor);
            mPaintPointer.setStrokeWidth(mMinutePointWidth);
            canvas.drawRoundRect(rectMinute, mPointRadius, mPointRadius, mPaintPointer);
            canvas.restore();
            //绘制分针
            canvas.save();
            canvas.rotate(angleSecond, mCenterX, mCenterY); //旋转到分针的角度
            RectF rectSecond = new RectF(mCenterX - mSecondPointWidth / 2, mCenterY - radius + 12,
                    mCenterX + mSecondPointWidth / 2, mCenterY + mPointEndLength);
            mPaintPointer.setStrokeWidth(mSecondPointWidth);
            mPaintPointer.setColor(mSecondPointColor);
            canvas.drawRoundRect(rectSecond, mPointRadius, mPointRadius, mPaintPointer);
            canvas.restore();

            //绘制原点
            canvas.save();
            mPaintPointer.setStyle(Paint.Style.FILL);
            canvas.drawCircle(mCenterX, mCenterY, mSecondPointWidth * 4, mPaintPointer);
            canvas.restore();
        }

        private boolean mRegisteredTimeZoneReceiver = false;

        //调整时区，系统会广播此事件
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Calendar.getInstance().setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            ZhipuWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            ZhipuWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private final Handler mUpdateTimeHandler = new EngineHandler(this);

        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        private boolean shouldTimerBeRunning() {
            return isVisible() && !mInAmbientMode;
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            Log.d(TAG, "---FaceEngine, onSurfaceChanged---");
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            //微光模式下，系统每分钟调用一次该方法
            Log.d(TAG, "---FaceEngine, onTimeTick---");
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            //模式改变时调用该方法：false，交互模式；true，微光模式
            Log.d(TAG, "---FaceEngine, onAmbientModeChanged---, inAmbientMode = " + inAmbientMode);
            mInAmbientMode = inAmbientMode;
            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                mPaintText.setAntiAlias(antiAlias);
                mPaintPoint.setAntiAlias(antiAlias);
                mPaintPointer.setAntiAlias(antiAlias);
            }
            updateTimer();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            //false：表盘不可见，如按表冠键显示其它页面导致表盘界面不可见时会调用该方法
            Log.d(TAG, "---FaceEngine, onVisibilityChanged---, visible = " + visible);
            if (visible) {
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                Calendar.getInstance().setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            updateTimer();
        }

        private boolean mLowBitAmbient;

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            boolean burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            Log.d(TAG, "---FaceEngine, onPropertiesChanged---, lowBitAmbient = " + mLowBitAmbient
                    + ", burnInProtection = " + burnInProtection);
        }

        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    Log.d(TAG, "---FaceEngine, onTapCommand---, tapType = " + tapType + ", touch");
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    Log.d(TAG, "---FaceEngine, onTapCommand---, tapType = " + tapType + ", touch cancel");
                    break;
                case TAP_TYPE_TAP:
                    Log.d(TAG, "---FaceEngine, onTapCommand---, tapType = " + tapType + ", tap");
                    break;
                default:
                    break;
            }
            invalidate();
        }
    }

}
