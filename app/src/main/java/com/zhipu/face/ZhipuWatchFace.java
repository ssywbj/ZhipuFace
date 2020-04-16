package com.zhipu.face;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.SystemProviders;
import android.support.wearable.complications.rendering.ComplicationDrawable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.palette.graphics.Palette;

import com.zhipu.face.config.AnalogComplicationConfigRecyclerViewAdapter;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class ZhipuWatchFace extends CanvasWatchFaceService {
    public static final String TAG = ZhipuWatchFace.class.getSimpleName();
    private static final int MSG_UPDATE_TIME = 0;

    // Unique IDs for each complication. The settings activity that supports allowing users
    // to select their complication data provider requires numbers to be >= 0.
    private static final int BACKGROUND_COMPLICATION_ID = 0;

    private static final int LEFT_COMPLICATION_ID = 100;
    private static final int RIGHT_COMPLICATION_ID = 101;
    private static final int BOTTOM_COMPLICATION_ID = 102;

    // Background, Left and right complication IDs as array for Complication API.
    private static final int[] COMPLICATION_IDS = {
            BACKGROUND_COMPLICATION_ID, LEFT_COMPLICATION_ID, RIGHT_COMPLICATION_ID, BOTTOM_COMPLICATION_ID
    };

    // Used by {@link AnalogComplicationConfigRecyclerViewAdapter} to check if complication location
    // is supported in settings config activity.
    public static int getComplicationId(
            AnalogComplicationConfigRecyclerViewAdapter.ComplicationLocation complicationLocation) {
        // Add any other supported locations here.
        switch (complicationLocation) {
            case BACKGROUND:
                return BACKGROUND_COMPLICATION_ID;
            case LEFT:
                return LEFT_COMPLICATION_ID;
            case RIGHT:
                return RIGHT_COMPLICATION_ID;
            case BOTTOM:
                return BOTTOM_COMPLICATION_ID;
            default:
                return -1;
        }
    }

    // Used by {@link AnalogComplicationConfigRecyclerViewAdapter} to retrieve all complication ids.
    public static int[] getComplicationIds() {
        return COMPLICATION_IDS;
    }

    // Used by {@link AnalogComplicationConfigRecyclerViewAdapter} to see which complication types
    // are supported in the settings config activity.
    public static int[] getSupportedComplicationTypes(
            AnalogComplicationConfigRecyclerViewAdapter.ComplicationLocation complicationLocation) {
        // Add any other supported locations here.
        switch (complicationLocation) {
            case BACKGROUND:
                return COMPLICATION_SUPPORTED_TYPES[0];
            case LEFT:
                return COMPLICATION_SUPPORTED_TYPES[1];
            case RIGHT:
                return COMPLICATION_SUPPORTED_TYPES[2];
            case BOTTOM:
                return COMPLICATION_SUPPORTED_TYPES[3];
            default:
                return new int[]{};
        }
    }

    // Left and right dial supported types.
    private static final int[][] COMPLICATION_SUPPORTED_TYPES = {
            {ComplicationData.TYPE_LARGE_IMAGE},
            {
                    ComplicationData.TYPE_RANGED_VALUE,
                    ComplicationData.TYPE_ICON,
                    ComplicationData.TYPE_SHORT_TEXT,
                    ComplicationData.TYPE_SMALL_IMAGE
            },
            {
                    ComplicationData.TYPE_RANGED_VALUE,
                    ComplicationData.TYPE_ICON,
                    ComplicationData.TYPE_SHORT_TEXT,
                    ComplicationData.TYPE_SMALL_IMAGE
            },
            {
                    ComplicationData.TYPE_RANGED_VALUE,
                    ComplicationData.TYPE_ICON,
                    ComplicationData.TYPE_SHORT_TEXT,
                    ComplicationData.TYPE_SMALL_IMAGE
            }
    };

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

    /**
     * implement service callback methods
     * 表盘生命周期：onCreate->onSurfaceChanged->onDraw->onDestroy
     * 启动表盘：onCreate->onSurfaceChanged->onDraw；更换表盘：onVisibilityChanged(false)->onDestroy
     */
    private class FaceEngine extends CanvasWatchFaceService.Engine {
        private final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

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

        private float mHourPointWidth = 4;//时针宽度
        private float mMinutePointWidth = 3;//分针宽度
        private float mSecondPointWidth = 2;//秒针宽度
        private int mHourPointColor = Color.RED; //时针的颜色
        private int mMinutePointColor = Color.BLACK;//分针的颜色
        private int mSecondPointColor = Color.WHITE;//秒针的颜色
        private float mPointRadius = 2;//指针圆角
        private float mPointEndLength = 20;//指针末尾长度

        private boolean mLowBitAmbient, mBurnInProtection, mInAmbientMode;

        private Context mContext;
        // Used to pull user's preferences for background color, highlight color, and visual
        // indicating there are unread notifications.
        SharedPreferences mSharedPref;

        private final Handler mUpdateTimeHandler = new EngineHandler(this);

        /**
         * 时区调整广播
         */
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Calendar.getInstance().setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean mRegisteredTimeZoneReceiver = false;

        private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                    int level = intent.getIntExtra("level", 0);//当前电量
                    int total = intent.getIntExtra("scale", 100);//总电量
                    int percentage = (level * 100) / total;
                    Log.d(TAG, "battery, level = " + level + ", total = " + total + ", percentage = " + percentage + "%");
                }
            }
        };
        private boolean mRegisteredBatteryReceiver = false;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            Log.d(TAG, "onCreate, initialize watch face");

            //setAcceptsTapEvents(true)：注册onTapCommand(int, int, int, long)监听
            setWatchFaceStyle(new WatchFaceStyle.Builder(ZhipuWatchFace.this).setAcceptsTapEvents(true)
                    /*.setAccentColor(Color.RED).setHideNotificationIndicator(true)*/.setShowUnreadCountIndicator(true).build());

            // Used throughout watch face to pull user's preferences.
            mContext = getApplicationContext();
            mSharedPref = mContext.getSharedPreferences(
                            getString(R.string.analog_complication_preference_file_key), Context.MODE_PRIVATE);

            this.initPaint();

            // Set defaults for colors
            mWatchHandHighlightColor = Color.RED;

            this.initializeComplicationsAndBackground();

            setDefaultSystemComplicationProvider(LEFT_COMPLICATION_ID, SystemProviders.WATCH_BATTERY, ComplicationData.TYPE_SHORT_TEXT);
            setDefaultSystemComplicationProvider(BOTTOM_COMPLICATION_ID, SystemProviders.STEP_COUNT,
                    ComplicationData.TYPE_SHORT_TEXT);
            /*setDefaultSystemComplicationProvider(RIGHT_COMPLICATION_ID, SystemProviders.DATE,
                    ComplicationData.TYPE_SHORT_TEXT);*/
            setDefaultComplicationProvider(RIGHT_COMPLICATION_ID, new ComponentName(mContext, DataService.class)
                    , ComplicationData.TYPE_SHORT_TEXT);

            SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            if (sensorManager == null) {
                Log.e(TAG, "SensorManager is null !");
            } else {
                List<Sensor> list = sensorManager.getSensorList(Sensor.TYPE_ALL);
                for (Sensor sensor : list) {
                    Log.d(TAG, "sensor: " + sensor + ", name: " + sensor.getName());
                }

                Sensor stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
                if (stepCounterSensor == null) {
                    Log.e(TAG, "StepCounterSensor is null !");
                } else {
                    sensorManager.registerListener(new SensorEventListener() {
                        @Override
                        public void onSensorChanged(SensorEvent event) {
                            if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
                                Log.d(TAG, "onSensorChanged，当前步数：" + event.values[0]);//values[0]为计步历史累加值
                            }
                        }

                        @Override
                        public void onAccuracyChanged(Sensor sensor, int accuracy) {
                        }
                    }, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL);
                }
            }
        }

        private Bitmap mBackgroundBitmap;

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            Log.d(TAG, "onSurfaceChanged, format = " + format + ", width = " + width + ", height = " + height);
            mCenterX = 1.0f * width / 2;//圆心X坐标
            mCenterY = 1.0f * height / 2 - 28;//圆心Y坐标
            mMaxRadius = Math.min(mCenterX, mCenterY);

            /* Scale loaded background image (more efficient) if surface dimensions change. */
            float scale = ((float) width) / (float) mBackgroundBitmap.getWidth();

            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int) (mBackgroundBitmap.getWidth() * scale),
                    (int) (mBackgroundBitmap.getHeight() * scale), true);

            /*
             * Calculates location bounds for right and left circular complications. Please note,
             * we are not demonstrating a long text complication in this watch face.
             *
             * We suggest using at least 1/4 of the screen width for circular (or squared)
             * complications and 2/3 of the screen width for wide rectangular complications for
             * better readability.
             */

            // For most Wear devices, width and height are the same, so we just chose one (width).
            int offset = 30;
            int sizeOfComplication = width / 4 - 15;
            int midpointOfScreen = (int) mCenterX;
            int verticalOffset = (int) (mCenterY - (sizeOfComplication / 2));

            // Left, Top, Right, Bottom
            Rect leftBounds = new Rect(midpointOfScreen - offset - sizeOfComplication, verticalOffset,
                    (midpointOfScreen - offset), (verticalOffset + sizeOfComplication));
            ComplicationDrawable leftComplicationDrawable = mComplicationDrawableSparseArray.get(LEFT_COMPLICATION_ID);
            this.setComplicationDrawable(leftBounds, leftComplicationDrawable);
            leftComplicationDrawable.setRangedValueProgressHidden(true);

            Rect rightBounds = new Rect((midpointOfScreen + offset), verticalOffset,
                    (midpointOfScreen + offset + sizeOfComplication), (verticalOffset + sizeOfComplication));
            ComplicationDrawable rightComplicationDrawable = mComplicationDrawableSparseArray.get(RIGHT_COMPLICATION_ID);
            this.setComplicationDrawable(rightBounds, rightComplicationDrawable);
            //rightComplicationDrawable.setBorderColorActive(Color.TRANSPARENT);
            rightComplicationDrawable.setHighlightDuration(0);

            Rect bottomBounds = new Rect((midpointOfScreen - sizeOfComplication / 2), (int) mCenterY + offset,
                    (midpointOfScreen + +sizeOfComplication / 2), ((int) mCenterY + offset + sizeOfComplication));
            ComplicationDrawable bottomComplicationDrawable = mComplicationDrawableSparseArray.get(BOTTOM_COMPLICATION_ID);
            this.setComplicationDrawable(bottomBounds, bottomComplicationDrawable);
            bottomComplicationDrawable.setRangedValueSecondaryColorActive(Color.WHITE);

            ComplicationDrawable backgroundComplicationDrawable =
                    mComplicationDrawableSparseArray.get(BACKGROUND_COMPLICATION_ID);
            backgroundComplicationDrawable.setBounds(new Rect(0, 0, width, height));
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            Log.d(TAG, "onPropertiesChanged, get device features (burn-in, low-bit ambient), " +
                    "lowBitAmbient = " + mLowBitAmbient + ", burnInProtection = " + mBurnInProtection);

            // Updates complications to properly render in ambient mode based on the
            // screen's capabilities.
            ComplicationDrawable complicationDrawable;

            for (int complicationId : COMPLICATION_IDS) {
                complicationDrawable = mComplicationDrawableSparseArray.get(complicationId);

                complicationDrawable.setLowBitAmbient(mLowBitAmbient);
                complicationDrawable.setBurnInProtection(mBurnInProtection);
            }
        }

        /**
         * 微光模式下，系统每分钟调用一次该方法
         */
        @Override
        public void onTimeTick() {
            super.onTimeTick();
            Log.d(TAG, "onTimeTick, the time changed");
            invalidate();
        }

        /**
         * 模式改变时调用该方法。微光模式下，为了延长电池续航时间，绘制表盘主题的代码应相对简单，通常使用一组有限的颜色
         * 来绘制形状的轮廓；交互模式下，可以使用全彩色、复杂的形状、渐变和动画来绘制表盘主题。
         *
         * @param inAmbientMode true:微光模式，false:交互模式
         */
        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            Log.d(TAG, "onAmbientModeChanged, the wearable switched between modes, inAmbientMode = " + inAmbientMode);
            mInAmbientMode = inAmbientMode;
            if (mInAmbientMode) {
                this.unregisterBatteryReceiver();
            } else {
                this.registerBatteryReceiver();
            }

            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                mPaintText.setAntiAlias(antiAlias);
                mPaintPoint.setAntiAlias(antiAlias);
                mPaintPointer.setAntiAlias(antiAlias);
            }
            this.updateTimer();
        }

        /**
         * 表盘界面不可见时会调用该方法，如按表冠键显示其它页面导致
         *
         * @param visible true:表盘可见，反之不可见
         */
        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            Log.d(TAG, "onVisibilityChanged, visible = " + visible);
            if (visible) {
                this.registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                Calendar.getInstance().setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                this.unregisterReceiver();

                this.unregisterBatteryReceiver();
            }

            this.updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            int width = canvas.getWidth();
            int height = canvas.getHeight();
            Log.d(TAG, "onDraw, draw watch face, width = " + width + ", height = " + height);

            int hour = Calendar.getInstance().get(Calendar.HOUR);//时
            int minute = Calendar.getInstance().get(Calendar.MINUTE);//分
            int second = Calendar.getInstance().get(Calendar.SECOND);//秒
            //Log.d(TAG, "---FaceEngine, paintPointer---hour = " + hour + ", minute = " + minute + ", second = " + second);

            String text = "zhipu";
            float textPaintHeight;
            if (mInAmbientMode) {
                canvas.drawColor(ContextCompat.getColor(mContext, R.color.zhipu_watchface_ambient_mode));//画面背景

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
                canvas.drawColor(ContextCompat.getColor(mContext, R.color.zhipu_watchface_interactive_mode));//画面背景

                textPaintHeight = (height - mPaintText.getTextSize() + 16);//文字到屏幕底部有一定间距

                this.paintPointer(canvas, hour, minute, second);
                this.paintScaleNumber(canvas);

                mPaintText.setTextSize(50f);
                mPaintText.setTypeface(Typeface.defaultFromStyle(Typeface.ITALIC));
                canvas.drawText(text, (width - mPaintText.measureText(text)) / 2, textPaintHeight, mPaintText);

                this.drawComplications(canvas, System.currentTimeMillis());
                //this.drawUnreadNotificationIcon(canvas);
            }
        }

        private void drawUnreadNotificationIcon(Canvas canvas) {
            int width = canvas.getWidth();
            int height = canvas.getHeight();

            canvas.drawCircle(width / 2, height - 40, 10, mPaintPoint);

            /*
             * Ensure center highlight circle is only drawn in interactive mode. This ensures
             * we don't burn the screen with a solid circle in ambient mode.
             */
            if (!mInAmbientMode) {
                canvas.drawCircle(width / 2, height - 40, 4, mPaintPointer);
            }
        }

        /* Maps active complication ids to the data for that complication. Note: Data will only be
         * present if the user has chosen a provider via the settings activity for the watch face.
         */
        private SparseArray<ComplicationData> mActiveComplicationDataSparseArray;
        /* Maps complication ids to corresponding ComplicationDrawable that renders the
         * the complication data on the watch face.
         */
        private SparseArray<ComplicationDrawable> mComplicationDrawableSparseArray;

        private int mWatchHandHighlightColor;

        /*
         * Called when there is updated data for a complication id.
         */
        @Override
        public void onComplicationDataUpdate(int watchFaceComplicationId, ComplicationData data) {
            super.onComplicationDataUpdate(watchFaceComplicationId, data);
            Log.d(TAG, "onComplicationDataUpdate, watchFaceComplicationId = " + watchFaceComplicationId + ", data = " + data);

            // Adds/updates active complication data in the array.
            mActiveComplicationDataSparseArray.put(watchFaceComplicationId, data);

            // Updates correct ComplicationDrawable with updated data.
            mComplicationDrawableSparseArray.get(watchFaceComplicationId).setComplicationData(data);

            invalidate();
        }

        private boolean mMuteMode;

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);
            Log.d(TAG, "onInterruptionFilterChanged, inMuteMode: " + inMuteMode);

            /* Dim display in mute mode. */
            /*if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode;
                mHourPaint.setAlpha(inMuteMode ? 100 : 255);
                mMinutePaint.setAlpha(inMuteMode ? 100 : 255);
                mSecondPaint.setAlpha(inMuteMode ? 80 : 255);
                invalidate();
            }*/
        }

        @Override
        public void onUnreadCountChanged(int count) {
            Log.d(TAG, "onUnreadCountChanged, count: " + count);
        }

        private void drawComplications(Canvas canvas, long currentTimeMillis) {
            ComplicationDrawable complicationDrawable;
            for (int complicationId : COMPLICATION_IDS) {
                complicationDrawable = mComplicationDrawableSparseArray.get(complicationId);

                complicationDrawable.draw(canvas, currentTimeMillis);
            }
        }

        private Paint mBackgroundPaint;
        private int mBackgroundColor = Color.BLACK;

        private void initializeComplicationsAndBackground() {
            // Initialize background color (in case background complication is inactive).
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(mBackgroundColor);

            mActiveComplicationDataSparseArray = new SparseArray<>(COMPLICATION_IDS.length);

            // Creates a ComplicationDrawable for each location where the user can render a
            // complication on the watch face. In this watch face, we create one for left, right,
            // and background, but you could add many more.
            ComplicationDrawable leftComplicationDrawable =
                    new ComplicationDrawable(getApplicationContext());

            ComplicationDrawable rightComplicationDrawable =
                    new ComplicationDrawable(getApplicationContext());

            ComplicationDrawable bottomComplicationDrawable =
                    new ComplicationDrawable(getApplicationContext());

            ComplicationDrawable backgroundComplicationDrawable =
                    new ComplicationDrawable(getApplicationContext());

            // Adds new complications to a SparseArray to simplify setting styles and ambient
            // properties for all complications, i.e., iterate over them all.
            mComplicationDrawableSparseArray = new SparseArray<>(COMPLICATION_IDS.length);

            mComplicationDrawableSparseArray.put(LEFT_COMPLICATION_ID, leftComplicationDrawable);
            mComplicationDrawableSparseArray.put(RIGHT_COMPLICATION_ID, rightComplicationDrawable);
            mComplicationDrawableSparseArray.put(BOTTOM_COMPLICATION_ID, bottomComplicationDrawable);
            mComplicationDrawableSparseArray.put(BACKGROUND_COMPLICATION_ID, backgroundComplicationDrawable);

            setComplicationsActiveAndAmbientColors(mWatchHandHighlightColor);
            setActiveComplications(COMPLICATION_IDS);
        }

        /* Sets active/ambient mode colors for all complications.
         *
         * Note: With the rest of the watch face, we update the paint colors based on
         * ambient/active mode callbacks, but because the ComplicationDrawable handles
         * the active/ambient colors, we only set the colors twice. Once at initialization and
         * again if the user changes the highlight color via AnalogComplicationConfigActivity.
         */
        private void setComplicationsActiveAndAmbientColors(int primaryComplicationColor) {
            int complicationId;
            ComplicationDrawable complicationDrawable;

            for (int i = 0; i < COMPLICATION_IDS.length; i++) {
                complicationId = COMPLICATION_IDS[i];
                complicationDrawable = mComplicationDrawableSparseArray.get(complicationId);

                if (complicationId == BACKGROUND_COMPLICATION_ID) {
                    // It helps for the background color to be black in case the image used for the
                    // watch face's background takes some time to load.
                    complicationDrawable.setBackgroundColorActive(Color.BLACK);
                } else {
                    // Active mode colors.
                    complicationDrawable.setBorderColorActive(primaryComplicationColor);
                    complicationDrawable.setRangedValuePrimaryColorActive(primaryComplicationColor);

                    // Ambient mode colors.
                    complicationDrawable.setBorderColorAmbient(Color.WHITE);
                    complicationDrawable.setRangedValuePrimaryColorAmbient(Color.WHITE);
                }
            }
        }

        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            String onTapCommand = "onTapCommand, tapType = " + tapType + ", x = " + x + ", y = "
                    + y + ", eventTime = " + eventTime;
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    Log.d(TAG, onTapCommand + ", touch");
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    Log.d(TAG, onTapCommand + ", touch cancel");
                    break;
                case TAP_TYPE_TAP:
                    Log.d(TAG, onTapCommand + ", tap");
                    if (x >= 290 && x <= 330 && y >= 400 && y <= 4500) {
                        Intent intent = new Intent("com.zhipu.watchface.CONFIG_COMPLICATION_SIMPLE");
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } else {
                        // If your background complication is the first item in your array, you need
                        // to walk backward through the array to make sure the tap isn't for a
                        // complication above the background complication.
                        for (int i = COMPLICATION_IDS.length - 1; i >= 0; i--) {
                            int complicationId = COMPLICATION_IDS[i];
                            ComplicationDrawable complicationDrawable =
                                    mComplicationDrawableSparseArray.get(complicationId);

                            boolean successfulTap = complicationDrawable.onTap(x, y);

                            if (successfulTap) {
                                return;
                            }
                        }
                    }
                    break;
                default:
                    break;
            }
            Log.d(TAG, onTapCommand);
            invalidate();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            Log.d(TAG, "onDestroy");
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
        }

        private String addZero(int number) {
            StringBuilder sBuilder = new StringBuilder(String.valueOf(number));
            if (sBuilder.length() < 2) {
                sBuilder.insert(0, "0");
            }
            return sBuilder.toString();
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

            mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bg);
            /* Extracts colors from background image to improve watchface style. */
            Palette.from(mBackgroundBitmap).generate(new Palette.PaletteAsyncListener() {
                @Override
                public void onGenerated(Palette palette) {
                    if (palette != null) {
                        /*mWatchHandHighlightColor = palette.getVibrantColor(Color.RED);
                        mWatchHandColor = palette.getLightVibrantColor(Color.WHITE);
                        mWatchHandShadowColor = palette.getDarkMutedColor(Color.BLACK);
                        updateWatchHandStyle();*/
                    }
                }
            });

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

        private void registerBatteryReceiver() {
            if (mRegisteredBatteryReceiver) {
                return;
            }
            mRegisteredBatteryReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            ZhipuWatchFace.this.registerReceiver(mBatteryReceiver, filter);
        }

        private void unregisterBatteryReceiver() {
            if (!mRegisteredBatteryReceiver) {
                return;
            }
            mRegisteredBatteryReceiver = false;
            ZhipuWatchFace.this.unregisterReceiver(mBatteryReceiver);
        }

        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (this.shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        private void handleUpdateTimeMessage() {
            invalidate();
            if (this.shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                /*Log.d(TAG, "handleUpdateTimeMessage, timeMs = " + timeMs
                        + ", delayMs = " + delayMs + ", INTERACTIVE_UPDATE_RATE_MS = " + INTERACTIVE_UPDATE_RATE_MS);*/
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        private boolean shouldTimerBeRunning() {
            return isVisible() && !mInAmbientMode;
        }

        private void setComplicationDrawable(Rect rect, ComplicationDrawable complicationDrawable) {
            complicationDrawable.setBounds(rect);
            complicationDrawable.setBorderColorActive(Color.YELLOW);
            complicationDrawable.setTitleColorActive(Color.MAGENTA);//洋红色
            complicationDrawable.setTextColorActive(Color.BLACK);
            complicationDrawable.setIconColorActive(Color.GREEN);
            complicationDrawable.setRangedValuePrimaryColorActive(Color.CYAN);//青色
        }
    }

}
