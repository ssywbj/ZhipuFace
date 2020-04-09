package com.zhipu.face.config;

import android.app.Activity;
import android.os.Bundle;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.wear.widget.WearableRecyclerView;

import com.zhipu.face.R;
import com.zhipu.face.model.AnalogComplicationConfigData;

/**
 * Allows user to select color for something on the watch face (background, highlight,etc.) and
 * saves it to {@link android.content.SharedPreferences} in RecyclerView.Adapter.
 */
public class ColorSelectionActivity extends Activity {

    private static final String TAG = ColorSelectionActivity.class.getSimpleName();

    static final String EXTRA_SHARED_PREF =
            "com.example.android.wearable.watchface.config.extra.EXTRA_SHARED_PREF";

    private WearableRecyclerView mConfigAppearanceWearableRecyclerView;

    private ColorSelectionRecyclerViewAdapter mColorSelectionRecyclerViewAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_color_selection_config);

        // Assigns SharedPreference String used to save color selected.
        String sharedPrefString = getIntent().getStringExtra(EXTRA_SHARED_PREF);

        mColorSelectionRecyclerViewAdapter = new ColorSelectionRecyclerViewAdapter(
                sharedPrefString,
                AnalogComplicationConfigData.getColorOptionsDataSet());

        mConfigAppearanceWearableRecyclerView =
                (WearableRecyclerView) findViewById(R.id.wearable_recycler_view);

        // Aligns the first and last items on the list vertically centered on the screen.
        mConfigAppearanceWearableRecyclerView.setEdgeItemsCenteringEnabled(true);

        mConfigAppearanceWearableRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Improves performance because we know changes in content do not change the layout size of
        // the RecyclerView.
        mConfigAppearanceWearableRecyclerView.setHasFixedSize(true);

        mConfigAppearanceWearableRecyclerView.setAdapter(mColorSelectionRecyclerViewAdapter);
    }
}