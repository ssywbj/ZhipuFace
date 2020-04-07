package com.zhipu.face;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationManager;
import android.support.wearable.complications.ComplicationProviderService;
import android.support.wearable.complications.ComplicationText;
import android.util.Log;

import java.util.Locale;

/**
 * Example Watch Face Complication data provider provides a number that can be incremented on tap.
 */
public class DataService extends ComplicationProviderService {
    static final String TAG = ZhipuWatchFace.TAG;

    /*
     * Called when a complication has been activated. The method is for any one-time
     * (per complication) set-up.
     *
     * You can continue sending data for the active complicationId until onComplicationDeactivated()
     * is called.
     */
    @Override
    public void onComplicationActivated(int complicationId, int dataType, ComplicationManager manager) {
        super.onComplicationActivated(complicationId, dataType, manager);
        Log.d(TAG, "onComplicationActivated() id: " + complicationId + ", type: " + dataType);
    }

    /*
     * Called when the complication needs updated data from your provider. There are four scenarios
     * when this will happen:
     *
     *   1. An active watch face complication is changed to use this provider
     *   2. A complication using this provider becomes active
     *   3. The period of time you specified in the manifest has elapsed (UPDATE_PERIOD_SECONDS)
     *   4. You triggered an update from your own class via the
     *       ProviderUpdateRequester.requestUpdate() method.
     */
    @Override
    public void onComplicationUpdate(int complicationId, int dataType, ComplicationManager complicationManager) {
        Log.d(TAG, "onComplicationUpdate() id: " + complicationId + ", type: " + dataType);

        // Create Tap Action so that the user can trigger an update by tapping the complication.
        // Used to create a unique key to use with SharedPreferences for this complication.
        ComponentName thisProvider = new ComponentName(this, getClass());
        // We pass the complication id, so we can only update the specific complication tapped.
        PendingIntent complicationTogglePendingIntent =
                ComplicationToggleReceiver.getToggleIntent(this, thisProvider, complicationId);

        // Retrieves your data, in this case, we grab an incrementing number from SharedPrefs.
        SharedPreferences preferences = getSharedPreferences(
                ComplicationToggleReceiver.COMPLICATION_PROVIDER_PREFERENCES_FILE_KEY, 0);
        int number = preferences.getInt(
                ComplicationToggleReceiver.getPreferenceKey(thisProvider, complicationId), 0);
        String numberText = String.format(Locale.getDefault(), "%d!", number);

        ComplicationData complicationData = null;
        switch (dataType) {
            case ComplicationData.TYPE_RANGED_VALUE:
                complicationData = new ComplicationData.Builder(ComplicationData.TYPE_RANGED_VALUE)
                        .setValue(number).setMinValue(0).setMaxValue(ComplicationToggleReceiver.MAX_NUMBER)
                        .setShortText(ComplicationText.plainText(numberText))
                        .setTapAction(complicationTogglePendingIntent).build();
                break;
            case ComplicationData.TYPE_SHORT_TEXT:
                complicationData = new ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                        .setShortText(ComplicationText.plainText(numberText))
                        .setTapAction(complicationTogglePendingIntent).build();
                break;
            case ComplicationData.TYPE_LONG_TEXT:
                complicationData = new ComplicationData.Builder(ComplicationData.TYPE_LONG_TEXT)
                        .setLongText(ComplicationText.plainText("Number: " + numberText))
                        .setTapAction(complicationTogglePendingIntent).build();
                break;
            default:
                if (Log.isLoggable(TAG, Log.WARN)) {
                    Log.w(TAG, "Unexpected complication type " + dataType);
                }
        }

        if (complicationData != null) {
            complicationManager.updateComplicationData(complicationId, complicationData);
        } else {
            // If no data is sent, we still need to inform the ComplicationManager, so
            // the update job can finish and the wake lock isn't held any longer.
            complicationManager.noUpdateRequired(complicationId);
        }
    }
}


