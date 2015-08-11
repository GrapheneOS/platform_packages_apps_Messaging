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

package com.android.messaging.ui.mediapicker;

import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;

import com.android.messaging.ui.mediapicker.CameraManager.CameraWrapper;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.HashMap;
import java.util.Map;

class MockCameraFactory {
    private static Map<Camera, CameraInfo> sCameraInfos = new HashMap<Camera, CameraInfo>();

    public static Camera createCamera(int facing) {
        Camera camera = Mockito.mock(Camera.class);
        CameraInfo cameraInfo = new CameraInfo();
        cameraInfo.facing = facing;
        sCameraInfos.put(camera, cameraInfo);
        return camera;
    }

    public static void getCameraInfo(Camera camera, CameraInfo outCameraInfo) {
        CameraInfo cameraInfo = sCameraInfos.get(camera);
        outCameraInfo.facing = cameraInfo.facing;
        outCameraInfo.orientation = cameraInfo.orientation;
        outCameraInfo.canDisableShutterSound = cameraInfo.canDisableShutterSound;
    }

    public static CameraWrapper createCameraWrapper(final Camera... cameras) {
        CameraWrapper wrapper = Mockito.mock(CameraWrapper.class);
        Mockito.when(wrapper.getNumberOfCameras()).thenReturn(cameras.length);
        Mockito.when(wrapper.open(Mockito.anyInt())).then(new Answer<Camera>() {
            @Override
            public Camera answer(InvocationOnMock invocation) {
                return cameras[(Integer) invocation.getArguments()[0]];
            }
        });
        Mockito.doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                getCameraInfo(
                        cameras[(Integer) invocation.getArguments()[0]],
                        (CameraInfo) invocation.getArguments()[1]
                );
                return null;
            }
        }).when(wrapper).getCameraInfo(Mockito.anyInt(), Mockito.any(CameraInfo.class));
        return wrapper;
    }

    public static void cleanup() {
        sCameraInfos.clear();
    }
}
