package com.seetalk.seetalk;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.Locale;

import static android.app.PendingIntent.getActivity;

public class VoiceRecognitionActivity extends AppCompatActivity implements
        RecognitionListener {

    private static final int REQUEST_RECORD_PERMISSION = 100;
    private TextView returnedText;
    private ToggleButton togglePause;
    private ProgressBar progressBar;
    private SpeechRecognizer speech = null;
    private Intent recognizerIntent;
    private String LOG_TAG = "VoiceRecognitionActivity";
    private int partLen = 0;
    private int retTextPos = 0;
    private String partStr = "";
    private int lastErr = 0;
    private SharedPreferences sharedPref;
    private String sizeStr = "textSize";
    private String langStr = "prefLang";
    private String defLangStr = "en_US";
    private String langPref;
    private boolean isPaused = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Button clrButton;
        Button rstButton;
        float defaultTextSize;
        float savedTextSize;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // set our textView to scroll and output starting text
        returnedText = (TextView) findViewById(R.id.textView1);
        returnedText.setMovementMethod(new ScrollingMovementMethod());
        returnedText.setText("*** SeeTalk Begins ***\n");
        retTextPos = returnedText.length();

        clrButton = (Button) findViewById(R.id.clearButton);
        rstButton = (Button) findViewById(R.id.resetButton);
        togglePause = (ToggleButton) findViewById(R.id.togglePause);

        // load stored settings if they exist
        sharedPref = getPreferences(Context.MODE_PRIVATE);
        defaultTextSize = returnedText.getTextSize();
        savedTextSize = sharedPref.getFloat(sizeStr, defaultTextSize);
        if (savedTextSize != defaultTextSize) {
            returnedText.setTextSize(TypedValue.COMPLEX_UNIT_PX, savedTextSize);
        }
        langPref = sharedPref.getString(langStr, defLangStr);

        //configure the progress bar input indicator
        progressBar = (ProgressBar) findViewById(R.id.progressBar1);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(false);
        progressBar.setMax(10);

        // allocate our first speech recognizer
        speech = SpeechRecognizer.createSpeechRecognizer(this);
        Log.i(LOG_TAG, "isRecognitionAvailable: " + SpeechRecognizer.isRecognitionAvailable(this));
        speech.setRecognitionListener(this);

        // allocate and configure the recognizerIntent
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,langPref);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langPref);
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

        // define behaviors for button presses
        clrButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                returnedText.setText("==> buffer cleared <==\n");
                retTextPos = returnedText.length();
            }

        });
        rstButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                returnedText.append("==> reset speech <==\n");
                retTextPos = returnedText.length();
                resetRecognizer();
            }

        });
        togglePause.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    isPaused = true;
                    speech.cancel();
                } else {
                    isPaused = false;
                    listen();
                }
            }
        });
        // turn off the sampling start/end audio output
        mute();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.stsr_menu, menu);
        return true;
    }

    private void changeLanguage(int lang)
    {
        switch( lang )
        {
            case 0: // locale
                langPref = Locale.getDefault().toString();
                break;
            case 1: // English
                langPref = "en_US";
                break;
            case 2: // French
                langPref = "fr_FR";
                break;
            case 3: // Spanish
                langPref = "es_MX";
                break;
            default:
                langPref = "en_US";
                break;
        }

        /* update the recognizer intent with the selected language */
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, langPref);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langPref);
    }

    private void changeTextSize(boolean up)
    {
        float size = returnedText.getTextSize();
        float incr = size * (float)0.05; // change by 5%

        if ( true == up )
        {
            size += incr;
        }
        else
        {
            size -= incr;
        }
        returnedText.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
    }

    private void saveChanges()
    {
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putFloat(sizeStr, returnedText.getTextSize());
        editor.putString(langStr, langPref);
        editor.apply();
    }

    private void doDialog(String title, String msg)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title)
                .setMessage(msg)
                .setNeutralButton("OK", null)
                .setCancelable(false)
                .create()
                .show();
    }

    private void showHelp()
    {
        String helpTitle = "Help";
        String helpMsg = "\nSpeak normally, directing the microphone " +
                           "toward the conversation\n" +
                           "\nPause: suspend listening\n" +
                           "\nListen: activate listening\n" +
                           "\nClear: empty display area\n" +
                           "\nReset: restart speech recogniton\n";
        doDialog(helpTitle, helpMsg);
    }

    private void showAbout()
    {
        String aboutTitle = "About";
        String aboutMsg = "\nSeeTalk Speech Recognizer\n" +
                            "Copyright 2018\n" +
                            "SeeTalk LLC\n" +
                            "Coeur d'Alene, ID\n" +
                            "\nPlease send comments to support@seetalk.org\n" +
                            "\nwith appreciation to Mohit Gupt at Truiton\n";
        doDialog(aboutTitle, aboutMsg);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem selItem) {
    // Handle item selection
    switch (selItem.getItemId()) {
        case R.id.local:
            changeLanguage(0);
            return true;
        case R.id.english:
            changeLanguage(1);
            return true;
        case R.id.french:
            changeLanguage(2);
            return true;
        case R.id.spanish:
            changeLanguage(3);
            return true;
        case R.id.textlarger:
            changeTextSize(true);
            return true;
        case R.id.textsmaller:
            changeTextSize(false);
            return true;
        case R.id.save:
            saveChanges();
            return true;
        case R.id.help:
            showHelp();
            return true;
        case R.id.about:
            showAbout();
            return true;
        default:
            return super.onOptionsItemSelected(selItem);
        }
    }

    private void mute() {
        //mute audio
        AudioManager amanager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        amanager.setStreamMute(AudioManager.STREAM_NOTIFICATION, true);
        amanager.setStreamMute(AudioManager.STREAM_MUSIC, true);
    }

    private void unmute() {
        //unmute audio
        AudioManager amanager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        amanager.setStreamMute(AudioManager.STREAM_NOTIFICATION, false);
        amanager.setStreamMute(AudioManager.STREAM_MUSIC, false);
    }

    private void resetRecognizer() {
        Log.i(LOG_TAG, "resetRecognizer");
        if (null != speech)
        {
            //speech.stopListening();
            speech.cancel();
            speech.destroy();
            speech = null;
        }
        isPaused = false;
        togglePause.setChecked(false);
        speech = SpeechRecognizer.createSpeechRecognizer(this);
        if (null != speech)
        {
            speech.setRecognitionListener(this);
            listen();
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
                    if (null == speech)
                    {
                        resetRecognizer();
                    }
                    else
                    {
                        listen();
                    }
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
        togglePause.setChecked(false);
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
        //unlisten();
        //toggleButton.setChecked(false);
    }

    @Override
    public void onError(int errorCode) {
        String errorMessage = getErrorText(errorCode);
        Log.i(LOG_TAG, "onError " + errorCode + " " + errorMessage);
        if (! isPaused) {
            switch (errorCode) {
                case SpeechRecognizer.ERROR_NETWORK:
                case SpeechRecognizer.ERROR_NO_MATCH:
                    if (SpeechRecognizer.ERROR_NO_MATCH == lastErr) {
                        resetRecognizer();
                    } else {
                        listen();
                    }
                    break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    resetRecognizer();
                    break;
                case SpeechRecognizer.ERROR_CLIENT:
                    resetRecognizer();
                    break;
                default:
                    String text = "=====> " + errorMessage + "\n";
                    Log.d(LOG_TAG, "FAILED " + errorMessage);
                    returnedText.append(text);
                    //toggleButton.setChecked(false);
                    listen();
                    break;
            }
        }
        lastErr = errorCode;
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

        String text;
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
        lastErr = 0;
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
