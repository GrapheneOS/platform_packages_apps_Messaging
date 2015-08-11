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

# Check to see if we need to force proguard to re-run, typically after using tapas to
# switch to/from eng builds. This is determined by comparing the flag files used in the previous
# build. If the flag files have changed, proguard is rerun.

# If the LOCAL_MODULE being setup isn't a build target, then don't run ForceProguard.
ifneq (,$(findstring $(LOCAL_MODULE), $(TARGET_BUILD_APPS)))

PREVIOUS_FLAG_FILES_USED_DIR := $(call local-intermediates-dir,1)

# If the local intermediates dir doesn't exist, ForceProguard won't work.
ifneq ($(wildcard $(PREVIOUS_FLAG_FILES_USED_DIR)),)
PREVIOUS_FLAG_FILES_USED_FILE := $(PREVIOUS_FLAG_FILES_USED_DIR)/previous_proguard_flag_files
PREVIOUS_FLAG_FILES_USED := $(if $(wildcard $(PREVIOUS_FLAG_FILES_USED_FILE)), \
                                $(shell cat $(PREVIOUS_FLAG_FILES_USED_FILE)))

ifneq ($(strip $(PREVIOUS_FLAG_FILES_USED)), $(strip $(LOCAL_PROGUARD_FLAG_FILES)))
$(info *** Flag files used for proguard have changed; forcing proguard to rerun.)
$(shell touch $(LOCAL_PATH)/proguard.flags)
$(shell echo $(LOCAL_PROGUARD_FLAG_FILES) > $(PREVIOUS_FLAG_FILES_USED_FILE))
endif

endif
# End local intermediates directory existence check

endif
# End LOCAL_MODULE is a build target check
