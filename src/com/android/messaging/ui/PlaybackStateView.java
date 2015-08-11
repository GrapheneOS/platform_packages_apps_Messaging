/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.messaging.ui;

/**
 * An interface for a UI element ("View") that reflects the playback state of a piece of media
 * content. It needs to support the ability to take common playback commands (play, pause, stop,
 * restart) and reflect the state in UI (through timer or progress bar etc.)
 */
public interface PlaybackStateView {
    /**
     * Restart the playback.
     */
    void restart();

    /**
     * Reset ("stop") the playback to the starting position.
     */
    void reset();

    /**
     * Resume the playback, or start it if it hasn't been started yet.
     */
    void resume();

    /**
     * Pause the playback.
     */
    void pause();
}
