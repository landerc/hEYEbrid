package org.lander.eyemirror.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.Chronometer;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.Toast;

import com.serenegiant.common.BaseActivity;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usbcameracommon.UVCCameraHandler;
import com.serenegiant.widget.CameraViewInterface;

import net.cachapa.expandablelayout.ExpandableLayout;

import org.lander.eyemirror.Processing.NativeInterface;
import org.lander.eyemirror.R;
import org.lander.eyemirror.Utils.DataFrame;
import org.lander.eyemirror.Utils.ImageData;
import org.lander.eyemirror.Utils.Mode;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Timer;

import de.morrox.fontinator.FontTextView;


public class Heyebrid3CActivity extends BaseActivity implements CameraDialog.CameraDialogParent, NativeInterface.ProcessingCallback {

    private static final int REQUEST_WRITE_STORAGE = 1;
    private static boolean DEBUG = false;

    private static final String TAG = "hEYEbrid.Heyebrid3CActivity";

    private static final float[] BANDWIDTH_FACTORS = { 0.3f, 0.3f, 0.3f };

    public static final int CORNEAL_WIDTH = 1280;
    public static final int CORNEAL_HEIGHT = 960;

    public static final int IR_WIDTH = 640;
    public static final int IR_HEIGHT = 480;

    public static final int SCENE_WIDTH = 1280;
    public static final int SCENE_HEIGHT = 720;

    // for accessing USB and USB camera
    private USBMonitor mUSBMonitor;

    /* Handler for scene camera */
    private UVCCameraHandler mSceneHandler;
    /* Handler for eye camera */
    private UVCCameraHandler mCornealHandler, mIRHandler;

    /* eye components */
    private ExpandableLayout mEyeExpand;
    private CameraViewInterface mCornealCamerView, mIRCameraView;
    private ImageButton expandEye;
    private ImageView mEyeCamIndicator, mEyeRecordIndicator;
    private Switch mCornealSwitch, mCornealRecordSwitch, mIRRecordSwitch, mIRSwitch;

    /**
     * ui components for testing
     */
    private ImageView imgBuffer, imgBuffer2;

    /* scene components */
    private ExpandableLayout mSceneExpand;
    private FontTextView mSceneLabel;
    private CameraViewInterface mSceneCameraView;
    private ImageButton expandScene;
    private ImageView mSceneCamIndicator, mSceneRecordIndicator;
    private Switch mSceneSwitch, mSceneRecordSwitch;

    /* control components*/
    private ExpandableLayout mControlExpand;
    private ImageView mUSBIndicator;
    private Switch mRecordAllSwitch;
    private ImageButton mExpandControl;
    private ImageButton mDelCalBtn;
    private CheckBox mCheckAuto, mCheckManual;
    private int imageCounter;
    private Chronometer mChrono;
    private int[] mREcordIndicator = new int[]{0,0,0}; // scene, ir, corneal

    /**
     * dialog view id
     */
    private int mDialogOnView = -1;

    /**
     * native interface to access C methods
     */
    private NativeInterface mNativeInterface;

    /**
     * local camera bitmaps
     */
    private final Bitmap mBitmapCorneal = Bitmap.createBitmap(CORNEAL_WIDTH, CORNEAL_HEIGHT, Bitmap.Config.RGB_565);
    private final Bitmap mBitmap_IR = Bitmap.createBitmap(IR_WIDTH, IR_HEIGHT, Bitmap.Config.RGB_565);
    private final Bitmap mBitmap_Scene = Bitmap.createBitmap(SCENE_WIDTH, SCENE_HEIGHT, Bitmap.Config.RGB_565);

    /**
     * flags to indicate if a picture should be take
     */
    private boolean takePicCorneal = false;
    private boolean takePicIR = false;
    private boolean takePicScene = false;

    /**
     * object for synchronization of photo taking
     */
    private Object synchObj = new Object();

    /**
     * object for synchronization of flag indicating if scene and corneal images should be passed
     */
    private Object synchImgPassing = new Object();

    private boolean passCornealImage = false;
    private boolean passSceneImage = false;

    private Timer mHomographyTimer;

    /* Begin Overrides of ProcessingCallback */
    @Override
    public void onHybridEyeTracked(final Bitmap ir, final Bitmap corneal, DataFrame df) {
        Log.d("Heyebrid3CActivity", "callback processing");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (ir != null) {
                    imgBuffer.setImageBitmap(ir);
                }
                if (corneal != null) {
                    imgBuffer2.setImageBitmap(corneal);
                }
            }},0);
    }

    @Override
    public void onSceneRegistered(final Bitmap feature) {
        Log.d("Heyebrid3CActivity", "callback feature");
        /*
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (feature != null) {
                    imgBuffer3.setImageBitmap(feature);
                }
            }},0);

        setPassCornealImage(false);
        setPassSceneImage(false);

        //TODO:debug
        mNativeInterface.gazeMapping();
        */
    }

    @Override
    public void onGazeMapped() {
        //TODO implementation
        Log.d("Heyebrid3CActivity", "callback gaze mapped");
        /*

        final NativeInterface.GazePoint gp = mNativeInterface.getGazePoint();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mGazeCoords.setText(gp.getGazeXNorm() + ", " + gp.getGazeYNorm());
                mGazeIndocator.setImageResource(R.drawable.ic_gaze_on);

                if (DEBUG && mBitmap_Scene != null) {
                    imgBuffer4.setImageBitmap(Utils.drawPoint(mBitmap_Scene, gp));
                }
            }
        },0);
        */
    }
    /* End Overrides of ProcessingCallback */


    /**
     * scene camera frame callback
     */
    private IFrameCallback mSceneFrameCallback = new IFrameCallback() {
        @Override
        public void onFrame(ByteBuffer frame, double ts) {
            synchronized (synchObj) {
                if (takePicScene) {
                    frame.clear();
                    mBitmap_Scene.copyPixelsFromBuffer(frame);
                    File outputFile = new File(getExternalFilesDir("captured"), "scene" + imageCounter + ".png");
                    try {
                        BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(outputFile));
                        try {
                            mBitmap_Scene.compress(Bitmap.CompressFormat.PNG, 100, os);
                            os.flush();
                        } catch (final IOException e) {
                        }
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    takePicScene = false;
                }
            }

            synchronized (synchImgPassing) {
                if (passSceneImage) {
                    frame.clear();
                    mBitmap_Scene.copyPixelsFromBuffer(frame);
                    mNativeInterface.setScene(new ImageData(mBitmap_Scene, ts));
                }
            }
        }

    };


    /**
     * Corneal camera frame callback
     */
    private IFrameCallback mEyeFrameCallback = new IFrameCallback() {
        @Override
        public void onFrame(ByteBuffer frame, double ts) {
            synchronized (synchObj) {
                if (takePicCorneal) {
                    frame.clear();
                    mBitmapCorneal.copyPixelsFromBuffer(frame);
                    File outputFile = new File(getExternalFilesDir("captured"), "corneal" + imageCounter + ".png");
                    try {
                        BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(outputFile));
                        try {
                            mBitmapCorneal.compress(Bitmap.CompressFormat.PNG, 100, os);
                            os.flush();
                        } catch (final IOException e) {
                        }
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    takePicCorneal = false;
                }
            }

            synchronized (synchImgPassing) {
                if (passCornealImage) {
                    frame.clear();
                    mBitmapCorneal.copyPixelsFromBuffer(frame);
                    mNativeInterface.setScene(new ImageData(mBitmapCorneal, ts));
                }
            }
        }

    };

    /**
     * IR camera frame callback
     */
    private IFrameCallback mIRFrameCallback = new IFrameCallback() {
        @Override
        public void onFrame(ByteBuffer frame, double ts) {
            synchronized (synchObj) {
                if (takePicIR) {
                    frame.clear();
                    mBitmap_IR.copyPixelsFromBuffer(frame);
                    File outputFile = new File(getExternalFilesDir("captured"), "ir" + imageCounter + ".png");
                    try {
                        BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(outputFile));
                        try {
                            mBitmap_IR.compress(Bitmap.CompressFormat.PNG, 100, os);
                            os.flush();
                        } catch (final IOException e) {
                        }
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    takePicIR = false;
                }
            }

            frame.clear();
            mBitmap_IR.copyPixelsFromBuffer(frame);
            mNativeInterface.setIR(new ImageData(mBitmap_IR, System.currentTimeMillis()));
        }

    };




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        //TODO: FIXME

        imageCounter = 0;

        /*** control view start ***/
        mControlExpand = (ExpandableLayout) findViewById(R.id.expandable_layout_control);
        mUSBIndicator = (ImageView) findViewById(R.id.usbIndicator);
        mDelCalBtn = (ImageButton) findViewById(R.id.btnDelCal);
        mDelCalBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //TODO:implement
            }
        });

        mCheckAuto = (CheckBox) findViewById(R.id.checkAuto);
        mCheckAuto.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mCheckManual.setChecked(false);

                    //TODO:implement
                }
            }
        });

        mCheckManual = (CheckBox) findViewById(R.id.checkManual);
        mCheckManual.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mCheckAuto.setChecked(false);

                    //TODO:implement
                }
            }
        });


        mExpandControl = (ImageButton) findViewById(R.id.expandControlBtn);
        mExpandControl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mControlExpand.isExpanded()) {
                    mControlExpand.collapse();
                } else if (mEyeExpand.isExpanded()) {
                    mEyeExpand.collapse();
                    mControlExpand.expand();
                } else if (mSceneExpand.isExpanded()) {
                    mSceneExpand.collapse();
                    mControlExpand.expand();
                }else {
                    mControlExpand.expand();
                }
            }
        });

        mRecordAllSwitch = (Switch) findViewById(R.id.recordAllSwitch);
        mRecordAllSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                //TODO: implement

                if (isChecked) {
                    mREcordIndicator[0] = 1;
                    mREcordIndicator[1] = 1;
                    mREcordIndicator[2] = 1;

                    requestWritePermission();
                }
                else {
                    mREcordIndicator[0] = 0;
                    mREcordIndicator[1] = 0;
                    mREcordIndicator[2] = 0;
                    mChrono.stop();
                    mChrono.setBase(SystemClock.elapsedRealtime());

                    mIRHandler.stopRecording();
                    mCornealHandler.stopRecording();
                    mSceneHandler.stopRecording();

                    mEyeRecordIndicator.setImageResource(R.drawable.ic_record_off);
                    mSceneRecordIndicator.setImageResource(R.drawable.ic_record_off);

                    mSceneRecordSwitch.setChecked(false);
                    mIRRecordSwitch.setChecked(false);
                    mCornealRecordSwitch.setChecked(false);
                }
            }
        });

        mChrono = (Chronometer) findViewById(R.id.chronoMeter);
        /*** control view end ***/


        /*** eye view start ***/
        mEyeExpand = (ExpandableLayout) findViewById(R.id.expandable_layout_eye);
        mEyeCamIndicator = (ImageView) findViewById(R.id.eyeCamIndicator);
        mEyeRecordIndicator = (ImageView) findViewById(R.id.eyeRecordIndicator);
        mCornealSwitch = (Switch) findViewById(R.id.eyeSwitch);
        mCornealSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mDialogOnView = R.id.camera_view_eye;
                    if (mCornealHandler != null) {
                        if (!mCornealHandler.isOpened() && !mCornealHandler.isPreviewing()) {
                            CameraDialog.showDialog(Heyebrid3CActivity.this);
                        } else {
                            mCornealHandler.close();
                        }
                    }
                } else {
                    if (mCornealHandler != null) {
                        mCornealHandler.stopPreview();
                        mCornealHandler.close();
                    }
                }
            }
        });
        mIRSwitch = (Switch) findViewById(R.id.irSwitch);
        mIRSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mDialogOnView = R.id.camera_view_ir;
                    if (mIRHandler != null) {
                        if (!mIRHandler.isOpened() && !mIRHandler.isPreviewing()) {
                            CameraDialog.showDialog(Heyebrid3CActivity.this);
                        } else {
                            mIRHandler.close();
                        }
                    }
                } else {
                    if (mIRHandler != null) {
                        mIRHandler.stopPreview();
                        mIRHandler.close();
                    }
                }
            }
        });

        mCornealRecordSwitch = (Switch) findViewById(R.id.eyeRecordSwitch);
        mCornealRecordSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                //TODO:implement
            }
        });

        mIRRecordSwitch = (Switch) findViewById(R.id.irRecordSwitch);
        mIRRecordSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                //TODO implement
            }
        });

        imgBuffer = (ImageView) findViewById(R.id.imgBuffer);
        imgBuffer2 = (ImageView) findViewById(R.id.imgBuffer2);

        expandEye = (ImageButton) findViewById(R.id.expandEyeBtn);
        expandEye.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mEyeExpand.isExpanded()) {
                    mEyeExpand.collapse();
                } else if (mSceneExpand.isExpanded()) {
                    mSceneExpand.collapse();
                    mEyeExpand.expand();
                } else if (mControlExpand.isExpanded()) {
                    mControlExpand.collapse();
                    mEyeExpand.expand();
                } else {
                    mEyeExpand.expand();
                }
            }
        });

        mCornealCamerView = (CameraViewInterface) findViewById(R.id.camera_view_eye);
        mCornealCamerView.setAspectRatio(CORNEAL_WIDTH / (float) CORNEAL_HEIGHT);
        //((UVCCameraTextureView)mCornealCamerView).setOnClickListener(mOnClickListener);
        mCornealHandler = UVCCameraHandler.createHandler(this, mCornealCamerView, 1, CORNEAL_WIDTH, CORNEAL_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG, BANDWIDTH_FACTORS[0], mEyeFrameCallback);

        mIRCameraView = (CameraViewInterface) findViewById(R.id.camera_view_ir);
        mIRCameraView.setAspectRatio(IR_WIDTH / (float) IR_HEIGHT);
        //((UVCCameraTextureView)mIRCameraView).setOnClickListener(mOnClickListener);
        mIRHandler = UVCCameraHandler.createHandler(this, mIRCameraView, 1, IR_WIDTH, IR_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG, BANDWIDTH_FACTORS[1], mIRFrameCallback);
        /*** eye view end ***/

        /*** scene view start ***/
        mSceneExpand = (ExpandableLayout) findViewById(R.id.expandable_layout_scene);
        mSceneCamIndicator = (ImageView) findViewById(R.id.sceneCamIndicator);
        mSceneRecordIndicator = (ImageView) findViewById(R.id.sceneRecordIndicator);
        mSceneSwitch = (Switch) findViewById(R.id.sceneSwitch);
        mSceneSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mDialogOnView = R.id.camera_view_scene;
                    if (mSceneHandler != null) {
                        if (!mSceneHandler.isOpened()) {
                            CameraDialog.showDialog(Heyebrid3CActivity.this);
                        } else {
                            mSceneHandler.close();
                        }
                    }
                } else {
                    if (mSceneHandler != null) {
                        mSceneHandler.stopPreview();
                        mSceneHandler.close();
                    }
                }
            }
        });
        mSceneRecordSwitch = (Switch) findViewById(R.id.sceneRecordSwitch);
        mSceneRecordSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                    if (isChecked) {
                        mREcordIndicator[0] = 1;
                        requestWritePermission();
                    } else {
                        mREcordIndicator[0] = 0;
                        mSceneHandler.stopRecording();
                        mSceneRecordIndicator.setImageResource(R.drawable.ic_record_off);
                    }
            }
        });
        mSceneLabel = (FontTextView) findViewById(R.id.scene_lbl);

        expandScene = (ImageButton) findViewById(R.id.expandSceneBtn);
        expandScene.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSceneExpand.isExpanded()) {
                    mSceneExpand.collapse();
                } else if (mEyeExpand.isExpanded()) {
                    mEyeExpand.collapse();
                    mSceneExpand.expand();
                } else if (mControlExpand.isExpanded()) {
                    mControlExpand.collapse();
                    mSceneExpand.expand();
                }else {
                    mSceneExpand.expand();
                }
            }
        });

        mSceneCameraView = (CameraViewInterface) findViewById(R.id.camera_view_scene);
        mSceneCameraView.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float)UVCCamera.DEFAULT_PREVIEW_HEIGHT);
       // ((UVCCameraTextureView)mSceneCameraView).setOnClickListener(mOnClickListener);
        mSceneHandler = UVCCameraHandler.createHandler(this, mSceneCameraView,1,  SCENE_WIDTH, SCENE_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG, BANDWIDTH_FACTORS[2], mSceneFrameCallback);
        /*** eye view end ***/

        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
        mUSBMonitor.register();

        mNativeInterface = new NativeInterface(this, Mode.HEYEBRID3C);
        mNativeInterface.createAkazeTracker();

        mHomographyTimer = new Timer();
    }


    protected void onStart() {

        //mUSBMonitor.register();
        if (mSceneCameraView != null) {
            mSceneCameraView.onResume();
        }
        if (mCornealCamerView != null) {
            mCornealCamerView.onResume();
        }
        if (mIRCameraView != null) {
            mIRCameraView.onResume();
        }

        super.onStart();
    }

    @Override
    protected void onStop() {
        if (mSceneCameraView != null) {
            mSceneCameraView.onPause();
        }
        if (mCornealCamerView != null) {
            mCornealCamerView.onPause();
        }
        if (mIRCameraView != null) {
            mIRCameraView.onPause();
        }
        /*
        if (mSceneHandler != null) {
            mSceneHandler.close();
        }
        if (mCornealHandler != null) {
            mCornealHandler.close();
        }
        if (mIRHandler != null) {
            mIRHandler.close();
        }

        mNativeInterface.stopNativeThreadPlus();
        mUSBMonitor.unregister();*/
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (mSceneHandler != null) {
            mSceneHandler.close();
            mSceneHandler = null;
        }
        if (mCornealHandler != null) {
            mCornealHandler.close();
            mCornealHandler = null;
        }
        if (mIRHandler != null) {
            mIRHandler.close();
            mIRHandler = null;
        }
        if (mUSBMonitor != null) {
            mUSBMonitor.destroy();
            mUSBMonitor.unregister();
            mUSBMonitor = null;
        }

        mNativeInterface.stopNativeThread();

        mSceneCameraView = null;
        mCornealCamerView = null;
        mIRCameraView = null;
        super.onDestroy();
    }

    private void requestWritePermission() {
        if ( ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                Snackbar.make(mControlExpand, "Write permission is needed to record the camera preview.",
                        Snackbar.LENGTH_INDEFINITE)
                        .setAction("OK", new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                ActivityCompat.requestPermissions(Heyebrid3CActivity.this,
                                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                        REQUEST_WRITE_STORAGE);
                            }
                        })
                        .show();
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_WRITE_STORAGE);
            }
        } else {
            for (int i = 0; i < mREcordIndicator.length; i++) {
                if (mREcordIndicator[i] > 0) {
                    switch (i) {
                        case 0:
                            mSceneHandler.startRecording("scene");
                            mSceneRecordIndicator.setImageResource(R.drawable.ic_record_on);
                            mSceneRecordSwitch.setChecked(true);
                            break;
                        case 1:
                            mIRHandler.startRecording("ir");
                            mIRRecordSwitch.setChecked(true);
                            break;
                        case 2:
                            mCornealHandler.startRecording("corneal");
                            mCornealRecordSwitch.setChecked(true);
                            mEyeRecordIndicator.setImageResource(R.drawable.ic_record_on);
                            mChrono.setVisibility(View.VISIBLE);
                            mChrono.start();
                            break;
                        default:
                            break;
                    }
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_WRITE_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay!

                    for (int i = 0; i < mREcordIndicator.length; i++) {
                        if (mREcordIndicator[i] > 0) {
                            switch (i) {
                                case 0:
                                    mSceneHandler.startRecording("scene");
                                    mSceneRecordSwitch.setChecked(true);
                                    mSceneRecordIndicator.setImageResource(R.drawable.ic_record_on);
                                    break;
                                case 1:
                                    mIRHandler.startRecording("ir");
                                    mIRRecordSwitch.setChecked(true);
                                    break;
                                case 2:
                                    mCornealHandler.startRecording("corneal");
                                    mCornealRecordSwitch.setChecked(true);
                                    mEyeRecordIndicator.setImageResource(R.drawable.ic_record_on);
                                    mChrono.setVisibility(View.VISIBLE);
                                    mChrono.start();
                                    break;
                                default:
                                    break;
                            }
                        }
                    }

                } else {
                    mREcordIndicator[0] = 0;
                    mREcordIndicator[1] = 0;
                    mREcordIndicator[2] = 0;

                    mSceneRecordSwitch.setChecked(false);
                    mIRRecordSwitch.setChecked(false);
                    mCornealRecordSwitch.setChecked(false);

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

        }
    }


    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            Toast.makeText(Heyebrid3CActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mUSBIndicator.setImageResource(R.drawable.ic_usb_connected);
                }
            });
        }

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            if (mDialogOnView == R.id.camera_view_scene && !mSceneHandler.isOpened()) {
                mSceneHandler.open(ctrlBlock);
                final SurfaceTexture st = mSceneCameraView.getSurfaceTexture();
                mSceneHandler.startPreview(new Surface(st));
                //mSceneHandler.startGrabbing(); //TODO: FIXME
            } else if (mDialogOnView == R.id.camera_view_eye && !mCornealHandler.isOpened()) {
                mCornealHandler.open(ctrlBlock);
                final SurfaceTexture st = mCornealCamerView.getSurfaceTexture();
                mCornealHandler.startPreview(new Surface(st));
                //start grabbing
                //mCornealHandler.startGrabbing(); //TODO: FIXME
            } else if (mDialogOnView == R.id.camera_view_ir && !mIRHandler.isOpened()) {
                mIRHandler.open(ctrlBlock);
                final SurfaceTexture st = mIRCameraView.getSurfaceTexture();
                mIRHandler.startPreview(new Surface(st));
                //start grabbing
                //mIRHandler.startGrabbing(); //TODO: FIXME
            }

            //update ui
            String[] info = new String[]{device.getProductName(), device.getSerialNumber()};
            int viewID = mDialogOnView;
            updateUI(viewID, info);

            mDialogOnView = -1;
        }

        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {

            if ((mSceneHandler != null) && !mSceneHandler.isEqual(device)) {
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        mSceneHandler.close();
                    }
                }, 0);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mSceneHandler != null) mSceneSwitch.setChecked(false);
                        mSceneCamIndicator.setImageResource(R.drawable.ic_cam_off);
                    }
                });
            } else if ((mCornealHandler != null) && !mCornealHandler.isEqual(device)) {
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        mCornealHandler.close();
                    }
                }, 0);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mCornealHandler != null) mCornealSwitch.setChecked(false);
                        mEyeCamIndicator.setImageResource(R.drawable.ic_cam_off);
                    }
                });
            } else if ((mIRHandler != null) && !mIRHandler.isEqual(device)) {
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        if (mIRHandler != null) mIRHandler.close();
                        mIRSwitch.setChecked(false);
                        //mEyeCamIndicator.setImageResource(R.drawable.ic_cam_off);
                    }
                }, 0);
            }

        }

        @Override
        public void onDettach(final UsbDevice device) {
            Toast.makeText(Heyebrid3CActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mUSBIndicator.setImageResource(R.drawable.ic_usb_disconneted);
                }
            });
        }

        @Override
        public void onCancel(final UsbDevice device) {

        }
    };

    private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(final View view) {
            switch (view.getId()) {
                case R.id.camera_view_scene:
                    mDialogOnView = R.id.camera_view_scene;
                    if (mSceneHandler != null) {
                        if (!mSceneHandler.isOpened()) {
                            CameraDialog.showDialog(Heyebrid3CActivity.this);
                        } else {
                            // do nothing
                            //mSceneHandler.close();
                        }
                    }
                    break;
                case R.id.camera_view_eye:
                    mDialogOnView = R.id.camera_view_eye;
                    if (mCornealHandler != null) {
                        if (!mCornealHandler.isOpened()) {
                            CameraDialog.showDialog(Heyebrid3CActivity.this);
                        } else {
                            // do nothing
                            // mCornealHandler.close();
                        }
                    }
                    break;
                case R.id.camera_view_ir:
                    mDialogOnView = R.id.camera_view_ir;
                    if (mIRHandler != null) {
                        if (!mIRHandler.isOpened()) {
                            CameraDialog.showDialog(Heyebrid3CActivity.this);
                        } else {
                            //do nothing
                            // mIRHandler.close();
                        }
                    }
                    break;
                /*
                case R.id.btnCapture:
                    if (mCornealHandler != null && mIRHandler != null) {
                        //Log.d("Heyebrid3CActivity", "view size: " + ((UVCCameraTextureView)mCornealCamerView).getWidth() + ", " + ((UVCCameraTextureView)mCornealCamerView).getHeight());
                        //mCornealHandler.captureStill("left"+imageCounter+".png");
                        //mIRHandler.captureStill("right"+imageCounter+".png");
                        //imageCounter++;
                        synchronized (synchObj) {
                            imageCounter++;
                            takePicCorneal = true;
                            takePicIR = true;
                            takePicScene = true;
                        }
                    }
                    break;
                case R.id.btnRT:
                    DEBUG = false;
                    mNativeInterface.startNativeThreadPlus();
                    mHomographyTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            synchronized (synchImgPassing) {
                                passCornealImage = true;
                                passSceneImage = true;
                            }
                        }
                    }, 5000, 10 * 60 * 1000);
                    break;
                   */
                default:
                    break;
            }
        }
    };

    /**
     * method to update several ui elements
     * @param viewID
     * @param information
     */
    private void updateUI(final int viewID, final String[] information) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String name = information[0];
                if (information[1] != null && information[1].equals("73BA28D0")) {
                    name = "Logitech Webcam C270";
                }
                if (viewID == R.id.camera_view_scene && (mSceneHandler != null) && !mSceneHandler.isOpened()) {
                    mSceneLabel.setText(name);
                    mSceneCamIndicator.setImageResource(R.drawable.ic_cam_on);
                    mSceneSwitch.setChecked(true);
                }
                if (viewID == R.id.camera_view_eye && (mCornealHandler != null) && !mCornealHandler.isOpened()) {
                    if (mIRSwitch.isChecked()) {
                        mEyeCamIndicator.setImageResource(R.drawable.ic_cam_on);
                    }
                    mCornealSwitch.setChecked(true);
                }
                if (viewID == R.id.camera_view_ir && (mIRHandler != null) && !mIRHandler.isOpened()) {
                    if (mCornealSwitch.isChecked()) {
                        mEyeCamIndicator.setImageResource(R.drawable.ic_cam_on);
                    }
                    mIRSwitch.setChecked(true);
                }

            }},0);
    }

    private Bitmap getBitmapFromAsset(String strName)
    {
        AssetManager assetManager = getAssets();
        InputStream istr = null;
        try {
            istr = assetManager.open(strName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Bitmap bitmap = BitmapFactory.decodeStream(istr);
        return bitmap;
    }

    @Override
    public USBMonitor getUSBMonitor() {
        return mUSBMonitor;
    }

    @Override
    public void onDialogResult(boolean canceled) {}

    @Override
    public void onDialogResult(boolean canceled, String[] information) {}



    public void setPassCornealImage(boolean passCornealImage) {
        synchronized (synchImgPassing) {
            this.passCornealImage = passCornealImage;
        }
    }

    public void setPassSceneImage(boolean passSceneImage) {
        synchronized (synchImgPassing) {
            this.passSceneImage = passSceneImage;
        }
    }
}
