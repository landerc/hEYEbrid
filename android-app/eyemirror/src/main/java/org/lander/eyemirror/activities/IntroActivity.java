package org.lander.eyemirror.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

import org.lander.eyemirror.R;

import java.util.ArrayList;
import java.util.List;

import io.blackbox_vision.wheelview.LoopScrollListener;
import io.blackbox_vision.wheelview.view.WheelView;

public class IntroActivity extends AppCompatActivity {

    private WheelView wheelView;
    private ImageButton continueBtn;
    private ImageView splash, device;

    private final List<String> MODES = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);

        MODES.add("hEYEbrid");
        MODES.add("hEYEbrid-3C");
        MODES.add("Pupil");

        device = (ImageView) findViewById(R.id.imageView3);

        continueBtn = (ImageButton) findViewById(R.id.continueBtn);
        continueBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int mode = wheelView.getSelectedItem();
                switch (mode) {
                    case 0:
                        Intent i1 = new Intent(getApplicationContext(), HeyebridActivity.class);
                        startActivity(i1);
                        break;
                    case 1:
                        Intent i = new Intent(getApplicationContext(), Heyebrid3CActivity.class);
                        startActivity(i);
                        break;
                    case 2:
                        Intent i2 = new Intent(getApplicationContext(), PupilActivity.class);
                        startActivity(i2);
                        break;
                    default:
                        break;
                }
            }
        });

        wheelView = (WheelView) findViewById(R.id.loop_view);
        wheelView.setItems(MODES);
        wheelView.setLoopListener(new LoopScrollListener() {
            @Override
            public void onItemSelect(int i) {
                switch (i) {
                    case 0:
                        device.setImageResource(R.drawable.glasses);
                        break;
                    case 1:
                        device.setImageResource(R.drawable.glasses2);
                        break;
                    case 2:
                        device.setImageResource(R.drawable.glasses2);
                        break;
                    default:
                        break;
                }
            }
        });


        splash = (ImageView) findViewById(R.id.splash);

        CountDownTimer cd = new CountDownTimer(1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {

            }

            @Override
            public void onFinish() {
                splash.setVisibility(View.GONE);
            }
        };
        cd.start();
    }

}
