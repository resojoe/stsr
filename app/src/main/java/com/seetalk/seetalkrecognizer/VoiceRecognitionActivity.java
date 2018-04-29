package com.seetalk.seetalkrecognizer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.ArrayList;

public class VoiceRecognitionActivity extends AppCompatActivity implements
        RecognitionListener {

    private static final int REQUEST_RECORD_PERMISSION = 100;
    private TextView returnedText;
    private ToggleButton toggleButton;
    private Button button;
    private ProgressBar progressBar;
    private SpeechRecognizer speech = null;
    private Intent recognizerIntent;
    private String LOG_TAG = "VoiceRecognitionActivity";
    private int partLen = 0;
    private int retTextPos = 0;
    private String partStr = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        returnedText = (TextView) findViewById(R.id.textView1);
        returnedText.setMovementMethod(new ScrollingMovementMethod());
        returnedText.setText("*** SeeTalk Begins ***\n");
        retTextPos = returnedText.length();
        progressBar = (ProgressBar) findViewById(R.id.progressBar1);
        button = (Button) findViewById(R.id.clearButton);
        //toggleButton = (ToggleButton) findViewById(R.id.toggleButton1);


        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(false);
        progressBar.setMax(10);
        speech = SpeechRecognizer.createSpeechRecognizer(this);
        Log.i(LOG_TAG, "isRecognitionAvailable: " + SpeechRecognizer.isRecognitionAvailable(this));
        speech.setRecognitionListener(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                "en");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 500);
        //recognizerIntent.putExtra("android.speech.extra.DICTATION_MODE", true);
        ActivityCompat.requestPermissions
                (VoiceRecognitionActivity.this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        REQUEST_RECORD_PERMISSION);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                returnedText.setText("==> buffer cleared <==\n");
                retTextPos = returnedText.length();
            }

        });
        mute();
/*
        toggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                if (isChecked) {
                    //progressBar.setVisibility(View.VISIBLE);
                    //progressBar.setIndeterminate(true);
                    if (speech == null) {
                        ActivityCompat.requestPermissions
                                (VoiceRecognitionActivity.this,
                                        new String[]{Manifest.permission.RECORD_AUDIO},
                                        REQUEST_RECORD_PERMISSION);
                    }
                    else
                    {
                        speech.startListening(recognizerIntent);
                    }
                } else {
                    //progressBar.setIndeterminate(false);
                    //progressBar.setVisibility(View.INVISIBLE);
                    speech.stopListening();
                }
            }
        });
*/
    }

    private void mute() {
        //mute audio
        AudioManager amanager = (AudioManager) getSystemService(android.content.Context.AUDIO_SERVICE);
        amanager.setStreamMute(AudioManager.STREAM_NOTIFICATION, true);
        amanager.setStreamMute(AudioManager.STREAM_MUSIC, true);
    }

    private void unmute() {
        //unmute audio
        AudioManager amanager = (AudioManager) getSystemService(android.content.Context.AUDIO_SERVICE);
        amanager.setStreamMute(AudioManager.STREAM_NOTIFICATION, false);
        amanager.setStreamMute(AudioManager.STREAM_MUSIC, false);
    }

    private void resetRecognizer() {
        speech.cancel();
        speech.destroy();
        speech = SpeechRecognizer.createSpeechRecognizer(this);
        if (null != speech)
        {
            speech.setRecognitionListener(this);
            speech.startListening(recognizerIntent);
        }
        else
        {
            returnedText.append("\n*** Recognizer Unavailable ***\n");
        }
    }

    private void listen()
    {
        if (null != speech)
        {
            speech.startListening(recognizerIntent);
        }
    }

    private void unlisten()
    {
        if (null != speech)
        {
            speech.stopListening();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_RECORD_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    speech.startListening(recognizerIntent);
                } else {
                    Toast.makeText(VoiceRecognitionActivity.this, "Permission Denied!", Toast
                            .LENGTH_SHORT).show();
                }
        }
    }

    @Override
    public void onResume() {
        speech = SpeechRecognizer.createSpeechRecognizer(this);
        if (null != speech)
        {
            speech.setRecognitionListener(this);
        }
        super.onResume();
        //toggleButton.setChecked(true);
        mute();
        listen();
    }

    @Override
    protected void onPause() {
        //toggleButton.setChecked(false);
        unlisten();
        if (null != speech)
        {
            speech.destroy();
            speech = null;
        }
        unmute();
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (speech != null) {
            speech.destroy();
            Log.i(LOG_TAG, "destroy");
        }
        unmute();
    }


    @Override
    public void onBeginningOfSpeech() {
        Log.i(LOG_TAG, "onBeginningOfSpeech");
        //progressBar.setIndeterminate(false);
        //progressBar.setMax(10);
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
        Log.i(LOG_TAG, "onBufferReceived: " + buffer);
    }

    @Override
    public void onEndOfSpeech() {
        Log.i(LOG_TAG, "onEndOfSpeech");
        //progressBar.setIndeterminate(true);
        unlisten();
        //toggleButton.setChecked(false);
    }

    @Override
    public void onError(int errorCode) {
        Log.i(LOG_TAG, "onError " + errorCode);
        switch (errorCode) {
            case SpeechRecognizer.ERROR_CLIENT:
            case SpeechRecognizer.ERROR_NO_MATCH:
                listen();
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                resetRecognizer();
                break;
            default:
                String errorMessage = getErrorText(errorCode);
                String text = "";
                text = "=====> " + errorMessage + "\n";
                Log.d(LOG_TAG, "FAILED " + errorMessage);
                returnedText.append(text);
                //toggleButton.setChecked(false);
                listen();
                break;
        }
    }

    @Override
    public void onEvent(int arg0, Bundle arg1) {
        Log.i(LOG_TAG, "onEvent");
    }

    @Override
    public void onPartialResults(Bundle arg0) {
        //Log.i(LOG_TAG, "onPartialResults");
        //onResults(arg0);
        String text;
        int curLen;
        ArrayList<String> matches = arg0
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        text = matches.get(0);
        //Log.i(LOG_TAG, "onPartialResults " + text);
        curLen = text.length();
        if ( curLen > 0)
        {
            if (curLen > partLen)
            {
                text = text.substring(partLen, curLen);
                returnedText.append(text);
                partStr = partStr + text;
//                Log.i(LOG_TAG, "onPartialResults " + text + " " + partStr);
                partLen = curLen;
            }
        }
    }

    @Override
    public void onReadyForSpeech(Bundle arg0) {
        Log.i(LOG_TAG, "onReadyForSpeech");
    }

    @Override
    public void onResults(Bundle results) {

        String text = "";
        String sep = "\n----\n";
        String tag = "\n++++\n";
        ArrayList<String> matches = results
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        text = matches.get(0);
        //Log.i(LOG_TAG, "onResults" + "\n>" + partStr + "<\n" + ">" + text + "<\n");
        if (false == partStr.equals(text)) {
            text = tag + text;
            returnedText.append(text);
        }

        returnedText.append(sep);
        partLen = 0;
        partStr = "";
        retTextPos = returnedText.length();
        listen();
    }

    @Override
    public void onRmsChanged(float rmsdB) {
//        Log.i(LOG_TAG, "onRmsChanged: " + rmsdB);
        progressBar.setProgress((int) rmsdB);
    }

    public static String getErrorText(int errorCode) {
        String message;
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                message = "Audio recording error";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                message = "Client side error";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = "Insufficient permissions";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                message = "Network error";
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                message = "Network timeout";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                message = "No match";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                message = "RecognitionService busy";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                message = "error from server";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                message = "No speech input";
                break;
            default:
                message = "Didn't understand, please try again.";
                break;
        }
        return message;
    }
}
