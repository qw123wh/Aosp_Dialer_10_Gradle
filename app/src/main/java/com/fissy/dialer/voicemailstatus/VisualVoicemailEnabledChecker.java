/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fissy.dialer.voicemailstatus;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import androidx.annotation.Nullable;

import com.fissy.dialer.database.CallLogQueryHandler;

/**
 * Helper class to check whether visual voicemail is enabled.
 *
 * <p>Call isVisualVoicemailEnabled() to retrieve the result.
 *
 * <p>The result is cached and saved in a SharedPreferences, stored as a boolean in
 * PREF_KEY_HAS_ACTIVE_VOICEMAIL_PROVIDER. Every time a new instance is created, it will try to
 * restore the cached result from the SharedPreferences.
 *
 * <p>Call asyncUpdate() to make a CallLogQuery to check the actual status. This is a async call so
 * isVisualVoicemailEnabled() will not be affected immediately.
 *
 * <p>If the status has changed as a result of asyncUpdate(),
 * Callback.onVisualVoicemailEnabledStatusChanged() will be called with the new value.
 */
public class VisualVoicemailEnabledChecker implements CallLogQueryHandler.Listener {

    public static final String PREF_KEY_HAS_ACTIVE_VOICEMAIL_PROVIDER =
            "has_active_voicemail_provider";
    private final SharedPreferences prefs;
    private final Context context;
    private final Callback callback;
    private boolean hasActiveVoicemailProvider;
    private CallLogQueryHandler callLogQueryHandler;

    public VisualVoicemailEnabledChecker(Context context, @Nullable Callback callback) {
        this.context = context;
        this.callback = callback;
        prefs = PreferenceManager.getDefaultSharedPreferences(this.context);
        hasActiveVoicemailProvider = prefs.getBoolean(PREF_KEY_HAS_ACTIVE_VOICEMAIL_PROVIDER, false);
    }

    /**
     * @return whether visual voicemail is enabled. Result is cached, call asyncUpdate() to update the
     * result.
     */
    public boolean isVisualVoicemailEnabled() {
        return hasActiveVoicemailProvider;
    }

    /**
     * Perform an async query into the system to check the status of visual voicemail. If the status
     * has changed, Callback.onVisualVoicemailEnabledStatusChanged() will be called.
     */
    public void asyncUpdate() {
        callLogQueryHandler = new CallLogQueryHandler(context, context.getContentResolver(), this);
        callLogQueryHandler.fetchVoicemailStatus();
    }

    @Override
    public void onVoicemailStatusFetched(Cursor statusCursor) {
        boolean hasActiveVoicemailProvider =
                VoicemailStatusHelper.getNumberActivityVoicemailSources(statusCursor) > 0;
        if (hasActiveVoicemailProvider != this.hasActiveVoicemailProvider) {
            this.hasActiveVoicemailProvider = hasActiveVoicemailProvider;
            prefs
                    .edit()
                    .putBoolean(PREF_KEY_HAS_ACTIVE_VOICEMAIL_PROVIDER, this.hasActiveVoicemailProvider)
                    .apply();
            if (callback != null) {
                callback.onVisualVoicemailEnabledStatusChanged(this.hasActiveVoicemailProvider);
            }
        }
    }

    @Override
    public void onVoicemailUnreadCountFetched(Cursor cursor) {
        // Do nothing
    }

    @Override
    public void onMissedCallsUnreadCountFetched(Cursor cursor) {
        // Do nothing
    }

    @Override
    public boolean onCallsFetched(Cursor combinedCursor) {
        // Do nothing
        return false;
    }

    public interface Callback {

        /**
         * Callback to notify enabled status has changed to the @param newValue
         */
        void onVisualVoicemailEnabledStatusChanged(boolean newValue);
    }
}
