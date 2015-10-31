/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.cellbroadcastreceiver;

import android.content.Context;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.UserManager;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.util.Log;
import android.view.View;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabContentFactory;
import android.widget.TabHost.TabSpec;
import android.widget.TabWidget;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings activity for the cell broadcast receiver.
 */
public class CellBroadcastSettings extends PreferenceActivity {

    // Preference key for whether to enable emergency notifications (default enabled).
    public static final String KEY_ENABLE_EMERGENCY_ALERTS = "enable_emergency_alerts";

    // Duration of alert sound (in seconds).
    public static final String KEY_ALERT_SOUND_DURATION = "alert_sound_duration";

    // Default alert duration (in seconds).
    public static final String ALERT_SOUND_DEFAULT_DURATION = "4";

    // Enable vibration on alert (unless master volume is silent).
    public static final String KEY_ENABLE_ALERT_VIBRATE = "enable_alert_vibrate";

    // Speak contents of alert after playing the alert sound.
    public static final String KEY_ENABLE_ALERT_SPEECH = "enable_alert_speech";

    // Preference category for emergency alert and CMAS settings.
    public static final String KEY_CATEGORY_ALERT_SETTINGS = "category_alert_settings";

    // Preference category for ETWS related settings.
    public static final String KEY_CATEGORY_ETWS_SETTINGS = "category_etws_settings";

    // Whether to display CMAS extreme threat notifications (default is enabled).
    public static final String KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS =
            "enable_cmas_extreme_threat_alerts";

    // Whether to display CMAS severe threat notifications (default is enabled).
    public static final String KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS =
            "enable_cmas_severe_threat_alerts";

    // Whether to display CMAS amber alert messages (default is enabled).
    public static final String KEY_ENABLE_CMAS_AMBER_ALERTS = "enable_cmas_amber_alerts";

    // Preference category for development settings (enabled by settings developer options toggle).
    public static final String KEY_CATEGORY_DEV_SETTINGS = "category_dev_settings";

    // Whether to display ETWS test messages (default is disabled).
    public static final String KEY_ENABLE_ETWS_TEST_ALERTS = "enable_etws_test_alerts";

    // Whether to display CMAS monthly test messages (default is disabled).
    public static final String KEY_ENABLE_CMAS_TEST_ALERTS = "enable_cmas_test_alerts";

    // Preference category for Brazil specific settings.
    public static final String KEY_CATEGORY_BRAZIL_SETTINGS = "category_brazil_settings";

    // Preference key for whether to enable channel 50 notifications
    // Enabled by default for phones sold in Brazil, otherwise this setting may be hidden.
    public static final String KEY_ENABLE_CHANNEL_50_ALERTS = "enable_channel_50_alerts";

    // Preference key for initial opt-in/opt-out dialog.
    public static final String KEY_SHOW_CMAS_OPT_OUT_DIALOG = "show_cmas_opt_out_dialog";

    // Alert reminder interval ("once" = single 2 minute reminder).
    public static final String KEY_ALERT_REMINDER_INTERVAL = "alert_reminder_interval";

    // Default reminder interval.
    public static final String ALERT_REMINDER_INTERVAL = "0";

    private final static String TAG = "CellBroadcastSettings";

    private TelephonyManager mTelephonyManager;
    private SubscriptionInfo mSir;
    private TabHost mTabHost;
    private TabWidget mTabWidget;
    private List<SubscriptionInfo> mSelectableSubInfos;

    private SwitchPreference mExtremeSwitch;
    private SwitchPreference mSevereSwitch;
    private SwitchPreference mAmberSwitch;
    private SwitchPreference mEmergencySwitch;
    private ListPreference mAlertDuration;
    private ListPreference mReminderInterval;
    private SwitchPreference mVibrateSwitch;
    private SwitchPreference mSpeechSwitch;
    private SwitchPreference mEtwsTestSwitch;
    private SwitchPreference mChannel50Switch;
    private SwitchPreference mCmasSwitch;
    private SwitchPreference mOptOutSwitch;
    private PreferenceCategory mAlertCategory;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        if (userManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_CELL_BROADCASTS)) {
            setContentView(R.layout.cell_broadcast_disallowed_preference_screen);
            return;
        }

        mTelephonyManager = (TelephonyManager) getSystemService(
                Context.TELEPHONY_SERVICE);
        mSelectableSubInfos = new ArrayList<SubscriptionInfo>();
        for (int i = 0; i < mTelephonyManager.getSimCount(); i++) {
            final SubscriptionInfo sir =
                    findRecordBySlotId(getApplicationContext(), i);
            if (sir != null) {
                mSelectableSubInfos.add(sir);
            }
        }

        addPreferencesFromResource(R.xml.preferences);
        mSir = mSelectableSubInfos.size() > 0 ? mSelectableSubInfos.get(0) : null;
        if (mSelectableSubInfos.size() > 1) {
            setContentView(com.android.internal.R.layout.common_tab_settings);

            mTabHost = (TabHost) findViewById(android.R.id.tabhost);
            mTabHost.setup();
            mTabHost.setOnTabChangedListener(mTabListener);
            mTabHost.clearAllTabs();

            for (int i = 0; i < mSelectableSubInfos.size(); i++) {
                mTabHost.addTab(buildTabSpec(String.valueOf(i),
                        String.valueOf(mSelectableSubInfos.get(i).getDisplayName())));
            }
        }
        updatePreferences();
    }

    private void updatePreferences() {

        PreferenceScreen prefScreen = getPreferenceScreen();

        if (prefScreen != null) {
            prefScreen.removeAll();
            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            mExtremeSwitch = (SwitchPreference)
                    findPreference(KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS);
            mSevereSwitch = (SwitchPreference)
                    findPreference(KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS);
            mAmberSwitch = (SwitchPreference)
                    findPreference(KEY_ENABLE_CMAS_AMBER_ALERTS);
            mEmergencySwitch = (SwitchPreference)
                    findPreference(KEY_ENABLE_EMERGENCY_ALERTS);
            mAlertDuration = (ListPreference)
                    findPreference(KEY_ALERT_SOUND_DURATION);
            mReminderInterval = (ListPreference)
                    findPreference(KEY_ALERT_REMINDER_INTERVAL);
            mVibrateSwitch = (SwitchPreference)
                    findPreference(KEY_ENABLE_ALERT_VIBRATE);
            mSpeechSwitch = (SwitchPreference)
                    findPreference(KEY_ENABLE_ALERT_SPEECH);
            mEtwsTestSwitch = (SwitchPreference)
                    findPreference(KEY_ENABLE_ETWS_TEST_ALERTS);
            mChannel50Switch = (SwitchPreference)
                    findPreference(KEY_ENABLE_CHANNEL_50_ALERTS);
            mCmasSwitch = (SwitchPreference)
                    findPreference(KEY_ENABLE_CMAS_TEST_ALERTS);
            mOptOutSwitch = (SwitchPreference)
                    findPreference(KEY_SHOW_CMAS_OPT_OUT_DIALOG);
            mAlertCategory = (PreferenceCategory)
                    findPreference(KEY_CATEGORY_ALERT_SETTINGS);

            if(mSir == null) {
                mExtremeSwitch.setEnabled(false);
                mSevereSwitch.setEnabled(false);
                mAmberSwitch.setEnabled(false);
                mEmergencySwitch.setEnabled(false);
                mReminderInterval.setEnabled(false);
                mAlertDuration.setEnabled(false);
                mVibrateSwitch.setEnabled(false);
                mSpeechSwitch.setEnabled(false);
                mEtwsTestSwitch.setEnabled(false);
                mChannel50Switch.setEnabled(false);
                mCmasSwitch.setEnabled(false);
                mOptOutSwitch.setEnabled(false);
                return;
            }

            // Handler for settings that require us to reconfigure enabled channels in radio
            Preference.OnPreferenceChangeListener startConfigServiceListener =
                    new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference pref, Object newValue) {
                            int newVal = (((Boolean) newValue).booleanValue()) ? 1 : 0;

                            switch (pref.getKey()) {
                                case KEY_ENABLE_EMERGENCY_ALERTS:
                                    SubscriptionManager
                                            .setSubscriptionProperty(mSir.getSubscriptionId(),
                                                    SubscriptionManager.CB_EMERGENCY_ALERT,
                                                    newVal + "");
                                    break;
                                case KEY_ENABLE_CHANNEL_50_ALERTS:
                                    SubscriptionManager
                                            .setSubscriptionProperty(mSir.getSubscriptionId(),
                                                    SubscriptionManager.CB_CHANNEL_50_ALERT,
                                                    newVal + "");
                                    break;
                                case KEY_ENABLE_ETWS_TEST_ALERTS:
                                    SubscriptionManager
                                            .setSubscriptionProperty(mSir.getSubscriptionId(),
                                                    SubscriptionManager.CB_ETWS_TEST_ALERT,
                                                    newVal + "");
                                    break;
                                case KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS:
                                    SubscriptionManager
                                            .setSubscriptionProperty(mSir.getSubscriptionId(),
                                                    SubscriptionManager.CB_EXTREME_THREAT_ALERT,
                                                    newVal + "");
                                    SubscriptionManager
                                            .setSubscriptionProperty(mSir.getSubscriptionId(),
                                                    SubscriptionManager.CB_SEVERE_THREAT_ALERT,
                                                    "0");

                                    boolean isExtremeAlertChecked =
                                            ((Boolean) newValue).booleanValue();

                                    if (mSevereSwitch != null) {
                                        mSevereSwitch.setEnabled(isExtremeAlertChecked);
                                        mSevereSwitch.setChecked(false);
                                    }
                                    break;
                                case KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS:
                                    SubscriptionManager
                                            .setSubscriptionProperty(mSir.getSubscriptionId(),
                                                    SubscriptionManager.CB_SEVERE_THREAT_ALERT,
                                                    newVal + "");
                                    break;
                                case KEY_ENABLE_CMAS_AMBER_ALERTS:
                                    SubscriptionManager
                                            .setSubscriptionProperty(mSir.getSubscriptionId(),
                                                    SubscriptionManager.CB_AMBER_ALERT,
                                                    newVal + "");
                                    break;
                                case KEY_ENABLE_CMAS_TEST_ALERTS:
                                    SubscriptionManager
                                            .setSubscriptionProperty(mSir.getSubscriptionId(),
                                                    SubscriptionManager.CB_CMAS_TEST_ALERT,
                                                    newVal + "");
                                    break;
                                default:
                                    Log.d(TAG, "Invalid preference changed");

                            }

                            CellBroadcastReceiver.startConfigService(pref.getContext(),
                                    mSir.getSubscriptionId());
                            return true;
                        }
                    };

            // Show extra settings when developer options is enabled in settings.
            boolean enableDevSettings = Settings.Global.getInt(getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;

            boolean showEtwsSettings = SubscriptionManager.getResourcesForSubId(
                    getApplicationContext(), mSir.getSubscriptionId())
                    .getBoolean(R.bool.show_etws_settings);

            String queryReturnVal;
            // alert reminder interval
            queryReturnVal = SubscriptionManager.getIntegerSubscriptionProperty(
                    mSir.getSubscriptionId(), SubscriptionManager.CB_ALERT_REMINDER_INTERVAL,
                    Integer.parseInt(ALERT_REMINDER_INTERVAL), this) + "";

            mReminderInterval.setValue(queryReturnVal);
            mReminderInterval.setSummary(mReminderInterval
                    .getEntries()[mReminderInterval.findIndexOfValue(queryReturnVal)]);

            mReminderInterval.setOnPreferenceChangeListener(
                    new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference pref, Object newValue) {
                            final ListPreference listPref = (ListPreference) pref;
                            final int idx = listPref.findIndexOfValue((String) newValue);
                            listPref.setSummary(listPref.getEntries()[idx]);
                            SubscriptionManager.setSubscriptionProperty(mSir.getSubscriptionId(),
                                    SubscriptionManager.CB_ALERT_REMINDER_INTERVAL,
                                    (String) newValue);
                            return true;
                        }
                    });

            boolean forceDisableEtwsCmasTest =
                    isEtwsCmasTestMessageForcedDisabled(this, mSir.getSubscriptionId());

            // Show alert settings and ETWS categories for ETWS builds and developer mode.
            if (enableDevSettings || showEtwsSettings) {
                // enable/disable all alerts
                if (mEmergencySwitch != null) {
                    if (SubscriptionManager.getBooleanSubscriptionProperty(mSir.getSubscriptionId(),
                            SubscriptionManager.CB_EMERGENCY_ALERT, true, this)) {
                        mEmergencySwitch.setChecked(true);
                    } else {
                        mEmergencySwitch.setChecked(false);
                    }
                    mEmergencySwitch.setOnPreferenceChangeListener(startConfigServiceListener);
                }

                // alert sound duration
                queryReturnVal = SubscriptionManager.getIntegerSubscriptionProperty(
                        mSir.getSubscriptionId(), SubscriptionManager.CB_ALERT_SOUND_DURATION,
                        Integer.parseInt(ALERT_SOUND_DEFAULT_DURATION), this) + "";
                mAlertDuration.setValue(queryReturnVal);
                mAlertDuration.setSummary(mAlertDuration
                        .getEntries()[mAlertDuration.findIndexOfValue(queryReturnVal)]);
                mAlertDuration.setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(Preference pref, Object newValue) {
                                final ListPreference listPref = (ListPreference) pref;
                                final int idx = listPref.findIndexOfValue((String) newValue);
                                listPref.setSummary(listPref.getEntries()[idx]);
                                SubscriptionManager.setSubscriptionProperty(
                                        mSir.getSubscriptionId(),
                                        SubscriptionManager.CB_ALERT_SOUND_DURATION,
                                        (String) newValue);
                                return true;
                            }
                        });
                if (forceDisableEtwsCmasTest) {
                    // Remove ETWS test preference.
                    prefScreen.removePreference(findPreference(KEY_CATEGORY_ETWS_SETTINGS));

                    PreferenceCategory devSettingCategory =
                            (PreferenceCategory) findPreference(KEY_CATEGORY_DEV_SETTINGS);

                    // Remove CMAS test preference.
                    if (devSettingCategory != null) {
                        devSettingCategory.removePreference(
                                findPreference(KEY_ENABLE_CMAS_TEST_ALERTS));
                    }
                }
            } else {
                // Remove general emergency alert preference items (not shown for CMAS builds).
                mAlertCategory.removePreference(findPreference(KEY_ENABLE_EMERGENCY_ALERTS));
                mAlertCategory.removePreference(findPreference(KEY_ALERT_SOUND_DURATION));
                mAlertCategory.removePreference(findPreference(KEY_ENABLE_ALERT_SPEECH));
                // Remove ETWS test preference category.
                prefScreen.removePreference(findPreference(KEY_CATEGORY_ETWS_SETTINGS));
            }

            if (!SubscriptionManager.getResourcesForSubId(getApplicationContext(),
                    mSir.getSubscriptionId()).getBoolean(R.bool.show_cmas_settings)) {
                // Remove CMAS preference items in emergency alert category.
                mAlertCategory.removePreference(
                        findPreference(KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS));
                mAlertCategory.removePreference(
                        findPreference(KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS));
                mAlertCategory.removePreference(findPreference(KEY_ENABLE_CMAS_AMBER_ALERTS));
            }

            boolean enableChannel50Support = SubscriptionManager.getResourcesForSubId(
                    getApplicationContext(), mSir.getSubscriptionId()).getBoolean(
                    R.bool.show_brazil_settings)
                    || "br".equals(mTelephonyManager.getSimCountryIso());

            if (!enableChannel50Support) {
                prefScreen.removePreference(findPreference(KEY_CATEGORY_BRAZIL_SETTINGS));
            }
            if (!enableDevSettings) {
                prefScreen.removePreference(findPreference(KEY_CATEGORY_DEV_SETTINGS));
            }

            if (mSpeechSwitch != null) {
                if (SubscriptionManager.getBooleanSubscriptionProperty(mSir.getSubscriptionId(),
                        SubscriptionManager.CB_ALERT_SPEECH, true, this)) {
                    mSpeechSwitch.setChecked(true);
                } else {
                    mSpeechSwitch.setChecked(false);
                }
                mSpeechSwitch.setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(Preference pref, Object newValue) {
                                int newVal = (((Boolean) newValue).booleanValue()) ? 1 : 0;
                                SubscriptionManager.setSubscriptionProperty(
                                        mSir.getSubscriptionId(),
                                        SubscriptionManager.CB_ALERT_SPEECH, newVal + "");
                                return true;
                            }
                        });
            }

            if (mVibrateSwitch != null) {
                if (SubscriptionManager.getBooleanSubscriptionProperty(mSir.getSubscriptionId(),
                        SubscriptionManager.CB_ALERT_VIBRATE, true, this)) {
                    mVibrateSwitch.setChecked(true);
                } else {
                    mVibrateSwitch.setChecked(false);
                }
                mVibrateSwitch.setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(Preference pref, Object newValue) {
                                int newVal = (((Boolean) newValue).booleanValue()) ? 1 : 0;
                                SubscriptionManager.setSubscriptionProperty(
                                        mSir.getSubscriptionId(),
                                        SubscriptionManager.CB_ALERT_VIBRATE, newVal + "");
                                return true;
                            }
                        });
            }

            if (mOptOutSwitch != null) {
                if (SubscriptionManager.getBooleanSubscriptionProperty(mSir.getSubscriptionId(),
                        SubscriptionManager.CB_OPT_OUT_DIALOG, true, this)) {
                    mOptOutSwitch.setChecked(true);
                } else {
                    mOptOutSwitch.setChecked(false);
                }
                mOptOutSwitch.setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(Preference pref, Object newValue) {
                                int newVal = (((Boolean) newValue).booleanValue()) ? 1 : 0;
                                SubscriptionManager.setSubscriptionProperty(
                                        mSir.getSubscriptionId(),
                                        SubscriptionManager.CB_OPT_OUT_DIALOG, newVal + "");
                                return true;
                            }
                        });
            }

            if (mChannel50Switch != null) {
                if (SubscriptionManager.getBooleanSubscriptionProperty(mSir.getSubscriptionId(),
                        SubscriptionManager.CB_CHANNEL_50_ALERT, true, this)) {
                    mChannel50Switch.setChecked(true);
                } else {
                    mChannel50Switch.setChecked(false);
                }
                mChannel50Switch.setOnPreferenceChangeListener(startConfigServiceListener);
            }

            if (mEtwsTestSwitch != null) {
                if (!forceDisableEtwsCmasTest &&
                        SubscriptionManager.getBooleanSubscriptionProperty(mSir.getSubscriptionId(),
                        SubscriptionManager.CB_ETWS_TEST_ALERT, false, this)) {
                    mEtwsTestSwitch.setChecked(true);
                } else {
                    mEtwsTestSwitch.setChecked(false);
                }
                mEtwsTestSwitch.setOnPreferenceChangeListener(startConfigServiceListener);
            }

            if (mExtremeSwitch != null) {
                if (SubscriptionManager.getBooleanSubscriptionProperty(mSir.getSubscriptionId(),
                        SubscriptionManager.CB_EXTREME_THREAT_ALERT, true, this)) {
                    mExtremeSwitch.setChecked(true);
                } else {
                    mExtremeSwitch.setChecked(false);
                }
                mExtremeSwitch.setOnPreferenceChangeListener(startConfigServiceListener);
            }

            if (mSevereSwitch != null) {
                if (SubscriptionManager.getBooleanSubscriptionProperty(mSir.getSubscriptionId(),
                        SubscriptionManager.CB_SEVERE_THREAT_ALERT, true, this)) {
                    mSevereSwitch.setChecked(true);
                } else {
                    mSevereSwitch.setChecked(false);
                }
                mSevereSwitch.setOnPreferenceChangeListener(startConfigServiceListener);
                if (mExtremeSwitch != null) {
                    boolean isExtremeAlertChecked =
                            ((SwitchPreference) mExtremeSwitch).isChecked();
                    mSevereSwitch.setEnabled(isExtremeAlertChecked);
                }
            }

            if (mAmberSwitch != null) {
                if (SubscriptionManager.getBooleanSubscriptionProperty(mSir.getSubscriptionId(),
                        SubscriptionManager.CB_AMBER_ALERT, true, this)) {
                    mAmberSwitch.setChecked(true);
                } else {
                    mAmberSwitch.setChecked(false);
                }
                mAmberSwitch.setOnPreferenceChangeListener(startConfigServiceListener);
            }

            if (mCmasSwitch != null) {
                if (!forceDisableEtwsCmasTest &&
                        SubscriptionManager.getBooleanSubscriptionProperty(mSir.getSubscriptionId(),
                        SubscriptionManager.CB_CMAS_TEST_ALERT, false, this)) {
                    mCmasSwitch.setChecked(true);
                } else {
                    mCmasSwitch.setChecked(false);
                }
                mCmasSwitch.setOnPreferenceChangeListener(startConfigServiceListener);
            }
        }
    }

    // Check if ETWS/CMAS test message is forced disabled on the device.
    public static boolean isEtwsCmasTestMessageForcedDisabled(Context context, int subId) {

        if (context == null) {
            return false;
        }

        CarrierConfigManager configManager =
                (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);

        if (configManager != null) {
            PersistableBundle carrierConfig =
                    configManager.getConfigForSubId(subId);

            if (carrierConfig != null) {
                return carrierConfig.getBoolean(
                        CarrierConfigManager.KEY_CARRIER_FORCE_DISABLE_ETWS_CMAS_TEST_BOOL);
            }
        }

        return false;
    }

    private OnTabChangeListener mTabListener = new OnTabChangeListener() {
        @Override
        public void onTabChanged(String tabId) {
            final int slotId = Integer.parseInt(tabId);
            mSir = mSelectableSubInfos.get(slotId);
            updatePreferences();
        }
    };

    private TabContentFactory mEmptyTabContent = new TabContentFactory() {
        @Override
        public View createTabContent(String tag) {
            return new View(mTabHost.getContext());
        }
    };

    private TabSpec buildTabSpec(String tag, String title) {
        return mTabHost.newTabSpec(tag).setIndicator(title).setContent(
                mEmptyTabContent);

    }

    public SubscriptionInfo findRecordBySlotId(Context context, final int slotId) {
        final List<SubscriptionInfo> subInfoList =
                SubscriptionManager.from(context).getActiveSubscriptionInfoList();
        if (subInfoList != null) {
            final int subInfoLength = subInfoList.size();

            for (int i = 0; i < subInfoLength; ++i) {
                final SubscriptionInfo sir = subInfoList.get(i);
                if (sir.getSimSlotIndex() == slotId) {
                    return sir;
                }
            }
        }

        return null;
    }

}
