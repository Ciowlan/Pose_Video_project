/*
 * Copyright 2013 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.devdroid.posedetectiononvideo;

import android.util.Log;


public class SpeedControlCallback implements MoviePlayer.FrameCallback {
    private static final String TAG = "";
    private static final boolean CHECK_SLEEP_TIME = false;

    private static final long ONE_MILLION = 1000000L;

    private long mPrevPresentUsec;
    private long mPrevMonoUsec;
    private long mFixedFrameDurationUsec;
    private boolean mLoopReset;

    /**
     * Sets a fixed playback rate.  If set, this will ignore the presentation time stamp
     * in the video file.  Must be called before playback thread starts.
     */
    public void setFixedPlaybackRate(int fps) {
        mFixedFrameDurationUsec = ONE_MILLION / fps;
    }

    // runs on decode thread
    @Override
    public void preRender(long presentationTimeUsec) {

        if (mPrevMonoUsec == 0) {
            // Latch current values, then return immediately.
            mPrevMonoUsec = System.nanoTime() / 1000;
            mPrevPresentUsec = presentationTimeUsec;
        } else {
            // Compute the desired time delta between the previous frame and this frame.
            long frameDelta;
            if (mLoopReset) {

                mPrevPresentUsec = presentationTimeUsec - ONE_MILLION / 30;
                mLoopReset = false;
            }
            if (mFixedFrameDurationUsec != 0) {
                // Caller requested a fixed frame rate.  Ignore PTS.
                frameDelta = mFixedFrameDurationUsec;
            } else {
                frameDelta = presentationTimeUsec - mPrevPresentUsec;
            }
            if (frameDelta < 0) {
                Log.w(TAG, "Weird, video times went backward");
                frameDelta = 0;
            } else if (frameDelta == 0) {
                // This suggests a possible bug in movie generation.
                Log.i(TAG, "Warning: current frame and previous frame had same timestamp");
            } else if (frameDelta > 10 * ONE_MILLION) {
                // Inter-frame times could be arbitrarily long.  For this player, we want
                // to alert the developer that their movie might have issues (maybe they
                // accidentally output timestamps in nsec rather than usec).
                Log.i(TAG, "Inter-frame pause was " + (frameDelta / ONE_MILLION) +
                        "sec, capping at 5 sec");
                frameDelta = 5 * ONE_MILLION;
            }

            long desiredUsec = mPrevMonoUsec + frameDelta;  // when we want to wake up
            long nowUsec = System.nanoTime() / 1000;
            while (nowUsec < (desiredUsec - 100) /*&& mState == RUNNING*/) {

                long sleepTimeUsec = desiredUsec - nowUsec;
                if (sleepTimeUsec > 500000) {
                    sleepTimeUsec = 500000;
                }
                try {
                    if (CHECK_SLEEP_TIME) {
                        long startNsec = System.nanoTime();
                        Thread.sleep(sleepTimeUsec / 1000, (int) (sleepTimeUsec % 1000) * 1000);
                        long actualSleepNsec = System.nanoTime() - startNsec;
                        Log.d(TAG, "sleep=" + sleepTimeUsec + " actual=" + (actualSleepNsec/1000) +
                                " diff=" + (Math.abs(actualSleepNsec / 1000 - sleepTimeUsec)) +
                                " (usec)");
                    } else {
                        Thread.sleep(sleepTimeUsec / 1000, (int) (sleepTimeUsec % 1000) * 1000);
                    }
                } catch (InterruptedException ie) {}
                nowUsec = System.nanoTime() / 1000;
            }

            // Advance times using calculated time values, not the post-sleep monotonic
            // clock time, to avoid drifting.
            mPrevMonoUsec += frameDelta;
            mPrevPresentUsec += frameDelta;
        }
    }

    // runs on decode thread
    @Override
    public void postRender() {}

    @Override
    public void loopReset() {
        mLoopReset = true;
    }
}
