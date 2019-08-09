package com.dezzy.raycasterdemo;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity {
    private Raycaster view;

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        view = new Raycaster(this);
        setContentView(view);

        view.post(new Runnable() {
            @Override
            public void run() {
                view.saveDimensions();
            }
        });
        view.requestFocus();
    }
}
