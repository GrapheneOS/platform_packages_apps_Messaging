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

# The config file to use when checking style issues
STYLE_CONFIG := $(LOCAL_PATH)/build/gcheckstyle/tools/java/checkstyle/googlestyle-5.0.xml

# The jar file to use to perform the checking
STYLE_JAR := $(LOCAL_PATH)/build/gcheckstyle/google-style-checker_deploy.jar

# The output file to cache the results of style error checking
$(LOCAL_PACKAGE_NAME)STYLE_ERRORS_TXT := $(intermediates.COMMON)/$(LOCAL_PACKAGE_NAME)_style_errors.txt

# The set of input files to check
$(LOCAL_PACKAGE_NAME)JAVA_FILES = $(shell find $(LOCAL_PATH)/src/ -name '*.java')

# Updates the JAR file with the new config if the config has changed
# The config file has to be packaged into the jar, and jar uf command expects the current working directory
# to match the jar structure
$(STYLE_JAR): PRIVATE_PATH := $(LOCAL_PATH)
$(STYLE_JAR) : $(STYLE_CONFIG)
	$(hide) pushd $(PRIVATE_PATH)/build/gcheckstyle && \
	jar uf google-style-checker_deploy.jar tools/java/checkstyle/googlestyle-5.0.xml && \
	popd

# Rebuilds the style errors text if the style checker or any of the java files have changed
# FLAG: It may be more efficient to cache individual file results rather than grouping them all together
$($(LOCAL_PACKAGE_NAME)STYLE_ERRORS_TXT): SOURCES := $($(LOCAL_PACKAGE_NAME)JAVA_FILES)
$($(LOCAL_PACKAGE_NAME)STYLE_ERRORS_TXT) : $($(LOCAL_PACKAGE_NAME)JAVA_FILES) $(STYLE_JAR)
	$(hide) -/usr/local/buildtools/java/jdk/bin/java -jar $(STYLE_JAR) $(SOURCES) > $@

# The root of the lint rule which just prints the style errors txt file to the console
$(LOCAL_PACKAGE_NAME)lint :: $($(LOCAL_PACKAGE_NAME)STYLE_ERRORS_TXT)
	$(hide) $(PRIVATE_PATH)/build/process_style_output.py -omit $(abspath $(PRIVATE_PATH))/ < $< | $(PRIVATE_PATH)/build/colorize_errors.py

