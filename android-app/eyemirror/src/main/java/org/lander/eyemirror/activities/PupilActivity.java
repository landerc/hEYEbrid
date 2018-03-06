package org.lander.eyemirror.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.Surface;
import android.view.View;
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
import org.lander.eyemirror.Utils.RecordingHelper;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import de.morrox.fontinator.FontTextView;

/**
 * Created by christian on 16.06.17.
 */

public class PupilActivity extends BaseActivity implements CameraDialog.CameraDialogParent, NativeInterface.ProcessingCallback {

    private static final int REQUEST_WRITE_STORAGE = 1;
    private static boolean DEBUG = false;

    private static final String TAG = "hEYEbrid.PupilActivity";

    private static final float[] BANDWIDTH_FACTORS = {0.5f, 0.5f};

    public static final int IR_WIDTH = 640;
    public static final int IR_HEIGHT = 480;

    public static final int SCENE_WIDTH = 1280;
    public static final int SCENE_HEIGHT = 720;

    // for accessing USB and USB camera
    private USBMonitor mUSBMonitor;

    /* Handler for scene camera */
    private UVCCameraHandler mSceneHandler;
    /* Handler for eye camera */
    private UVCCameraHandler mIRHandler;

    /* eye components */
    private ExpandableLayout mEyeExpand;
    private CameraViewInterface mIRCameraView;
    private ImageButton expandEye;
    private ImageView mEyeCamIndicator, mEyeRecordIndicator;
    private Switch mIRRecordSwitch, mIRSwitch;

    /**
     * native interface to access C methods
     */
    private NativeInterface mNativeInterface;

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
    private Switch mRecordAllSwitch, mTrackingSwitch;
    private ImageButton mExpandControl;
    private int imageCounter;
    private Chronometer mChrono;
    private int[] mREcordIndicator = new int[]{0, 0, 0}; // scene, ir, corneal
    private FileOutputStream mOutputStreamWriterScene, mOutputStreamWriterIR, mOutputStreamWriterPupil, mOutputStreamWriterInfo;
    private boolean mRecordScene, mRecordIR;
    private int mFrameCounter;

    /**
     * dialog view id
     */
    private int mDialogOnView = -1;

    /**
     * Eye Camera picture as int array.
     */
    private int[] mEyeARGB8888;

    /**
     * local camera bitmaps
     */
    private final Bitmap mBitmap_IR = Bitmap.createBitmap(IR_WIDTH, IR_HEIGHT, Bitmap.Config.RGB_565);
    private final Bitmap mBitmap_Scene = Bitmap.createBitmap(SCENE_WIDTH, SCENE_HEIGHT, Bitmap.Config.RGB_565);

    /**
     * flags to indicate if a picture should be take
     */
    private boolean takePicIR = false;
    private boolean takePicScene = false;
    private boolean mRecordData = false;

    /**
     * object for synchronization of photo taking
     */
    private Object synchObj = new Object();
    private Object synchRecordingScene = new Object();
    private Object synchRecordingIR = new Object();
    private Object synchRecordingData = new Object();

    private RecordingHelper mRecordingHelper;

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

            synchronized (synchRecordingScene) {
                if (mRecordScene) {
                    /*
                    try {

                        if (mOutputStreamWriterScene != null) {
                            mOutputStreamWriterScene.write((System.currentTimeMillis()+"\n").getBytes());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }*/
                    mFrameCounter++;
                    mRecordingHelper.writeSceneTS(ts);
                }
            }

            mNativeInterface.setScene(new ImageData(null, ts));
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

            synchronized (synchRecordingIR) {
                if (mRecordIR) {/*

                        if (mOutputStreamWriterIR != null) {
                            mOutputStreamWriterIR.write((System.currentTimeMillis()+"\n").getBytes());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }*/
                    mRecordingHelper.writeIRTS(ts);
                }
            }

            frame.clear();
            mBitmap_IR.copyPixelsFromBuffer(frame);
            mNativeInterface.setIR(new ImageData(mBitmap_IR, ts));
        }

    };

    /**
     * Starts the recording of all data sources
     */
    private void startRecording() {
        System.out.println("start recording*****");
        mRecordingHelper.init();


        mSceneHandler.startRecording("world");
        synchronized (synchRecordingScene) {
            mRecordScene = true;
        }

        mIRHandler.startRecording("eye0");
        synchronized (synchRecordingIR) {
            mRecordIR = true;
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                mSceneRecordIndicator.setImageResource(R.drawable.ic_record_on);
                mEyeRecordIndicator.setImageResource(R.drawable.ic_record_on);

            }
        });

        mChrono.setBase(SystemClock.elapsedRealtime());
        mChrono.start();

        synchronized (synchRecordingData) {
            mRecordData = true;
        }
    }

    /**
     * Stops the recording of all data sources
     */
    private void stopRecording() {
        synchronized (synchRecordingScene) {
            mRecordScene = false;
        }
        synchronized (synchRecordingIR) {
            mRecordIR = false;
        }
        synchronized (synchRecordingData) {
            mRecordData = false;
        }

        mRecordingHelper.close(mFrameCounter);

        mChrono.stop();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                mEyeRecordIndicator.setImageResource(R.drawable.ic_record_off);
                mSceneRecordIndicator.setImageResource(R.drawable.ic_record_off);
                mChrono.setBase(SystemClock.elapsedRealtime());
            }
        });


        mFrameCounter = 0;

        mIRHandler.stopRecording();
        mSceneHandler.stopRecording();
    }

    /**
     * Timer to stop recording
     */
    final CountDownTimer cdt = new CountDownTimer(1000 * 60 * 5, 1000) {
        @Override
        public void onTick(long millisUntilFinished) {
        }

        @Override
        public void onFinish() {
            stopRecording();
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pupil);

        imageCounter = 0;

        /*** control view start ***/
        mControlExpand = (ExpandableLayout) findViewById(R.id.expandable_layout_control);
        mUSBIndicator = (ImageView) findViewById(R.id.usbIndicator);

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
                } else {
                    mControlExpand.expand();
                }
            }
        });

        mRecordAllSwitch = (Switch) findViewById(R.id.recordAllSwitch);
        mRecordAllSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    requestWritePermission();
                } else {

                    System.out.println("manually stopped recording*****");
                    cdt.cancel();
                    stopRecording();

                    /*
                    synchronized (synchObj1) {
                        mRecordScene = false;
                    }
                    synchronized (synchObj2) {
                        mRecordIR = false;
                    }

                    mRecordingHelper.close(mFrameCounter);

                    mREcordIndicator[0] = 0;
                    mREcordIndicator[1] = 0;
                    mREcordIndicator[2] = 0;
                    mChrono.stop();
                    mChrono.setBase(SystemClock.elapsedRealtime());

                    mIRHandler.stopRecording();
                    mSceneHandler.stopRecording();

                    mEyeRecordIndicator.setImageResource(R.drawable.ic_record_off);
                    mSceneRecordIndicator.setImageResource(R.drawable.ic_record_off);

                    mSceneRecordSwitch.setChecked(false);
                    mIRRecordSwitch.setChecked(false);


                    mFrameCounter = 0;*/
                }
            }
        });

        mChrono = (Chronometer) findViewById(R.id.chronoMeter);

        mTrackingSwitch = (Switch) findViewById(R.id.checkTracking);
        mTrackingSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mNativeInterface.startNativeThread();
                } else {
                    mNativeInterface.stopNativeThread();
                }
            }
        });
        /*** control view end ***/


        /*** eye view start ***/
        mEyeExpand = (ExpandableLayout) findViewById(R.id.expandable_layout_eye);
        mEyeCamIndicator = (ImageView) findViewById(R.id.eyeCamIndicator);
        mEyeRecordIndicator = (ImageView) findViewById(R.id.eyeRecordIndicator);
        mIRSwitch = (Switch) findViewById(R.id.irSwitch);
        mIRSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mDialogOnView = R.id.camera_view_ir;
                    if (mIRHandler != null) {
                        if (!mIRHandler.isOpened() && !mIRHandler.isPreviewing()) {
                            CameraDialog.showDialog(PupilActivity.this);
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

        mIRRecordSwitch = (Switch) findViewById(R.id.irRecordSwitch);
        mIRRecordSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                //TODO implement
            }
        });


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

        mIRCameraView = (CameraViewInterface) findViewById(R.id.camera_view_ir);
        mIRCameraView.setAspectRatio(IR_WIDTH / (float) IR_HEIGHT);
        //((UVCCameraTextureView)mIRCameraView).setOnClickListener(mOnClickListener);
        mIRHandler = UVCCameraHandler.createHandler(this, mIRCameraView, 1, IR_WIDTH, IR_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG, BANDWIDTH_FACTORS[0], mIRFrameCallback);
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
                            CameraDialog.showDialog(PupilActivity.this);
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
                } else {
                    mSceneExpand.expand();
                }
            }
        });

        mSceneCameraView = (CameraViewInterface) findViewById(R.id.camera_view_scene);
        mSceneCameraView.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float) UVCCamera.DEFAULT_PREVIEW_HEIGHT);
        // ((UVCCameraTextureView)mSceneCameraView).setOnClickListener(mOnClickListener);
        mSceneHandler = UVCCameraHandler.createHandler(this, mSceneCameraView, 1, SCENE_WIDTH, SCENE_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG, BANDWIDTH_FACTORS[1], mSceneFrameCallback);
        /*** eye view end ***/

        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
        mUSBMonitor.register();

        if (mSceneCameraView != null) {
            mSceneCameraView.onResume();
        }
        if (mIRCameraView != null) {
            mIRCameraView.onResume();
        }

        mNativeInterface = new NativeInterface(this, Mode.PUPIL);
        mRecordingHelper = new RecordingHelper(Mode.PUPIL);
    }


    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        mRecordingHelper.close(mFrameCounter);
        mNativeInterface.stopNativeThread();
        if (mSceneHandler != null) {
            mSceneHandler.close();
            mSceneHandler = null;
        }
        if (mIRHandler != null) {
            mIRHandler.close();
            mIRHandler = null;
        }

        mSceneCameraView = null;
        mIRCameraView = null;


        if (mUSBMonitor != null) {
            mUSBMonitor.unregister();
            mUSBMonitor.destroy();
            mUSBMonitor = null;
        }
        super.onDestroy();
    }

    private void requestWritePermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
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
                                ActivityCompat.requestPermissions(PupilActivity.this,
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
            startRecording();
            cdt.start();

            /*
            mRecordingHelper.init();

            for (int i = 0; i < mREcordIndicator.length; i++) {
                if (mREcordIndicator[i] > 0) {
                    switch (i) {
                        case 0:
                            mSceneHandler.startRecording("world");
                            mSceneRecordIndicator.setImageResource(R.drawable.ic_record_on);
                            mSceneRecordSwitch.setChecked(true);
                            synchronized (synchObj1) {
                                mRecordScene = true;
                            }
                            break;
                        case 1:
                            mIRHandler.startRecording("eye0");
                            mIRRecordSwitch.setChecked(true);
                            synchronized (synchObj2) {
                                mRecordIR = true;
                            }
                            break;
                        case 2:
                            mEyeRecordIndicator.setImageResource(R.drawable.ic_record_on);
                            mChrono.setBase(SystemClock.elapsedRealtime());
                            mChrono.start();
                            break;
                        default:
                            break;
                    }
                }
            }
            */
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
                    startRecording();
                    cdt.start();

                    /*
                    mRecordingHelper.init();

                    for (int i = 0; i < mREcordIndicator.length; i++) {
                        if (mREcordIndicator[i] > 0) {
                            switch (i) {
                                case 0:
                                    mSceneHandler.startRecording("world");
                                    mSceneRecordSwitch.setChecked(true);
                                    mSceneRecordIndicator.setImageResource(R.drawable.ic_record_on);
                                    synchronized (synchObj1) {
                                        mRecordScene = true;
                                    }
                                    break;
                                case 1:
                                    mIRHandler.startRecording("eye0");
                                    mIRRecordSwitch.setChecked(true);
                                    synchronized (synchObj2) {
                                        mRecordIR = true;
                                    }
                                    break;
                                case 2:
                                    mEyeRecordIndicator.setImageResource(R.drawable.ic_record_on);
                                    mChrono.setBase(SystemClock.elapsedRealtime());
                                    mChrono.start();
                                    break;
                                default:
                                    break;
                            }
                        }
                    }*/

                } else {
                    /*
                    mREcordIndicator[0] = 0;
                    mREcordIndicator[1] = 0;
                    mREcordIndicator[2] = 0;

                    mSceneRecordSwitch.setChecked(false);
                    mIRRecordSwitch.setChecked(false);
                       */
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
            Toast.makeText(PupilActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
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
                mSceneHandler.startGrabbing(); //TODO: FIXME
            } else if (mDialogOnView == R.id.camera_view_ir && !mIRHandler.isOpened()) {
                mIRHandler.open(ctrlBlock);
                final SurfaceTexture st = mIRCameraView.getSurfaceTexture();
                mIRHandler.startPreview(new Surface(st));
                //start grabbing
                mIRHandler.startGrabbing(); //TODO: FIXME
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
            Toast.makeText(PupilActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
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
                            CameraDialog.showDialog(PupilActivity.this);
                        } else {
                            // do nothing
                            //mSceneHandler.close();
                        }
                    }
                    break;
                case R.id.camera_view_ir:
                    mDialogOnView = R.id.camera_view_ir;
                    if (mIRHandler != null) {
                        if (!mIRHandler.isOpened()) {
                            CameraDialog.showDialog(PupilActivity.this);
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
     *
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
                if (viewID == R.id.camera_view_ir && (mIRHandler != null) && !mIRHandler.isOpened()) {
                    mEyeCamIndicator.setImageResource(R.drawable.ic_cam_on);
                    mIRSwitch.setChecked(true);
                }

            }
        }, 0);
    }

    private Bitmap getBitmapFromAsset(String strName) {
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
    public void onDialogResult(boolean canceled) {
    }

    @Override
    public void onDialogResult(boolean canceled, String[] information) {
    }


    @Override
    public void onHybridEyeTracked(Bitmap ir, Bitmap corneal, DataFrame df) {
        //write pupil data info
        synchronized (synchRecordingData) {
            if (mRecordData) {
                mRecordingHelper.update(df);
            }
        }
    }

    @Override
    public void onSceneRegistered(Bitmap feature) {
    }

    @Override
    public void onGazeMapped() {
    }
}
