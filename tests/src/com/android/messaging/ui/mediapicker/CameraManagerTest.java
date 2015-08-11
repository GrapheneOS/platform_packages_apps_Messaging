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
import android.os.AsyncTask;
import android.test.suitebuilder.annotation.SmallTest;
import com.android.messaging.BugleTestCase;
import com.android.messaging.ui.mediapicker.CameraManager.CameraWrapper;
import org.mockito.InOrder;
import org.mockito.Mockito;

@SmallTest
public class CameraManagerTest extends BugleTestCase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Force each test to set up a camera wrapper to match their needs
        CameraManager.setCameraWrapper(null);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        MockCameraFactory.cleanup();
    }

    public void testNoCameraDeviceGetInfo() {
        CameraManager.setCameraWrapper(MockCameraFactory.createCameraWrapper());
        assertEquals(false, CameraManager.get().hasAnyCamera());
        assertEquals(false, CameraManager.get().hasFrontAndBackCamera());
        try {
            CameraManager.get().selectCamera(CameraInfo.CAMERA_FACING_BACK);
            fail("selectCamera should have thrown");
        } catch (AssertionError e) {
        }
    }

    public void testFrontFacingOnlyGetInfo() {
        CameraManager.setCameraWrapper(MockCameraFactory.createCameraWrapper(
                MockCameraFactory.createCamera(CameraInfo.CAMERA_FACING_FRONT)
        ));
        assertEquals(true, CameraManager.get().hasAnyCamera());
        assertEquals(false, CameraManager.get().hasFrontAndBackCamera());
        CameraManager.get().selectCamera(CameraInfo.CAMERA_FACING_FRONT);
        assertEquals(CameraInfo.CAMERA_FACING_FRONT, CameraManager.get().getCameraInfo().facing);
        CameraManager.get().selectCamera(CameraInfo.CAMERA_FACING_BACK);
        assertEquals(CameraInfo.CAMERA_FACING_FRONT, CameraManager.get().getCameraInfo().facing);
    }

    public void testBackFacingOnlyGetInfo() {
        CameraManager.setCameraWrapper(MockCameraFactory.createCameraWrapper(
                MockCameraFactory.createCamera(CameraInfo.CAMERA_FACING_BACK)
        ));
        assertEquals(true, CameraManager.get().hasAnyCamera());
        assertEquals(false, CameraManager.get().hasFrontAndBackCamera());
        CameraManager.get().selectCamera(CameraInfo.CAMERA_FACING_FRONT);
        assertEquals(CameraInfo.CAMERA_FACING_BACK, CameraManager.get().getCameraInfo().facing);
        CameraManager.get().selectCamera(CameraInfo.CAMERA_FACING_BACK);
        assertEquals(CameraInfo.CAMERA_FACING_BACK, CameraManager.get().getCameraInfo().facing);
    }

    public void testFrontAndBackGetInfo() {
        CameraManager.setCameraWrapper(MockCameraFactory.createCameraWrapper(
                MockCameraFactory.createCamera(CameraInfo.CAMERA_FACING_FRONT),
                MockCameraFactory.createCamera(CameraInfo.CAMERA_FACING_BACK)
        ));
        assertEquals(true, CameraManager.get().hasAnyCamera());
        assertEquals(true, CameraManager.get().hasFrontAndBackCamera());
        CameraManager.get().selectCamera(CameraInfo.CAMERA_FACING_FRONT);
        assertEquals(CameraInfo.CAMERA_FACING_FRONT, CameraManager.get().getCameraInfo().facing);
        CameraManager.get().selectCamera(CameraInfo.CAMERA_FACING_BACK);
        assertEquals(CameraInfo.CAMERA_FACING_BACK, CameraManager.get().getCameraInfo().facing);
    }

    public void testSwapCamera() {
        CameraManager.setCameraWrapper(MockCameraFactory.createCameraWrapper(
                MockCameraFactory.createCamera(CameraInfo.CAMERA_FACING_FRONT),
                MockCameraFactory.createCamera(CameraInfo.CAMERA_FACING_BACK)
        ));
        CameraManager.get().selectCamera(CameraInfo.CAMERA_FACING_FRONT);
        assertEquals(CameraInfo.CAMERA_FACING_FRONT, CameraManager.get().getCameraInfo().facing);
        CameraManager.get().swapCamera();
        assertEquals(CameraInfo.CAMERA_FACING_BACK, CameraManager.get().getCameraInfo().facing);
    }

    public void testOpenCamera() {
        Camera backCamera = MockCameraFactory.createCamera(CameraInfo.CAMERA_FACING_BACK);
        Camera frontCamera = MockCameraFactory.createCamera(CameraInfo.CAMERA_FACING_FRONT);
        CameraWrapper wrapper = MockCameraFactory.createCameraWrapper(frontCamera, backCamera);
        CameraManager.setCameraWrapper(wrapper);
        CameraManager.get().selectCamera(CameraInfo.CAMERA_FACING_BACK);
        CameraManager.get().openCamera();
        CameraManager.get().openCamera();
        CameraManager.get().openCamera();
        waitForPendingAsyncTasks();
        Mockito.verify(wrapper, Mockito.never()).open(0);
        Mockito.verify(wrapper).open(1);
        Mockito.verify(wrapper, Mockito.never()).release(frontCamera);
        Mockito.verify(wrapper, Mockito.never()).release(backCamera);
        CameraManager.get().swapCamera();
        waitForPendingAsyncTasks();
        Mockito.verify(wrapper).open(0);
        Mockito.verify(wrapper).open(1);
        Mockito.verify(wrapper, Mockito.never()).release(frontCamera);
        Mockito.verify(wrapper).release(backCamera);
        InOrder inOrder = Mockito.inOrder(wrapper);
        inOrder.verify(wrapper).open(1);
        inOrder.verify(wrapper).release(backCamera);
        inOrder.verify(wrapper).open(0);
    }

    private void waitForPendingAsyncTasks() {
        try {
            final Object lockObject = new Object();

            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    return null;
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    super.onPostExecute(aVoid);
                    synchronized (lockObject) {
                        lockObject.notifyAll();
                    }
                }
            }.execute();

            synchronized (lockObject) {
                lockObject.wait(500);
            }
        } catch (InterruptedException e) {
            fail();
        }
    }
}
