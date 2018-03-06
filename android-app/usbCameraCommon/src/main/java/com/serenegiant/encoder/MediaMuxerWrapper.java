package com.serenegiant.encoder;
/*
 * UVCCamera
 * library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2016 saki t_saki@serenegiant.com
 *
 * File name: MediaMuxerWrapper.java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
 * Files in the jni/libjpeg, jni/libusb, jin/libuvc, jni/rapidjson folder may have a different license, see the respective files.
*/

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

public class MediaMuxerWrapper {
	private static final boolean DEBUG = true;	// TODO set false on release
	private static final String TAG = "MediaMuxerWrapper";

	private static final String DIR_NAME = "hEYEbrid";
    private static final SimpleDateFormat mDateTimeFormat = new SimpleDateFormat("yyyy_MM_dd", Locale.US);
	private static final SimpleDateFormat mDateTimeFormat2 = new SimpleDateFormat("H:m:s", Locale.US);

	private String mOutputPath;
	private final MediaMuxer mMediaMuxer;	// API >= 18
	private int mEncoderCount, mStatredCount;
	private boolean mIsStarted;
	private MediaEncoder mVideoEncoder, mAudioEncoder;

	private static String CURRENT_DIR = "";

	private static Date mStartDate;

	/**
	 * Constructor
	 * @param ext extension of output file
	 * @throws IOException
	 */
	public MediaMuxerWrapper(String ext) throws IOException {
		if (TextUtils.isEmpty(ext)) ext = ".mp4";
		try {
			//mOutputPath = getCaptureFile(Environment.DIRECTORY_DCIM, ext).toString();
			mOutputPath = new File(getDir(), ext).toString();
		} catch (final NullPointerException e) {
			throw new RuntimeException("This app has no permission of writing external storage");
		}

		Log.d(TAG, "video path=" + mOutputPath);
		mMediaMuxer = new MediaMuxer(mOutputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
		mEncoderCount = mStatredCount = 0;
		mIsStarted = false;
	}

	public String getOutputPath() {
		return mOutputPath;
	}

	public static Date getStart() {
		return mStartDate;
	}

	public void prepare() throws IOException {
		if (mVideoEncoder != null)
			mVideoEncoder.prepare();
		if (mAudioEncoder != null)
			mAudioEncoder.prepare();
	}

	public void startRecording() {
		if (mVideoEncoder != null)
			mVideoEncoder.startRecording();
		if (mAudioEncoder != null)
			mAudioEncoder.startRecording();
		mStartDate = new GregorianCalendar().getTime();
	}

	public void stopRecording() {
		if (mVideoEncoder != null) {
			if (DEBUG) Log.v(TAG, "stopRecording video");
			mVideoEncoder.stopRecording();
		}
		mVideoEncoder = null;
		if (mAudioEncoder != null) {
			if (DEBUG) Log.v(TAG, "stopRecording audio");
			mAudioEncoder.stopRecording();
		}
		mAudioEncoder = null;
		CURRENT_DIR = "";
	}

	public synchronized boolean isStarted() {
		return mIsStarted;
	}

//**********************************************************************
//**********************************************************************
	/**
	 * assign encoder to this calss. this is called from encoder.
	 * @param encoder instance of MediaVideoEncoder or MediaAudioEncoder
	 */
	/*package*/ void addEncoder(final MediaEncoder encoder) {
		if (encoder instanceof MediaVideoEncoder) {
			if (mVideoEncoder != null)
				throw new IllegalArgumentException("Video encoder already added.");
			mVideoEncoder = encoder;
		} else if (encoder instanceof MediaSurfaceEncoder) {
				if (mVideoEncoder != null)
					throw new IllegalArgumentException("Video encoder already added.");
				mVideoEncoder = encoder;
		} else if (encoder instanceof MediaVideoBufferEncoder) {
			if (mVideoEncoder != null)
				throw new IllegalArgumentException("Video encoder already added.");
			mVideoEncoder = encoder;
		} else if (encoder instanceof MediaAudioEncoder) {
			if (mAudioEncoder != null)
				throw new IllegalArgumentException("Video encoder already added.");
			mAudioEncoder = encoder;
		} else
			throw new IllegalArgumentException("unsupported encoder");
		mEncoderCount = (mVideoEncoder != null ? 1 : 0) + (mAudioEncoder != null ? 1 : 0);
	}

	/**
	 * request start recording from encoder
	 * @return true when muxer is ready to write
	 */
	/*package*/ synchronized boolean start() {
		if (DEBUG) Log.v(TAG,  "start:");
		mStatredCount++;
		if ((mEncoderCount > 0) && (mStatredCount == mEncoderCount)) {
			mMediaMuxer.start();
			mIsStarted = true;
			notifyAll();
			if (DEBUG) Log.v(TAG,  "MediaMuxer started:");
		}
		return mIsStarted;
	}

	/**
	 * request stop recording from encoder when encoder received EOS
	*/
	/*package*/ synchronized void stop() {
		if (DEBUG) Log.v(TAG,  "stop:mStatredCount=" + mStatredCount);
		mStatredCount--;
		if ((mEncoderCount > 0) && (mStatredCount <= 0)) {
			try {
				mMediaMuxer.stop();
			} catch (final Exception e) {
				Log.w(TAG, e);
			}
			mIsStarted = false;
			if (DEBUG) Log.v(TAG,  "MediaMuxer stopped:");
		}
	}

	/**
	 * assign encoder to muxer
	 * @param format
	 * @return minus value indicate error
	 */
	/*package*/ synchronized int addTrack(final MediaFormat format) {
		if (mIsStarted)
			throw new IllegalStateException("muxer already started");
		final int trackIx = mMediaMuxer.addTrack(format);
		if (DEBUG) Log.i(TAG, "addTrack:trackNum=" + mEncoderCount + ",trackIx=" + trackIx + ",format=" + format);
		return trackIx;
	}

	/**
	 * write encoded data to muxer
	 * @param trackIndex
	 * @param byteBuf
	 * @param bufferInfo
	 */
	/*package*/ synchronized void writeSampleData(final int trackIndex, final ByteBuffer byteBuf, final MediaCodec.BufferInfo bufferInfo) {
		if (mStatredCount > 0)
			mMediaMuxer.writeSampleData(trackIndex, byteBuf, bufferInfo);
	}

//**********************************************************************
//**********************************************************************
    /**
     * generate output file
     * @param type Environment.DIRECTORY_MOVIES / Environment.DIRECTORY_DCIM etc.
     * @param ext .mp4(.m4a for audio) or .png
     * @return return null when this app has no writing permission to external storage.
     */
    public static final File getCaptureFile(final String type, final String ext) {
		final File dir = new File(Environment.getExternalStoragePublicDirectory(type), DIR_NAME+"/"+getDateTimeString());
		Log.d(TAG, "path=" + dir.toString());
		dir.mkdirs();
        if (dir.canWrite()) {
        	return new File(dir, ext);
        }
    	return null;
    }

    public static File getDir() {
		Log.d(TAG, "call");
		if (CURRENT_DIR.equalsIgnoreCase("")) {
			final File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), DIR_NAME + "/" + getDateTimeString());
			dir.mkdirs();
			if (dir.canWrite()) {
				int idx = 0;
				File[] sub = dir.listFiles();
				for (File f : sub) {
					if (f.isDirectory()) {
						idx = Integer.parseInt(f.getName());
					}
				}
				idx++;
				final File finalDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), DIR_NAME + "/" + getDateTimeString() + "/" + idx);
				finalDir.mkdirs();
				CURRENT_DIR = finalDir.toString();
				return finalDir;
			}
			return null;
		} else {
			return new File(CURRENT_DIR);
		}
	}

    /**
     * get current date and time as String
     * @return
     */
    public static final String getDateTimeString() {
    	final GregorianCalendar now = new GregorianCalendar();
    	return mDateTimeFormat.format(now.getTime());
    }

	public static final String getTimeString() {
		final GregorianCalendar now = new GregorianCalendar();
		return mDateTimeFormat2.format(now.getTime());
	}

}
