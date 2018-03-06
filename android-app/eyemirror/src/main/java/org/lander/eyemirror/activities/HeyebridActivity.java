package org.lander.eyemirror.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.media.MediaActionSound;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
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
import com.serenegiant.usbcameracommon.AbstractUVCCameraHandler;
import com.serenegiant.usbcameracommon.UVCCameraHandler;
import com.serenegiant.widget.CameraViewInterface;
import com.serenegiant.widget.UVCCameraTextureView;

import net.cachapa.expandablelayout.ExpandableLayout;

import org.lander.eyemirror.Processing.NativeInterface;
import org.lander.eyemirror.R;
import org.lander.eyemirror.Utils.DataFrame;
import org.lander.eyemirror.Utils.ImageData;
import org.lander.eyemirror.Utils.Mode;
import org.lander.eyemirror.Utils.RecordingHelper;
import org.lander.eyemirror.Utils.UploadStatusListener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import de.morrox.fontinator.FontTextView;

public class HeyebridActivity extends BaseActivity implements CameraDialog.CameraDialogParent, NativeInterface.ProcessingCallback, UploadStatusListener {

    private static boolean DEBUG = false;

    private final String TAG = "hEYEbridActivity";

    /**
     * Constants
     */
    private static final float[] BANDWIDTH_FACTORS = {0.5f, 0.5f};
    private static final int REQUEST_WRITE_STORAGE = 1;
    public static final int CORNEAL_WIDTH = 1280;
    public static final int CORNEAL_HEIGHT = 960;
    public static final int IR_WIDTH = 320; //640; //speeding up processing
    public static final int IR_HEIGHT = 240; //480;

    // for accessing USB and USB camera
    private USBMonitor mUSBMonitor;

    /* Handler for corneal camera */
    private UVCCameraHandler mCornealHandler;
    /* Handler for eye camera */
    private UVCCameraHandler mEyeIRHandler;

    /* ir components */
    private ExpandableLayout mEyeIRExpand;
    private FontTextView mEyeIRLabel, mIRTrackingLabel;
    private CameraViewInterface mEyeCameraViewIR; //mEyeCamerView
    private ImageButton expandEyeIR;
    private Button btnCaptureIR;
    private ImageView mEyeIRCamIndicator, mEyeRecordIndicator;
    private Switch mEyeIRSwitch;//, mEyeIRRecordSwitch;

    /* images showing processed ir and corneal images */
    private ImageView imgBufferIR, imgBufferCorneal;

    //private Button btnCapture;

    /* corneal components */
    private ExpandableLayout mCornealExpand;
    private FontTextView mCornealLabel, mCornealCroppedLabel;
    private CameraViewInterface mCornealCameraView;
    private ImageButton expandCorneal;
    private Button btnCaptureCorneal;
    private ImageView mCornealCamIndicator, mCornealRecordIndicator;
    private Switch mCornealSwitch;//, mCornealRecordSwitch;

    /* control components*/
    private ExpandableLayout mControlExpand;
    private ImageView mUSBIndicator, mStreamIndicator;
    private ImageButton mExpandControl;
    private int imageCounter = 0;
    private Chronometer mChrono;
    private Switch mTrackingSwitch, mStreamingSwitch, mRecordAllSwitch;
    private int mFrameCounter;


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


    /**
     * different flags to indicate if a picture should be taken, content should be streamed or recorded
     */
    private boolean takePicCorneal = false;
    private boolean takePicIR = false;
    private boolean streaming = false;
    private boolean mRecordData = false;
    private boolean storeCorneal = false;
    private boolean mRecordCorneal, mRecordIR;

    /**
     * object for synchronization of photo taking
     */
    private Object synchImgStoring = new Object();
    private Object synchRecordingCorneal = new Object();
    private Object synchRecordingIR = new Object();
    private Object synchRecordingData = new Object();
    private Object synchStoreImg = new Object();

    /**
     * helper to record data
     */
    private RecordingHelper mRecordingHelper;

    private AbstractUVCCameraHandler.CameraCallback mCornealCameraCallback = new AbstractUVCCameraHandler.CameraCallback() {
        @Override
        public void onOpen() {
            //nothing
        }

        @Override
        public void onClose() {
            //nothing
        }

        @Override
        public void onStartPreview() {
            //nothing
        }

        @Override
        public void onStopPreview() {
            //nothing
        }

        @Override
        public void onStartRecording() {
            //nothing
        }

        @Override
        public void onStopRecording() {
            if (mRecordAllSwitch.isChecked()) {
                startRecording();
                cdt.start();
            }
        }

        @Override
        public void onError(Exception e) {
            //nothing
        }
    };


    /* Begin Overrides of ProcessingCallback */
    @Override
    public void onHybridEyeTracked(final Bitmap ir, final Bitmap corneal, final DataFrame df) {
        long ts = System.currentTimeMillis();
        synchronized (synchStoreImg) {
            if (storeCorneal) {
                storeCorneal = false;
                mRecordingHelper.writeImage(corneal, ts, "corneal_cropped_");
                MediaActionSound sound = new MediaActionSound();
                sound.play(MediaActionSound.SHUTTER_CLICK);
                imageCounter++;
            }
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (ir != null) {
                    imgBufferIR.setImageBitmap(ir);
                }
                if (corneal != null) {
                    imgBufferCorneal.setImageBitmap(corneal);
                }
                /*
                if (df.mPupilPoint != null && df.mPupilPoint.normPupil.x > 0 && df.mPupilPoint.normPupil.y > 0) {
                    mGazeCoords.setText(String.format("%.2f", df.mPupilPoint.normPupil.x) + " - " + String.format("%.2f", df.mPupilPoint.normPupil.y));
                }*/
            }
        }, 0);

        synchronized (synchRecordingData) {
            if (mRecordData) {
                mRecordingHelper.update(df);
            }
        }
    }

    @Override
    public void onSceneRegistered(final Bitmap feature) {
        //nothing
    }

    @Override
    public void onGazeMapped() {
        // nothing
    }
    /* End Overrides of ProcessingCallback */


    /**
     * Corneal camera frame callback
     */
    private IFrameCallback mCornealFrameCallback = new IFrameCallback() {
        @Override
        public void onFrame(ByteBuffer frame, double ts) {
            //long ts = System.currentTimeMillis();
            synchronized (synchImgStoring) {
                if (takePicCorneal || streaming) {
                    frame.clear();
                    mBitmapCorneal.copyPixelsFromBuffer(frame);
                    mRecordingHelper.writeImage(mBitmapCorneal, ts, "corneal_");
                    mRecordingHelper.writeCornealTS(ts);
                    takePicCorneal = false;
                }
            }

            synchronized (synchRecordingCorneal) {
                if (mRecordCorneal) {
                    mFrameCounter++;
                    mRecordingHelper.writeCornealTS(ts);
                }
            }

            frame.clear();
            mBitmapCorneal.copyPixelsFromBuffer(frame);
            mNativeInterface.setCorneal(new ImageData(mBitmapCorneal, ts));
        }

    };

    /**
     * IR camera frame callback
     */
    private IFrameCallback mIRFrameCallback = new IFrameCallback() {
        @Override
        public void onFrame(ByteBuffer frame, double ts) {
            //long ts = System.currentTimeMillis();
            synchronized (synchImgStoring) {
                if (takePicIR || streaming) {
                    frame.clear();
                    mBitmap_IR.copyPixelsFromBuffer(frame);
                    mRecordingHelper.writeImage(mBitmap_IR, ts, "ir_");
                    mRecordingHelper.writeIRTS(ts);
                    takePicIR = false;
                }
            }

            synchronized (synchRecordingIR) {
                if (mRecordIR) {
                    mRecordingHelper.writeIRTS(ts);
                }
            }

            frame.clear();
            mBitmap_IR.copyPixelsFromBuffer(frame);
            mNativeInterface.setIR(new ImageData(mBitmap_IR, ts));
        }

    };

    /**
     * task run by the scheduler for streaming and pushing the data to a server
     */
    final Runnable logPushTask = new Runnable() {
        @Override
        public void run() {
            if (mRecordingHelper != null) {
                //pause streaming until zip is completed
                synchronized (synchImgStoring) {
                    streaming = false;
                }

                mRecordingHelper.close(mFrameCounter);
                mFrameCounter = 0;

                //upload everything
                mRecordingHelper.upload(HeyebridActivity.this, HeyebridActivity.this);
            }
        }
    };

    /**
     * Starts the recording of all data sources
     */
    private void startRecording() {
        if (!streaming) {
            System.out.println("start recording*****");
            mRecordingHelper.init();


            mCornealHandler.startRecording("corneal");
            synchronized (synchRecordingCorneal) {
                mRecordCorneal = true;
            }

            mEyeIRHandler.startRecording("eye0");
            synchronized (synchRecordingIR) {
                mRecordIR = true;
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    mCornealRecordIndicator.setImageResource(R.drawable.ic_record_on);
                    mEyeRecordIndicator.setImageResource(R.drawable.ic_record_on);

                }
            });

            mChrono.setBase(SystemClock.elapsedRealtime());
            mChrono.start();

            synchronized (synchRecordingData) {
                mRecordData = true;
            }
        }
    }

    /**
     * Stops the recording of all data sources
     */
    private void stopRecording() {
        synchronized (synchRecordingCorneal) {
            mRecordCorneal = false;
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
                mCornealRecordIndicator.setImageResource(R.drawable.ic_record_off);
                mChrono.setBase(SystemClock.elapsedRealtime());
            }
        });


        mFrameCounter = 0;

        mEyeIRHandler.stopRecording();
        mCornealHandler.stopRecording();
    }

    /**
     * scheduler for streaming and pushing data
     */
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);

    /**
     * scheduler for storing recordings as batches
     */
    //private final ScheduledExecutorService schedulerRecording =
    //       Executors.newScheduledThreadPool(1);

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
        setContentView(R.layout.activity_main2);

        imageCounter = 0;

        //control view
        mControlExpand = (ExpandableLayout) findViewById(R.id.expandable_layout_control);
        mUSBIndicator = (ImageView) findViewById(R.id.usbIndicator);
        mStreamIndicator = (ImageView) findViewById(R.id.streamingIndicator);

        mExpandControl = (ImageButton) findViewById(R.id.expandControlBtn);
        mExpandControl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mControlExpand.isExpanded()) {
                    mControlExpand.collapse();
                } else if (mEyeIRExpand.isExpanded()) {
                    mEyeIRExpand.collapse();
                    mControlExpand.expand();
                } else if (mCornealExpand.isExpanded()) {
                    mCornealExpand.collapse();
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
                    //schedulerRecording.shutdownNow();
                    cdt.cancel();
                    stopRecording();
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

        mStreamingSwitch = (Switch) findViewById(R.id.checkLogging);
        //TODO: implement
        mStreamingSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                synchronized (synchImgStoring) {
                    streaming = isChecked;
                    if (streaming) {
                        requestWritePermission();

                        scheduler.schedule(logPushTask, 60, TimeUnit.SECONDS);
                    } else {
                        scheduler.shutdownNow();
                    }
                }
            }
        });

        //ir components
        imgBufferIR = (ImageView) findViewById(R.id.imgBuffer);
        btnCaptureIR = (Button) findViewById(R.id.btnCaptureIR);
        mEyeIRExpand = (ExpandableLayout) findViewById(R.id.expandable_layout_eye);
        mEyeIRCamIndicator = (ImageView) findViewById(R.id.eyeCamIndicator);
        mEyeRecordIndicator = (ImageView) findViewById(R.id.eyeRecordIndicator);
        mEyeIRSwitch = (Switch) findViewById(R.id.eyeSwitch);
        mEyeIRSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mDialogOnView = R.id.camera_view_eye;
                    if (mEyeIRHandler != null) {
                        if (!mEyeIRHandler.isOpened() && !mEyeIRHandler.isPreviewing()) {

                            CameraDialog.showDialog(HeyebridActivity.this);
                        }
                    }
                } else {
                    System.out.println("unchecked ir****");
                    if (mEyeIRHandler != null) {
                        mEyeIRHandler.close();
                    }

                }
            }
        });
        mIRTrackingLabel = (FontTextView) findViewById(R.id.pupilLbl);
        mEyeIRLabel = (FontTextView) findViewById(R.id.ir_lbl);

        expandEyeIR = (ImageButton) findViewById(R.id.expandEyeBtn);
        expandEyeIR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mEyeIRExpand.isExpanded()) {
                    mEyeIRExpand.collapse();
                } else if (mCornealExpand.isExpanded()) {
                    mCornealExpand.collapse();
                    mEyeIRExpand.expand();
                } else if (mControlExpand.isExpanded()) {
                    mControlExpand.collapse();
                    mEyeIRExpand.expand();
                } else {
                    mEyeIRExpand.expand();
                }
            }
        });

        mEyeCameraViewIR = (CameraViewInterface) findViewById(R.id.camera_view_eye);
        mEyeCameraViewIR.setAspectRatio(IR_WIDTH / (float) IR_HEIGHT);
        //((UVCCameraTextureView)mEyeCameraViewIR).setOnClickListener(mOnClickListener);
        mEyeIRHandler = UVCCameraHandler.createHandler(this, mEyeCameraViewIR, 1, IR_WIDTH, IR_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG, BANDWIDTH_FACTORS[1], mIRFrameCallback);


        // corneal component
        imgBufferCorneal = (ImageView) findViewById(R.id.imgBuffer2);
        mCornealExpand = (ExpandableLayout) findViewById(R.id.expandable_layout_scene);
        mCornealCamIndicator = (ImageView) findViewById(R.id.sceneCamIndicator);
        mCornealRecordIndicator = (ImageView) findViewById(R.id.sceneRecordIndicator);
        mCornealSwitch = (Switch) findViewById(R.id.sceneSwitch);
        mCornealSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mDialogOnView = R.id.camera_view_scene;
                    if (mCornealHandler != null) {
                        if (!mCornealHandler.isOpened()) {

                            CameraDialog.showDialog(HeyebridActivity.this);
                        }
                    }
                } else {
                    System.out.println("unchecked corneal****");
                    if (mCornealHandler != null) {
                        mCornealHandler.close();
                    }
                }
            }
        });

        mCornealLabel = (FontTextView) findViewById(R.id.scene_lbl);
        mCornealCroppedLabel = (FontTextView) findViewById(R.id.mapPupilLbl);

        expandCorneal = (ImageButton) findViewById(R.id.expandSceneBtn);
        expandCorneal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCornealExpand.isExpanded()) {
                    mCornealExpand.collapse();
                } else if (mEyeIRExpand.isExpanded()) {
                    mEyeIRExpand.collapse();
                    mCornealExpand.expand();
                } else if (mControlExpand.isExpanded()) {
                    mControlExpand.collapse();
                    mCornealExpand.expand();
                } else {
                    mCornealExpand.expand();
                }
            }
        });

        btnCaptureCorneal = (Button) findViewById(R.id.btnCaptureCorneal);
        btnCaptureCorneal.setOnClickListener(mOnClickListener);

        mCornealCameraView = (CameraViewInterface) findViewById(R.id.camera_view_scene);
        mCornealCameraView.setAspectRatio(CORNEAL_WIDTH / (float) CORNEAL_HEIGHT);
        // ((UVCCameraTextureView)mCornealCameraView).setOnClickListener(mOnClickListener);
        mCornealHandler = UVCCameraHandler.createHandler(this, mCornealCameraView, 1, CORNEAL_WIDTH, CORNEAL_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG, BANDWIDTH_FACTORS[0], mCornealFrameCallback);

        mCornealHandler.addCallback(mCornealCameraCallback);

        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
        mUSBMonitor.register();

        if (mCornealCameraView != null) {
            mCornealCameraView.onResume();
        }

        if (mEyeCameraViewIR != null) {
            mEyeCameraViewIR.onResume();
        }

        mNativeInterface = new NativeInterface(this, Mode.HEYEBRID);
        mRecordingHelper = new RecordingHelper(Mode.HEYEBRID);

    }


    protected void onStart() {
        super.onStart();

        /*
        if (mCornealCameraView != null) {
            mCornealCameraView.onResume();
        }
        if (mEyeCameraViewIR != null) {
            mEyeCameraViewIR.onResume();
        }*/
    }

    @Override
    protected void onStop() {
        /*
        if (mCornealCameraView != null) {
            mCornealCameraView.onPause();
        }
        if (mEyeCameraViewIR != null) {
            mEyeCameraViewIR.onPause();
        }*/
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        mRecordingHelper.close(mFrameCounter);
        if (mCornealHandler != null) {
            mCornealHandler.close();
            mCornealHandler = null;
        }
        if (mEyeIRHandler != null) {
            mEyeIRHandler.close();
            mEyeIRHandler = null;
        }

        mCornealCameraView = null;
        mEyeCameraViewIR = null;

        if (mUSBMonitor != null) {
            mUSBMonitor.unregister();
            mUSBMonitor.destroy();
            mUSBMonitor = null;
        }
        mNativeInterface.stopNativeThread();

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
                                ActivityCompat.requestPermissions(HeyebridActivity.this,
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
            //do this only if we do not stream the data to a server
            //schedulerRecording.scheduleAtFixedRate(new Runnable() {
            //    @Override
            //    public void run() {
                    startRecording();
                    cdt.start();

            //    }
            //}, 0, 60 + 30, TimeUnit.SECONDS);
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
                    //schedulerRecording.scheduleAtFixedRate(new Runnable() {
                    //    @Override
                    //    public void run() {
                            startRecording();
                            cdt.start();

                    //    }
                    //}, 0, 60 + 30, TimeUnit.SECONDS);

                } else {
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
            Toast.makeText(HeyebridActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mUSBIndicator.setImageResource(R.drawable.ic_usb_connected);
                }
            });
        }

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            if (mDialogOnView == R.id.camera_view_scene && !mCornealHandler.isOpened()) {
                mCornealHandler.open(ctrlBlock);
                final SurfaceTexture st = mCornealCameraView.getSurfaceTexture();
                mCornealHandler.startPreview(new Surface(st));
                mCornealHandler.startGrabbing();
            } else if (mDialogOnView == R.id.camera_view_eye && !mEyeIRHandler.isOpened()) {
                mEyeIRHandler.open(ctrlBlock);
                final SurfaceTexture st = mEyeCameraViewIR.getSurfaceTexture();
                mEyeIRHandler.startPreview(new Surface(st));
                mEyeIRHandler.startGrabbing();
            }

            //update ui
            String[] info = new String[]{device.getProductName(), device.getSerialNumber()};
            int viewID = mDialogOnView;
            updateUI(viewID, info);

            mDialogOnView = -1;
        }

        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
            System.out.println("disconnect: " + device.getDeviceName() + ", " + device.getProductName());
            if ((mCornealHandler != null) && mCornealHandler.isEqual(device)) {

                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        mCornealHandler.close();
                    }
                }, 0);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //mCornealSwitch.setChecked(false);
                        mCornealCamIndicator.setImageResource(R.drawable.ic_cam_off);
                    }
                });
            } else if ((mEyeIRHandler != null) && mEyeIRHandler.isEqual(device)) {

                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                       mEyeIRHandler.close();
                    }
                }, 0);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //mEyeIRSwitch.setChecked(false);
                        mEyeIRCamIndicator.setImageResource(R.drawable.ic_cam_off);
                    }
                });
            }

        }

        @Override
        public void onDettach(final UsbDevice device) {
            Toast.makeText(HeyebridActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
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
                case R.id.btnCaptureCorneal:
                    synchronized (synchStoreImg) {
                        imageCounter++;
                        storeCorneal = true;
                        storeCorneal = true;
                    }
                    break;
                case R.id.camera_view_scene:
                    mDialogOnView = R.id.camera_view_scene;
                    if (mCornealHandler != null) {
                        if (!mCornealHandler.isOpened()) {
                            CameraDialog.showDialog(HeyebridActivity.this);
                        } else {
                            // do nothing
                            //mCornealHandler.close();
                        }
                    }
                    break;
                case R.id.btnCaptureIR:
                    if (mCornealHandler != null && mEyeIRHandler != null) {
                        synchronized (synchImgStoring) {
                            imageCounter++;
                            takePicIR = true;
                        }
                    }
                    break;
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
                if (viewID == R.id.camera_view_scene && (mCornealHandler != null) && !mCornealHandler.isOpened()) {
                    //mCornealLabel.setText(name);
                    mCornealCamIndicator.setImageResource(R.drawable.ic_cam_on);
                    //mCornealSwitch.setChecked(true);
                }
                if (viewID == R.id.camera_view_eye && (mEyeIRHandler != null) && !mEyeIRHandler.isOpened()) {
                    //mEyeIRLabel.setText(name);
                    mEyeIRCamIndicator.setImageResource(R.drawable.ic_cam_on);
                    //mEyeIRSwitch.setChecked(true);
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
    public void onUploadComplete(RecordingHelper recording) {
        mRecordingHelper.deleteLastDir();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStreamIndicator.setImageResource(R.drawable.ic_import_export_on);
            }
        });
        scheduler.schedule(logPushTask, 10, TimeUnit.SECONDS);
    }

    @Override
    public void onUploadFail(RecordingHelper recording, String error) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStreamIndicator.setImageResource(R.drawable.ic_import_export_on);
                Toast.makeText(HeyebridActivity.this, "Upload to server failed", Toast.LENGTH_SHORT);

            }
        });
        scheduler.schedule(logPushTask, 10, TimeUnit.SECONDS);
    }

    @Override
    public void onProgress(RecordingHelper recording, int progress) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStreamIndicator.setImageResource(R.drawable.ic_import_export_on);
            }
        });
    }

    @Override
    public void onZipComplete(RecordingHelper recording) {
        synchronized (synchImgStoring) {
            //continue streaming
            requestWritePermission();
            streaming = true;
        }
    }
}
