package com.example.camerabioandroid.camerabiomanager;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.camerabioandroid.R;
import com.google.android.material.snackbar.Snackbar;


public class BaseActivity extends AppCompatActivity {

    public static final String TAG = "BaseActivity";
    protected View rootView;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
        rootView = findViewById(R.id.root_view);
    }


    protected void showSnackbar(String text) {
        if (text == null) {
            Log.e(TAG, text);
            return;
        }

        Snackbar snackbar = Snackbar.make(getRootView(), text, Snackbar.LENGTH_LONG);
        View snackbarView = snackbar.getView();
        snackbarView.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.colorRed));
        TextView textView = snackbarView.findViewById(R.id.snackbar_text);
        textView.setTextColor(Color.WHITE);
        snackbar.show();
    }

    protected void showFastToast(final String message) {
        try {
            Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.TOP|Gravity.CENTER, 0, 0);
            toast.show();
        } catch (Exception ex) {
            Log.d(TAG, ex.toString());
        }
    }

    protected void showToast(final String message) {
        try {
            Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.TOP|Gravity.CENTER, 0, 0);
            toast.show();
        } catch (Exception ex) {
            Log.d(TAG, ex.toString());
        }
    }


    protected void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getRootView().getWindowToken(), 0);
    }



    public void showAlert(String message) {
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle(message);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK", (dialog, which) -> dialog.dismiss());
        alertDialog.show();
    }

    private View getRootView() {
        if (rootView == null) {
            rootView = findViewById(R.id.root_view);
        }
        return rootView;
    }

    protected String getNormalizedUserName(String userName) {

        if (userName == null || userName.trim().length() == 0) {
            return "";
        }

        String[] array = userName.toLowerCase().split(" ");
        String name = "";

        if (array.length > 1) {
            String strName = array[0];
            String strLastname = array[1];

            if (strName.length() > 1) {
                name  = strName.substring(0, 1).toUpperCase() + strName.substring(1) + " ";
            }

            if (strLastname.length() > 1) {
                name += strLastname.substring(0, 1).toUpperCase() + ".";
            }

        } else {
            String str = array[0];

            if (str != null && str.length() > 1) {
                name  = str.substring(0, 1).toUpperCase() + str.substring(1);
            }
        }
        return name;
    }
}
