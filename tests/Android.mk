#  Copyright (C) 2015 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests

# Only compile source java files in this apk.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := messagingtests

LOCAL_INSTRUMENTATION_FOR := messaging

LOCAL_PROGUARD_ENABLED := disabled

# Matching ../Android.mk
LOCAL_SDK_VERSION := current

LOCAL_COMPATIBILITY_SUITE := general-tests

LOCAL_CERTIFICATE := platform

LOCAL_STATIC_JAVA_LIBRARIES := \
    mockito-target

LOCAL_JAVA_LIBRARIES := \
    android.test.mock.stubs \
    android.test.runner.stubs \
    android.test.base.stubs \


include $(BUILD_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))
