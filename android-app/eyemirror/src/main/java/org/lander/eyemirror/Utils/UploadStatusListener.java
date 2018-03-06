package org.lander.eyemirror.Utils;

/**
 * Created by christian on 17.07.17.
 */

public interface UploadStatusListener {

    public void onUploadComplete(RecordingHelper recording);
    public void onUploadFail(RecordingHelper recording, String error);
    public void onProgress(RecordingHelper recording, int progress);
    public void onZipComplete(RecordingHelper recording);

}
