// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: packages/apps/Dialer/java/com/android/voicemail/impl/transcribe/grpc/voicemail_transcription.proto

package com.google.internal.communications.voicemailtranscription.v1;

public interface TranscribeVoicemailAsyncRequestOrBuilder extends
        // @@protoc_insertion_point(interface_extends:google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailAsyncRequest)
        com.google.protobuf.MessageLiteOrBuilder {

    /**
     * <pre>
     * Voicemail audio data encoded in the format specified by audio_format.
     * </pre>
     *
     * <code>optional bytes voicemail_data = 1;</code>
     */
    boolean hasVoicemailData();

    /**
     * <pre>
     * Voicemail audio data encoded in the format specified by audio_format.
     * </pre>
     *
     * <code>optional bytes voicemail_data = 1;</code>
     */
    com.google.protobuf.ByteString getVoicemailData();

    /**
     * <pre>
     * Audio format of the voicemail file.
     * </pre>
     *
     * <code>optional .google.internal.communications.voicemailtranscription.v1.AudioFormat audio_format = 2;</code>
     */
    boolean hasAudioFormat();

    /**
     * <pre>
     * Audio format of the voicemail file.
     * </pre>
     *
     * <code>optional .google.internal.communications.voicemailtranscription.v1.AudioFormat audio_format = 2;</code>
     */
    com.google.internal.communications.voicemailtranscription.v1.AudioFormat getAudioFormat();

    /**
     * <pre>
     * The client may provide their own unique ID for this transcription. It
     * should be globally unique across all voicemails from all users.
     * If the given transcription_id is not unique, an ALREADY_EXISTS (409) error
     * will be returned.
     * If no transcription_id is provided, one will be generated by the server.
     * </pre>
     *
     * <code>optional string transcription_id = 3;</code>
     */
    boolean hasTranscriptionId();

    /**
     * <pre>
     * The client may provide their own unique ID for this transcription. It
     * should be globally unique across all voicemails from all users.
     * If the given transcription_id is not unique, an ALREADY_EXISTS (409) error
     * will be returned.
     * If no transcription_id is provided, one will be generated by the server.
     * </pre>
     *
     * <code>optional string transcription_id = 3;</code>
     */
    java.lang.String getTranscriptionId();

    /**
     * <pre>
     * The client may provide their own unique ID for this transcription. It
     * should be globally unique across all voicemails from all users.
     * If the given transcription_id is not unique, an ALREADY_EXISTS (409) error
     * will be returned.
     * If no transcription_id is provided, one will be generated by the server.
     * </pre>
     *
     * <code>optional string transcription_id = 3;</code>
     */
    com.google.protobuf.ByteString
    getTranscriptionIdBytes();

    /**
     * <pre>
     * User's donation preference.
     * </pre>
     *
     * <code>optional .google.internal.communications.voicemailtranscription.v1.DonationPreference donation_preference = 4;</code>
     */
    boolean hasDonationPreference();

    /**
     * <pre>
     * User's donation preference.
     * </pre>
     *
     * <code>optional .google.internal.communications.voicemailtranscription.v1.DonationPreference donation_preference = 4;</code>
     */
    com.google.internal.communications.voicemailtranscription.v1.DonationPreference getDonationPreference();
}
