#!/usr/bin/python
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
#
# Given an input stream look for Error: and Warning: lines and colorize those to
# the output

import fileinput
import re
import sys

RED = "\033[31m"
YELLOW = "\033[33m"
RESET = "\033[0m"

ERROR = re.compile(r"^Error:")
WARNING = re.compile(r"^Warning:")
STARTS_WITH_WS = re.compile(r"^\s")

for line in fileinput.input():
  if ERROR.match(line):
    print RED + line,
  elif WARNING.match(line):
    print YELLOW + line,
  elif STARTS_WITH_WS.match(line):
    # If the line starts with a space use the same coloring as the previous line
    print line,
  else:
    print RESET + line,
print RESET

