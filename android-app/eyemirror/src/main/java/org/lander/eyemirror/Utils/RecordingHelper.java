package org.lander.eyemirror.Utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.JsonWriter;
import android.util.Log;

import com.serenegiant.encoder.MediaMuxerWrapper;

import net.gotev.uploadservice.MultipartUploadRequest;
import net.gotev.uploadservice.ServerResponse;
import net.gotev.uploadservice.UploadInfo;
import net.gotev.uploadservice.UploadNotificationConfig;
import net.gotev.uploadservice.UploadStatusDelegate;

import org.lander.eyemirror.activities.PupilActivity;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by christian on 10.07.17.
 */

public class RecordingHelper {

    private FileOutputStream mSceneOut, mIROut, mCornealOut, mMetaInfoOut;
    private Mode mode;
    public String guid;
    private JsonWriter writer;
    public String directoryPath;
    private String currentZipFile;
    private boolean uploaded;
    private String lastDirectory;
    private int frameCounter;

    private ConcurrentLinkedQueue<DataFrame> dataFrames;
    private ConcurrentLinkedQueue<String> cornealTimes, eyeTimes, worldTimes;

    private Thread loggingThread;

    private Runnable loggingTask = new Runnable() {
        @Override
        public void run() {
            try {
                directoryPath = MediaMuxerWrapper.getDir().toString();
                //System.out.println("data out: " + out.toString());
                writer = new JsonWriter(new OutputStreamWriter(new FileOutputStream(new File(directoryPath, "pupil_data"))));
                mMetaInfoOut = new FileOutputStream(new File(directoryPath,"info.csv"));

                switch (mode) {
                    case HEYEBRID:
                        mCornealOut = new FileOutputStream(new File(directoryPath, "corenal_timestamps.txt"));
                        mIROut = new FileOutputStream(new File(directoryPath, "eye0_timestamps.txt"));
                        break;
                    case HEYEBRID3C:
                        mCornealOut = new FileOutputStream(new File(directoryPath, "corenal_timestamps.txt"));
                        mIROut = new FileOutputStream(new File(directoryPath, "eye0_timestamps.txt"));
                        mSceneOut = new FileOutputStream(new File(directoryPath, "world_timestamps.txt"));
                        break;
                    case PUPIL:
                        mIROut = new FileOutputStream(new File(directoryPath, "eye0_timestamps.txt"));
                        mSceneOut = new FileOutputStream(new File(directoryPath, "world_timestamps.txt"));
                        break;
                    default:
                        break;
                }

                mMetaInfoOut.write(("key,value\n").getBytes());
                mMetaInfoOut.write(("Recording Name," + MediaMuxerWrapper.getDateTimeString() + "\n").getBytes());
                mMetaInfoOut.write(("Start Date," + MediaMuxerWrapper.getDateTimeString() + "\n").getBytes());
                mMetaInfoOut.write(("Start Time," + MediaMuxerWrapper.getTimeString() + "\n").getBytes());
                mMetaInfoOut.write(("System Info,Android\n").getBytes());

                writer.setIndent("  ");
                writer.beginObject();

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }


            while (!loggingThread.isInterrupted()) {
                logDataFrame(dataFrames.poll());

                logSceneTS(worldTimes.poll());
                logCornealTS(cornealTimes.poll());
                logEyeTS(eyeTimes.poll());
            }


            //doing flushing after interruption
            try {
                if (mMetaInfoOut != null) {
                    mMetaInfoOut.write(("World Camera Frames," + frameCounter + "\n").getBytes());
                    mMetaInfoOut.write(("World Camera Resolution," + PupilActivity.SCENE_WIDTH + "x" + PupilActivity.SCENE_HEIGHT + "\n").getBytes());

                    long diff = new GregorianCalendar().getTime().getTime() - MediaMuxerWrapper.getStart().getTime();
                    long mills = Math.abs(diff);

                    int Hours = (int) (mills / (1000 * 60 * 60));
                    int Mins = (int) (mills / (1000 * 60)) % 60;
                    long Secs = (int) (mills / 1000) % 60;

                    String timediff = Hours + ":" + Mins + ":" + Secs;

                    mMetaInfoOut.write(("Duration Time," + timediff + "\n").getBytes());
                    mMetaInfoOut.write(("Capture Software Version,0.0.1\n").getBytes());

                    mMetaInfoOut.flush();
                    mMetaInfoOut.close();
                    mMetaInfoOut = null;
                }

                if (mIROut != null) {
                    mIROut.flush();
                    mIROut.close();
                    mIROut = null;
                }
                if (mSceneOut != null) {
                    mSceneOut.flush();
                    mSceneOut.close();
                    mSceneOut = null;
                }
                if (mCornealOut != null) {
                    mCornealOut.flush();
                    mCornealOut.close();
                    mCornealOut = null;
                }

                if (writer != null) {
                    writer.endObject();
                    writer.flush();
                    writer.close();
                    writer = null;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    public RecordingHelper(Mode mode) {
        this.mode = mode;
    }

    private String zip(Context context) {
        guid =  UUID.randomUUID().toString();
        String zip_file = directoryPath + "/" +  guid + ".zip";
        this.currentZipFile = zip_file;
        Compress c = new Compress((new File(directoryPath)).list(),zip_file);
        c.zip();
        return zip_file;
    }

    public void deleteLastDir() {
        // delete all files in the folder
        File file = new File(lastDirectory);
        boolean deleted = false;
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                deleted &= f.delete();
            }
        }
        Log.d("RECORDING","all files deleted: "+ deleted);
    }

    public void deleteAll() {

    }

    private void uploadCompleted() {
        this.uploaded = true;

    }

    public void upload(final UploadStatusListener listener, Context context){
        final Context tmp_content = context;
        final UploadStatusListener tmp = listener;
        lastDirectory = directoryPath;

        new Thread(new Runnable() {
            public void run() {
                // zip it
                String zipFilepath = zip(tmp_content);
                tmp.onZipComplete(RecordingHelper.this);

                try {
                    String uploadId =
                            new MultipartUploadRequest(tmp_content, "some URL") //TODO:FIXME
                                    // starting from 3.1+, you can also use content:// URI string instead of absolute file
                                    .addFileToUpload(zipFilepath, "zip_file")

                                    .setNotificationConfig(new UploadNotificationConfig())
                                    .setDelegate(new UploadStatusDelegate() {
                                        @Override
                                        public void onProgress(Context context, UploadInfo uploadInfo) {
                                            listener.onProgress(RecordingHelper.this, uploadInfo.getProgressPercent());
                                        }

                                        @Override
                                        public void onError(Context context, UploadInfo uploadInfo, Exception exception) {
                                            listener.onUploadFail(RecordingHelper.this, exception.toString());
                                        }

                                        @Override
                                        public void onCompleted(Context context, UploadInfo uploadInfo, ServerResponse serverResponse) {
                                            RecordingHelper.this.uploadCompleted();
                                            listener.onUploadComplete(RecordingHelper.this);
                                            // delete the zip file
                                            File file = new File(currentZipFile);
                                            boolean deleted = file.delete();
                                        }

                                        @Override
                                        public void onCancelled(Context context, UploadInfo uploadInfo) {
                                            listener.onUploadFail(RecordingHelper.this, uploadInfo.toString());
                                        }
                                    })
                                    .addParameter("uuid",guid)
                                    .addHeader("Authorization", "Token "+ "") //TODO:FIXME
                                    .setMaxRetries(2)
                                    .startUpload();
                } catch (Exception exc) {
                    Log.e("AndroidUploadService", exc.getMessage(), exc);
                }



            }


        }).start();


    }

    public void init() {
        this.dataFrames = new ConcurrentLinkedQueue<>();
        this.cornealTimes = new ConcurrentLinkedQueue<>();
        this.eyeTimes = new ConcurrentLinkedQueue<>();
        this.worldTimes = new ConcurrentLinkedQueue<>();

        this.loggingThread = new Thread(loggingTask);

        loggingThread.start();
    }

    public void writeImage(Bitmap mBitmapCorneal, double ts, String prefix) {
        File outputFile = MediaMuxerWrapper.getCaptureFile(Environment.DIRECTORY_DCIM, prefix + ts + ".jpg");
        try {
            BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(outputFile));
            try {
                mBitmapCorneal.compress(Bitmap.CompressFormat.JPEG, 80, os);
                os.flush();
            } catch (final IOException e) {
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }


    private void writePupilData(PupilPoint pp) throws IOException {
        writer.beginObject();
        writer.name("diameter").value(pp.diameter);
        writer.name("confidence").value(pp.confidence);
        writer.name("norm_pos");
        writer.beginArray();
        writer.value(pp.normPupil.x);
        writer.value(pp.normPupil.y);
        writer.endArray();
        writer.name("ellipse");
        writer.beginObject();
        writer.name("axes");
        writer.beginArray();
        writer.value(pp.axes.x);
        writer.value(pp.axes.y);
        writer.endArray();
        writer.name("angle").value(pp.angle);
        writer.name("center");
        writer.beginArray();
        writer.value(pp.centerPupil.x);
        writer.value(pp.centerPupil.y);
        writer.endArray();
        writer.endObject();
        writer.name("topic").value("pupil");
        writer.name("method").value("2d c++");
        writer.name("timestamp").value(pp.timestamp);
        writer.name("id").value(0);
        writer.endObject();
    }

    private void writeCornealData(CornealPoint cp) throws IOException {
        writer.beginObject();
        writer.name("norm_pos");
        writer.beginArray();
        writer.value(cp.getGazeXNorm());
        writer.value(cp.getGazeYNorm());
        writer.endArray();
        writer.name("abs_pos");
        writer.beginArray();
        writer.value(cp.cornealPupil.x);
        writer.value(cp.cornealPupil.y);
        writer.endArray();
        writer.name("roi_size");
        writer.beginArray();
        writer.value(cp.mRectW);
        writer.value(cp.mRectH);
        writer.endArray();
        writer.name("roi_tl");
        writer.beginArray();
        writer.value(cp.topLeft.x);
        writer.value(cp.topLeft.y);
        writer.endArray();
        writer.name("timestamp").value(cp.mTimeStamp);
        writer.endObject();
    }

    public void update(DataFrame df) {
        if (df != null) {
            dataFrames.add(df);
        }
    }

    private synchronized void logDataFrame(DataFrame df) {
        if (writer != null && df != null) {
            try {
                writer.name("gaze_positions");
                writer.beginArray();
                if (df.mGazePoint != null) {
                    writer.beginObject();
                    writer.name("topic").value("gaze");
                    writer.name("norm_pos").value("(" + df.mGazePoint.getGazeXNorm() + "," + df.mGazePoint.getGazeYNorm() + ")");
                    writer.name("confidence").value(df.mPupilPoint.confidence);
                    writer.name("base_data");
                    writer.beginArray();
                    writePupilData(df.mPupilPoint);
                    writer.endArray();
                    writer.name("timestamp").value(df.mPupilPoint.timestamp);
                    writer.endObject();
                }
                writer.endArray();


                writer.name("pupil_positions");
                writer.beginArray();
                if (df.mPupilPoint != null) {
                    //Log.d("RecordingHelper", "write pupil point");
                    writePupilData(df.mPupilPoint);
                }
                writer.endArray();

                writer.name("corneal_positions");
                writer.beginArray();
                if (df.mCornealPoint != null) {
                    //Log.d("RecordingHelper", "write corneal point");
                    writeCornealData(df.mCornealPoint);
                }
                writer.endArray();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private synchronized void logSceneTS(String ts) {
        try {

            if (mSceneOut != null && ts != null) {
                mSceneOut.write((ts).getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void logEyeTS(String ts) {
        try {
            if (mIROut != null && ts != null) {
                mIROut.write((ts).getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void logCornealTS(String ts) {
        try {

            if (mCornealOut != null && ts != null) {
                mCornealOut.write((ts).getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeSceneTS(double ts) {
        worldTimes.add(ts+"\n");
    }

    public void writeIRTS(double ts) {
        eyeTimes.add(ts +"\n");
    }

    public void writeCornealTS(double ts) {
        cornealTimes.add(ts +"\n");
    }

    public void close(int frameCounter) {
        if (loggingThread != null) {
            this.frameCounter = frameCounter;
            loggingThread.interrupt();
        }
    }
}

