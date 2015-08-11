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

# Android lint checks for common code/resource errors specific to Android

# Lint tool expects the intermediates dir to match the project name Bugle rather
# than $(LOCAL_PACKAGE_NAME) which is messaging
# Create a symbolic link to redirect the tool
$(LOCAL_PACKAGE_NAME)BUGLE_RENAME := $(subst $(LOCAL_PACKAGE_NAME)_intermediates,Bugle_intermediates,$(intermediates.COMMON))
$($(LOCAL_PACKAGE_NAME)BUGLE_RENAME): SOURCE_PATH := $(abspath $(intermediates.COMMON)/)
$($(LOCAL_PACKAGE_NAME)BUGLE_RENAME): DST_PATH := $($(LOCAL_PACKAGE_NAME)BUGLE_RENAME)
$($(LOCAL_PACKAGE_NAME)BUGLE_RENAME) :
	ln -f -s $(SOURCE_PATH) $(DST_PATH)

# Lint tool expects api-versions.xml from the SDK to be in development/sdk but
# it's not.  Create a symbolic link to the android SDK to fix it
API_VERSIONS_XML := $(abspath development/sdk/api-versions.xml)
$(API_VERSIONS_XML):
	ln -f -s $(abspath prebuilts/fullsdk/linux/platform-tools/api/api-versions.xml) $(API_VERSIONS_XML)

# The output xml file from the lint tool
$(LOCAL_PACKAGE_NAME)LINT_XML := $(intermediates.COMMON)/$(LOCAL_PACKAGE_NAME)_android_lint.xml

# The transformed text file from the output xml
$(LOCAL_PACKAGE_NAME)LINT_TXT := $(intermediates.COMMON)/$(LOCAL_PACKAGE_NAME)_android_lint.txt

# Creates the output xml from the lint tool by running the linting tool if the
# package has been updated
$($(LOCAL_PACKAGE_NAME)LINT_XML): PRIVATE_PATH := $(LOCAL_PATH)
$($(LOCAL_PACKAGE_NAME)LINT_XML): LINT_CMD = $(LINT) --quiet -Wall --disable UnusedIds,UnusedResources,MissingTranslation $(PRIVATE_PATH)
$($(LOCAL_PACKAGE_NAME)LINT_XML) : $(LOCAL_BUILT_MODULE) $($(LOCAL_PACKAGE_NAME)BUGLE_RENAME) $(API_VERSIONS_XML)
	$(LINT_CMD) --xml $@ > /dev/null

# Creates the transformed text file from the output xml by running an xslt on it
# which filters out issues from the support library and formats it for console
# output
$($(LOCAL_PACKAGE_NAME)LINT_TXT): PRIVATE_PATH := $(LOCAL_PATH)
$($(LOCAL_PACKAGE_NAME)LINT_TXT): INPUT := $($(LOCAL_PACKAGE_NAME)LINT_XML)
$($(LOCAL_PACKAGE_NAME)LINT_TXT): XSLT_CMD = xsltproc $(PRIVATE_PATH)/build/android_lint.xslt $(INPUT)
$($(LOCAL_PACKAGE_NAME)LINT_TXT) : $($(LOCAL_PACKAGE_NAME)LINT_XML)
	$(hide) $(XSLT_CMD) > $@

# The root of the lint rule which just prints the lint errors txt file to the
# console
$(LOCAL_PACKAGE_NAME)lint: PRIVATE_PATH := $(LOCAL_PATH)
$(LOCAL_PACKAGE_NAME)lint :: $($(LOCAL_PACKAGE_NAME)LINT_TXT)
	$(hide) $(PRIVATE_PATH)/build/colorize_errors.py < $<

