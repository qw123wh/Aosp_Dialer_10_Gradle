/**
 * Copyright (C) 2017 The Android Open Source Project
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.android.voicemail.impl;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build.VERSION_CODES;
import android.os.PersistableBundle;
import android.preference.PreferenceManager;
import android.provider.VoicemailContract.Status;
import android.provider.VoicemailContract.Voicemails;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BuildCompat;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;

import com.android.voicemail.PinChanger;
import com.android.voicemail.VisualVoicemailTypeExtensions;
import com.android.voicemail.VoicemailClient;
import com.android.voicemail.VoicemailVersionConstants;
import com.android.voicemail.impl.configui.VoicemailSecretCodeActivity;
import com.android.voicemail.impl.settings.VisualVoicemailSettingsUtil;
import com.android.voicemail.impl.sync.VvmAccountManager;
import com.android.voicemail.impl.transcribe.TranscriptionBackfillService;
import com.android.voicemail.impl.transcribe.TranscriptionConfigProvider;
import com.fissy.dialer.common.Assert;
import com.fissy.dialer.common.LogUtil;
import com.fissy.dialer.configprovider.ConfigProviderComponent;

import java.util.List;

import javax.inject.Inject;

/**
 * {@link VoicemailClient} to be used when the voicemail module is activated. May only be used above
 * O.
 */
public class VoicemailClientImpl implements VoicemailClient {

    /**
     * List of legacy OMTP voicemail packages that should be ignored. It could never be the active VVM
     * package anymore. For example, voicemails in OC will no longer be handled by telephony, but
     * legacy voicemails might still exist in the database due to upgrading from NYC. Dialer will
     * fetch these voicemails again so it should be ignored.
     */
    private static final String[] OMTP_VOICEMAIL_BLACKLIST = {"com.android.phone"};

    // Flag name used for configuration
    private static final String ALLOW_VOICEMAIL_ARCHIVE = "allow_voicemail_archive";

    private static final String[] OMTP_VOICEMAIL_TYPE = {
            TelephonyManager.VVM_TYPE_OMTP,
            TelephonyManager.VVM_TYPE_CVVM,
            VisualVoicemailTypeExtensions.VVM_TYPE_VVM3
    };

    @Inject
    public VoicemailClientImpl() {
        Assert.checkArgument(BuildCompat.isAtLeastO());
    }

    @Override
    public boolean isVoicemailModuleEnabled() {
        return true;
    }

    @Override
    public boolean hasCarrierSupport(Context context, PhoneAccountHandle phoneAccountHandle) {
        OmtpVvmCarrierConfigHelper config = new OmtpVvmCarrierConfigHelper(context, phoneAccountHandle);
        return config.isValid() && !config.isCarrierAppInstalled();
    }

    @Override
    public boolean isVoicemailEnabled(Context context, PhoneAccountHandle phoneAccountHandle) {
        return VisualVoicemailSettingsUtil.isEnabled(context, phoneAccountHandle);
    }

    @Override
    public void setVoicemailEnabled(
            Context context, PhoneAccountHandle phoneAccountHandle, boolean enabled) {
        VisualVoicemailSettingsUtil.setEnabled(context, phoneAccountHandle, enabled);
    }

    @Override
    public boolean isVoicemailArchiveEnabled(Context context, PhoneAccountHandle phoneAccountHandle) {
        return VisualVoicemailSettingsUtil.isArchiveEnabled(context, phoneAccountHandle);
    }

    @Override
    public boolean isVoicemailArchiveAvailable(Context context) {
        if (!BuildCompat.isAtLeastO()) {
            LogUtil.i("VoicemailClientImpl.isVoicemailArchiveAllowed", "not running on O or later");
            return false;
        }

        if (!ConfigProviderComponent.get(context)
                .getConfigProvider()
                .getBoolean(ALLOW_VOICEMAIL_ARCHIVE, false)) {
            LogUtil.i(
                    "VoicemailClientImpl.isVoicemailArchiveAllowed",
                    "feature disabled by config: %s",
                    ALLOW_VOICEMAIL_ARCHIVE);
            return false;
        }

        return true;
    }

    @Override
    public void setVoicemailArchiveEnabled(
            Context context, PhoneAccountHandle phoneAccountHandle, boolean value) {
        VisualVoicemailSettingsUtil.setArchiveEnabled(context, phoneAccountHandle, value);
    }

    @Override
    public boolean isVoicemailTranscriptionAvailable(
            Context context, PhoneAccountHandle phoneAccountHandle) {
        if (phoneAccountHandle == null) {
            LogUtil.i(
                    "VoicemailClientImpl.isVoicemailTranscriptionAvailable", "phone account handle is null");
        }

        if (!BuildCompat.isAtLeastO()) {
            LogUtil.i(
                    "VoicemailClientImpl.isVoicemailTranscriptionAvailable", "not running on O or later");
            return false;
        }

        if (!isVoicemailEnabled(context, phoneAccountHandle)) {
            LogUtil.i(
                    "VoicemailClientImpl.isVoicemailTranscriptionAvailable",
                    "visual voicemail is not enabled");
            return false;
        }

        if (!isActivated(context, phoneAccountHandle)) {
            LogUtil.i(
                    "VoicemailClientImpl.isVoicemailTranscriptionAvailable",
                    "visual voicemail is not activated");
            return false;
        }

        TranscriptionConfigProvider provider = new TranscriptionConfigProvider(context);
        if (!provider.isVoicemailTranscriptionAvailable()) {
            LogUtil.i(
                    "VoicemailClientImpl.isVoicemailTranscriptionAvailable", "feature disabled by config");
            return false;
        }

        return true;
    }

    @Override
    public boolean isVoicemailTranscriptionEnabled(Context context, PhoneAccountHandle account) {
        return isVoicemailTranscriptionAvailable(context, account)
                && VisualVoicemailSettingsUtil.isVoicemailTranscriptionEnabled(context, account);
    }

    @Override
    public boolean isVoicemailDonationAvailable(
            Context context, PhoneAccountHandle phoneAccountHandle) {
        if (!isVoicemailTranscriptionAvailable(context, phoneAccountHandle)) {
            LogUtil.i("VoicemailClientImpl.isVoicemailDonationAvailable", "transcription not available");
            return false;
        }

        if (!isVoicemailTranscriptionEnabled(context, phoneAccountHandle)) {
            LogUtil.i("VoicemailClientImpl.isVoicemailDonationAvailable", "transcription not enabled");
            return false;
        }

        TranscriptionConfigProvider provider = new TranscriptionConfigProvider(context);
        if (!provider.isVoicemailDonationAvailable()) {
            LogUtil.i("VoicemailClientImpl.isVoicemailDonationAvailable", "feature disabled by config");
            return false;
        }

        return true;
    }

    @Override
    public boolean isVoicemailDonationEnabled(Context context, PhoneAccountHandle account) {
        return isVoicemailTranscriptionEnabled(context, account)
                && isVoicemailDonationAvailable(context, account)
                && VisualVoicemailSettingsUtil.isVoicemailDonationEnabled(context, account);
    }

    @Override
    public void setVoicemailTranscriptionEnabled(
            Context context, PhoneAccountHandle phoneAccountHandle, boolean enabled) {
        Assert.checkArgument(
                isVoicemailTranscriptionAvailable(context, phoneAccountHandle),
                "transcription must be available before enabling/disabling it");
        VisualVoicemailSettingsUtil.setVoicemailTranscriptionEnabled(
                context, phoneAccountHandle, enabled);
        if (enabled) {
            TranscriptionBackfillService.scheduleTask(context, phoneAccountHandle);
        }
    }

    @Override
    public void setVoicemailDonationEnabled(
            Context context, PhoneAccountHandle phoneAccountHandle, boolean enabled) {
        if (enabled) {
            Assert.checkArgument(
                    isVoicemailTranscriptionAvailable(context, phoneAccountHandle)
                            && isVoicemailTranscriptionEnabled(context, phoneAccountHandle),
                    "should not be able to enable donation without transcription "
                            + "available(value: %b) and enabled (value:%b) for account:%s",
                    isVoicemailTranscriptionAvailable(context, phoneAccountHandle),
                    isVoicemailTranscriptionEnabled(context, phoneAccountHandle),
                    phoneAccountHandle.toString());
        }
        VisualVoicemailSettingsUtil.setVoicemailDonationEnabled(context, phoneAccountHandle, enabled);
    }

    @Override
    public boolean isActivated(Context context, PhoneAccountHandle phoneAccountHandle) {
        return VvmAccountManager.isAccountActivated(context, phoneAccountHandle);
    }

    @Override
    public void showConfigUi(@NonNull Context context) {
        Intent intent = new Intent(context, VoicemailSecretCodeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    public PersistableBundle getConfig(Context context, PhoneAccountHandle phoneAccountHandle) {
        return new OmtpVvmCarrierConfigHelper(context, phoneAccountHandle).getConfig();
    }

    @Override
    @MainThread
    public void onBoot(@NonNull Context context) {
        OmtpService.onBoot(context);
        StatusCheckJobService.schedule(context);
    }

    @Override
    @MainThread
    public void onShutdown(@NonNull Context context) {
        OmtpService.onShutdown(context);
    }

    @Override
    public void addActivationStateListener(ActivationStateListener listener) {
        VvmAccountManager.addListener(listener);
    }

    @Override
    public void removeActivationStateListener(ActivationStateListener listener) {
        VvmAccountManager.removeListener(listener);
    }

    @Override
    public PinChanger createPinChanger(Context context, PhoneAccountHandle phoneAccountHandle) {
        return new PinChangerImpl(context, phoneAccountHandle);
    }

    @TargetApi(VERSION_CODES.O)
    @Override
    public void appendOmtpVoicemailSelectionClause(
            Context context, StringBuilder where, List<String> selectionArgs) {
        String omtpSource =
                context.getSystemService(TelephonyManager.class).getVisualVoicemailPackageName();
        if (where.length() != 0) {
            where.append(" AND ");
        }
        where.append("(");
        {
            where.append("(");
            {
                where.append(Voicemails.IS_OMTP_VOICEMAIL).append(" != 1");
                where.append(")");
            }
            where.append(" OR ");
            where.append("(");
            {
                where.append(Voicemails.SOURCE_PACKAGE).append(" = ?");
                selectionArgs.add(omtpSource);
                where.append(")");
            }
            where.append(")");
        }

        for (String blacklistedPackage : OMTP_VOICEMAIL_BLACKLIST) {
            where.append("AND (").append(Voicemails.SOURCE_PACKAGE).append("!= ?)");
            selectionArgs.add(blacklistedPackage);
        }
    }

    @TargetApi(VERSION_CODES.O)
    @Override
    public void appendOmtpVoicemailStatusSelectionClause(
            Context context, StringBuilder where, List<String> selectionArgs) {
        String omtpSource =
                context.getSystemService(TelephonyManager.class).getVisualVoicemailPackageName();
        if (where.length() != 0) {
            where.append(" AND ");
        }
        where.append("(");
        {
            where.append("(");
            {
                where.append(Status.SOURCE_PACKAGE).append(" = ? ");
                selectionArgs.add(omtpSource);
                where.append(")");
            }
            where.append(" OR NOT (");
            {
                for (int i = 0; i < OMTP_VOICEMAIL_TYPE.length; i++) {
                    if (i != 0) {
                        where.append(" OR ");
                    }
                    where.append(" (");
                    {
                        where.append(Status.SOURCE_TYPE).append(" IS ?");
                        selectionArgs.add(OMTP_VOICEMAIL_TYPE[i]);
                        where.append(")");
                    }
                }
                where.append(")");
            }
            for (String blacklistedPackage : OMTP_VOICEMAIL_BLACKLIST) {
                where.append("AND (");
                {
                    where.append(Voicemails.SOURCE_PACKAGE).append("!= ?");
                    selectionArgs.add(blacklistedPackage);
                    where.append(")");
                }
            }
            where.append(")");
        }
    }

    @Override
    public void onTosAccepted(Context context, PhoneAccountHandle account) {
        LogUtil.i("VoicemailClientImpl.onTosAccepted", "try backfilling voicemail transcriptions");
        TranscriptionBackfillService.scheduleTask(context, account);
    }

    @Override
    public boolean hasAcceptedTos(Context context, PhoneAccountHandle phoneAccountHandle) {
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        OmtpVvmCarrierConfigHelper helper = new OmtpVvmCarrierConfigHelper(context, phoneAccountHandle);
        boolean isVvm3 = VisualVoicemailTypeExtensions.VVM_TYPE_VVM3.equals(helper.getVvmType());
        if (isVvm3) {
            return preferences.getInt(VoicemailVersionConstants.PREF_VVM3_TOS_VERSION_ACCEPTED_KEY, 0)
                    >= VoicemailVersionConstants.CURRENT_VVM3_TOS_VERSION;
        } else {
            return preferences.getInt(VoicemailVersionConstants.PREF_DIALER_TOS_VERSION_ACCEPTED_KEY, 0)
                    >= VoicemailVersionConstants.CURRENT_DIALER_TOS_VERSION;
        }
    }

    @Override
    @Nullable
    public String getCarrierConfigString(Context context, PhoneAccountHandle account, String key) {
        OmtpVvmCarrierConfigHelper helper = new OmtpVvmCarrierConfigHelper(context, account);
        return helper.isValid() ? helper.getString(key) : null;
    }
}
