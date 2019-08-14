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

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res
LOCAL_USE_AAPT2 := true

LOCAL_STATIC_ANDROID_LIBRARIES := \
    androidx.core_core \
    androidx.media_media \
    androidx.legacy_legacy-support-core-utils \
    androidx.legacy_legacy-support-core-ui \
    androidx.fragment_fragment \
    androidx.appcompat_appcompat \
    androidx.palette_palette \
    androidx.recyclerview_recyclerview \
    androidx.legacy_legacy-support-v13 \
    colorpicker \
    libchips \
    libphotoviewer

LOCAL_STATIC_JAVA_LIBRARIES := \
    androidx.annotation_annotation \
    android-common \
    android-common-framesequence \
    com.android.vcard \
    guava \
    libphonenumber

include $(LOCAL_PATH)/version.mk

LOCAL_AAPT_FLAGS += --version-name "$(version_name_package)"
LOCAL_AAPT_FLAGS += --version-code $(version_code_package)

ifdef TARGET_BUILD_APPS
    LOCAL_JNI_SHARED_LIBRARIES := libframesequence libgiftranscode
else
    LOCAL_REQUIRED_MODULES:= libframesequence libgiftranscode
endif

LOCAL_PROGUARD_ENABLED := obfuscation optimization

LOCAL_PROGUARD_FLAG_FILES := proguard.flags
ifeq (eng,$(TARGET_BUILD_VARIANT))
    LOCAL_PROGUARD_FLAG_FILES += proguard-test.flags
else
    LOCAL_PROGUARD_FLAG_FILES += proguard-release.flags
endif

LOCAL_PACKAGE_NAME := messaging

LOCAL_CERTIFICATE := platform

LOCAL_SDK_VERSION := current

LOCAL_PRODUCT_MODULE := true

LOCAL_MODULE_PATH := $(TARGET_OUT_PRODUCT_APPS)

LOCAL_COMPATIBILITY_SUITE := general-tests

include $(BUILD_PACKAGE)

include $(call all-makefiles-under, $(LOCAL_PATH))
