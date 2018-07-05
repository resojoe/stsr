package com.seetalk.seetalk;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Build;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Layout;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener;


import java.util.ArrayList;
import java.util.Locale;

public class VoiceRecognitionActivity extends AppCompatActivity implements
        RecognitionListener {
    private static final String versionStr = "Version 1.03\n";
    private static final String LOG_TAG = "VoiceRecognitionActivity";
    private static final int REQUEST_RECORD_PERMISSION = 100;
    private static final String defLangStr = "en_US";
    private TextView returnedText;
    private ToggleButton togglePause;
    private ProgressBar progressBar;
    private SpeechRecognizer speech = null;
    private Intent recognizerIntent;
    private int partLen = 0;
    private int retTextPos = 0;
    private String partStr = "";
    private int lastErr = 0;
    private SharedPreferences sharedPref;
    private String sizeStr = "textSize";
    private String langStr = "prefLang";
    private String langPref;
    private boolean isPaused = false;
    private int noMatchCount = 0;
    private int audioLevelMusic = 0;
    private int audioLevelNotif = 0;

    ScaleGestureDetector scaleGestureDetector;
    GestureDetector      scrollDetector;
    GestureDetector.SimpleOnGestureListener simpleGestureListener;

    private enum prefLang {
        langLocal,
        langEnglish,
        langFrench,
        langSpanish
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Button clrButton;
        Button rstButton;
        float defaultTextSize;
        float savedTextSize;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        saveAudioLevels();

        // set our textView to scroll and output starting text
        returnedText = (TextView) findViewById(R.id.textView1);

        scaleGestureDetector =
                new ScaleGestureDetector(this,
                        new SeeTalkOnScaleGestureListener(returnedText));

        simpleGestureListener = new SeeTalkSimpleGestureListener(returnedText);
        scrollDetector = new GestureDetector(this, simpleGestureListener);

        //returnedText.setMovementMethod(new ScrollingMovementMethod());
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

        requestMicrophone();

        // define behaviors for button presses
        clrButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                returnedText.setText("==> buffer cleared <==\n");
                retTextPos = returnedText.length();
            }

        });
        rstButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                appendText("==> reset speech <==\n");
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
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getPointerCount() > 1) {
            scaleGestureDetector.onTouchEvent(event);
        }
        else {
            scrollDetector.onTouchEvent(event);
        }
        return true;
    }

    private class SeeTalkSimpleGestureListener extends
            GestureDetector.SimpleOnGestureListener {
        TextView scrollView;

        public SeeTalkSimpleGestureListener(TextView sview)
        {
            scrollView = sview;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY)
        {
            //scrollView.scrollBy((int) distanceX, (int) distanceY);
            int x = 0;
            int y = (int) distanceY;
            scrollView.scrollBy(x, y);
            return true;
        }
    }
    private class SeeTalkOnScaleGestureListener extends
            ScaleGestureDetector.SimpleOnScaleGestureListener {

        TextView scaleView;

        public SeeTalkOnScaleGestureListener(TextView tview) {
            scaleView = tview;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {

            float scaleFactor = detector.getScaleFactor();
            Log.i(LOG_TAG, "onScale " + String.valueOf(scaleFactor));

            if (scaleFactor > 1.02) {
                //appendText("increase size\n");
                changeTextSize(true);
            } else if (scaleFactor < 0.98) {
                //appendText("decrease size\n");
                changeTextSize(false);
            }
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {  }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.stsr_menu, menu);
        return true;
    }

    private void saveAudioLevels() {
        AudioManager amanager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioLevelMusic = amanager.getStreamVolume(AudioManager.STREAM_MUSIC);
        audioLevelNotif = amanager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
    }

    private void requestMicrophone(){
        mute();
        ActivityCompat.requestPermissions
                (VoiceRecognitionActivity.this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        REQUEST_RECORD_PERMISSION);
    }

    private void changeLanguage(prefLang lang)
    {
        switch( lang )
        {
            case langLocal: // locale
                langPref = Locale.getDefault().toString();
                break;
            case langEnglish: // English
                langPref = "en_US";
                break;
            case langFrench: // French
                langPref = "fr_FR";
                break;
            case langSpanish: // Spanish
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
        appendText("\n---> Settings saved <---\n");
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
                            versionStr +
                            "Copyright 2018\n" +
                            "SeeTalk LLC\n" +
                            "Post Falls, ID\n" +
                            "\nPlease send comments to support@seetalk.org\n" +
                            "\nwith appreciation to Mohit Gupt at Truiton\n";
        doDialog(aboutTitle, aboutMsg);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem selItem) {
    // Handle item selection
    switch (selItem.getItemId()) {
        case R.id.local:
            changeLanguage(prefLang.langLocal);
            return true;
        case R.id.english:
            changeLanguage(prefLang.langEnglish);
            return true;
        case R.id.french:
            changeLanguage(prefLang.langFrench);
            return true;
        case R.id.spanish:
            changeLanguage(prefLang.langSpanish);
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            amanager.setStreamMute(AudioManager.STREAM_NOTIFICATION, true);
            amanager.setStreamMute(AudioManager.STREAM_MUSIC, true);
            amanager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
        }
        else {
            amanager.setStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_MUTE, 0);
            amanager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0);
        }
    }

    private void unmute() {
        //unmute audio
        AudioManager amanager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            amanager.setStreamMute(AudioManager.STREAM_NOTIFICATION, false);
            amanager.setStreamMute(AudioManager.STREAM_MUSIC, false);
            amanager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        }
        else {
            amanager.setStreamVolume(AudioManager.STREAM_MUSIC, audioLevelMusic, 0);
            amanager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, audioLevelNotif, 0);
        }
    }

    private void resetRecognizer() {
        //Log.i(LOG_TAG, "resetRecognizer");
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
            appendText("\n*** Recognizer Unavailable ***\n");
        }
        lastErr = 0;
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

    private void appendText(String text)
    {
        if(returnedText != null){
            returnedText.append(text);
/*
            final Layout layout = returnedText.getLayout();
            if(layout != null){
                int scrollDelta = layout.getLineBottom(returnedText.getLineCount() - 1)
                        - returnedText.getScrollY() - returnedText.getHeight();
                if(scrollDelta > 0)
                    returnedText.scrollBy(0, scrollDelta);
            }
*/
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_RECORD_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mute();
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
        saveAudioLevels();
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
            //Log.i(LOG_TAG, "destroy");
        }
        unmute();
    }


    @Override
    public void onBeginningOfSpeech() {
        //Log.i(LOG_TAG, "onBeginningOfSpeech");
        //progressBar.setIndeterminate(false);
        //progressBar.setMax(10);
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
        //Log.i(LOG_TAG, "onBufferReceived: " + buffer);
    }

    @Override
    public void onEndOfSpeech() {
        //Log.i(LOG_TAG, "onEndOfSpeech");
        //progressBar.setIndeterminate(true);
        //unlisten();
        //toggleButton.setChecked(false);
    }

    @Override
    public void onError(int errorCode) {
        String errorMessage = getErrorText(errorCode);
        //Log.i(LOG_TAG, "onError " + errorCode );
        if (! isPaused) {
            switch (errorCode) {
                case SpeechRecognizer.ERROR_NO_MATCH:
                    noMatchCount++;
                    if (noMatchCount > 3) {
                        resetRecognizer();
                        noMatchCount = 0;
                    }
                    else
                    {
                        listen();
                    }
                    break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    //resetRecognizer();
                    //listen();
                    requestMicrophone();
                    break;
                case SpeechRecognizer.ERROR_CLIENT:
                    resetRecognizer();
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                default:
                    String text = "=====> " + errorMessage + "\n";
                    //Log.d(LOG_TAG, "FAILED " + errorMessage);
                    appendText(text);
                    //toggleButton.setChecked(false);
                    listen();
                    break;
            }
        }
        lastErr = errorCode;
    }

    @Override
    public void onEvent(int arg0, Bundle arg1) {
        //Log.i(LOG_TAG, "onEvent");
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
                appendText(text);
                partStr = partStr + text;
                //Log.i(LOG_TAG, "onPartialResults " + text + " " + partStr);
                partLen = curLen;
            }
        }
    }

    @Override
    public void onReadyForSpeech(Bundle arg0) {
        //Log.i(LOG_TAG, "onReadyForSpeech");
    }

    @Override
    public void onResults(Bundle results) {

        String text;
        String sep = "\n---- ";
        String tag = "\n++++ ";
        ArrayList<String> matches = results
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        text = matches.get(0);
        //Log.i(LOG_TAG, "onResults" + "\n>" + partStr + "<\n" + ">" + text + "<\n");
        if (false == partStr.equals(text)) {
            text = tag + text;
            appendText(text);
        }

        appendText(sep);
        partLen = 0;
        partStr = "";
        retTextPos = returnedText.length();
        listen();
        lastErr = 0;
        noMatchCount = 0;
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        //Log.i(LOG_TAG, "onRmsChanged: " + rmsdB);
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
