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

package com.android.messaging.util;

import com.google.common.base.CharMatcher;

/**
 * Parsing the email address
 */
public final class EmailAddress {
    private static final CharMatcher ANY_WHITESPACE = CharMatcher.anyOf(
            " \t\n\r\f\u000B\u0085\u2028\u2029\u200D\uFFEF\uFFFD\uFFFE\uFFFF");
    private static final CharMatcher EMAIL_ALLOWED_CHARS = CharMatcher.inRange((char) 0, (char) 31)
            .or(CharMatcher.is((char) 127))
            .or(CharMatcher.anyOf(" @,:<>"))
            .negate();

    /**
     * Helper method that checks whether the input text is valid email address.
     * TODO: This creates a new EmailAddress object each time
     * Need to make it more lightweight by pulling out the validation code into a static method.
     */
    public static boolean isValidEmail(final String emailText) {
        return new EmailAddress(emailText).isValid();
    }

    /**
     * Parses the specified email address. Internationalized addresses are treated as invalid.
     *
     * @param emailString A string representing just an email address. It should
     * not contain any other tokens. <code>"Name&lt;foo@example.org>"</code> won't be valid.
     */
    public EmailAddress(final String emailString) {
        this(emailString, false);
    }

    /**
     * Parses the specified email address.
     *
     * @param emailString A string representing just an email address. It should
     * not contain any other tokens. <code>"Name&lt;foo@example.org>"</code> won't be valid.
     * @param i18n Accept an internationalized address if it is true.
     */
    public EmailAddress(final String emailString, final boolean i18n) {
        allowI18n = i18n;
        valid = parseEmail(emailString);
    }

    /**
     * Parses the specified email address. Internationalized addresses are treated as invalid.
     *
     * @param user A string representing the username in the email prior to the '@' symbol
     * @param host A string representing the host following the '@' symbol
     */
    public EmailAddress(final String user, final String host) {
        this(user, host, false);
    }

    /**
     * Parses the specified email address.
     *
     * @param user A string representing the username in the email prior to the '@' symbol
     * @param host A string representing the host following the '@' symbol
     * @param i18n Accept an internationalized address if it is true.
     */
    public EmailAddress(final String user, final String host, final boolean i18n) {
        allowI18n = i18n;
        this.user = user;
        setHost(host);
    }

    protected boolean parseEmail(final String emailString) {
        // check for null
        if (emailString == null) {
            return false;
        }

        // Check for an '@' character. Get the last one, in case the local part is
        // quoted. See http://b/1944742.
        final int atIndex = emailString.lastIndexOf('@');
        if ((atIndex <= 0) || // no '@' character in the email address
                              // or @ on the first position
                (atIndex == (emailString.length() - 1))) { // last character, no host
            return false;
        }

        user = emailString.substring(0, atIndex);
        host = emailString.substring(atIndex + 1);

        return isValidInternal();
    }

    @Override
    public String toString() {
        return user + "@" + host;
    }

    /**
     * Ensure the email address is valid, conforming to current RFC2821 and
     * RFC2822 guidelines (although some iffy characters, like ! and ;, are
     * allowed because they are not technically prohibited in the RFC)
     */
    private boolean isValidInternal() {
        if ((user == null) || (host == null)) {
            return false;
        }

        if ((user.length() == 0) || (host.length() == 0)) {
            return false;
        }

        // check for white space in the host
        if (ANY_WHITESPACE.indexIn(host) >= 0) {
            return false;
        }

        // ensure the host is above the minimum length
        if (host.length() < 4) {
            return false;
        }

        final int firstDot = host.indexOf('.');

        // ensure host contains at least one dot
        if (firstDot == -1) {
            return false;
        }

        // check if the host contains two continuous dots.
        if (host.indexOf("..") >= 0) {
            return false;
        }

        // check if the first host char is a dot.
        if (host.charAt(0) == '.') {
            return false;
        }

        final int secondDot = host.indexOf(".", firstDot + 1);

        // if there's a dot at the end, there needs to be a second dot
        if (host.charAt(host.length() - 1) == '.' && secondDot == -1) {
            return false;
        }

        // Host must not have any disallowed characters; allowI18n dictates whether
        // host must be ASCII.
        if (!EMAIL_ALLOWED_CHARS.matchesAllOf(host)
                || (!allowI18n && !CharMatcher.ASCII.matchesAllOf(host))) {
            return false;
        }

        if (user.startsWith("\"")) {
            if (!isQuotedUserValid()) {
                return false;
            }
        } else {
            // check for white space in the user
            if (ANY_WHITESPACE.indexIn(user) >= 0) {
                return false;
            }

            // the user cannot contain two continuous dots
            if (user.indexOf("..") >= 0) {
                return false;
            }

            // User must not have any disallowed characters; allow I18n dictates whether
            // user must be ASCII.
            if (!EMAIL_ALLOWED_CHARS.matchesAllOf(user)
                    || (!allowI18n && !CharMatcher.ASCII.matchesAllOf(user))) {
                return false;
            }
        }
        return true;
    }

    private boolean isQuotedUserValid() {
        final int limit = user.length() - 1;
        if (limit < 1 || !user.endsWith("\"")) {
            return false;
        }

        // Unusual loop bounds (looking only at characters between the outer quotes,
        // not at either quote character). Plus, i is manipulated within the loop.
        for (int i = 1; i < limit; ++i) {
            final char ch = user.charAt(i);
            if (ch == '"' || ch == 127
                    // No non-whitespace control chars:
                    || (ch < 32 && !ANY_WHITESPACE.matches(ch))
                    // No non-ASCII chars, unless i18n is in effect:
                    || (ch >= 128 && !allowI18n)) {
                return false;
            } else if (ch == '\\') {
                if (i + 1 < limit) {
                    ++i; // Skip the quoted character
                } else {
                    // We have a trailing backslash -- so it can't be quoting anything.
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public boolean equals(final Object otherObject) {
        // Do an instance check first as an optimization.
        if (this == otherObject) {
            return true;
        }
        if (otherObject instanceof EmailAddress) {
            final EmailAddress otherAddress = (EmailAddress) otherObject;
            return toString().equals(otherAddress.toString());
        }
        return false;
    }

    @Override
    public int hashCode() {
        // Arbitrary hash code as a function of both host and user.
        return toString().hashCode();
    }

    // accessors
    public boolean isValid() {
        return valid;
    }

    public String getUser() {
        return user;
    }

    public String getHost() {
        return host;
    }

    // used to change the host on an email address and rechecks validity

    /**
     * Changes the host name of the email address and rechecks the address'
     * validity. Exercise caution when storing EmailAddress instances in
     * hash-keyed collections. Calling setHost() with a different host name will
     * change the return value of hashCode.
     *
     * @param hostName The new host name of the email address.
     */
    public void setHost(final String hostName) {
        host = hostName;
        valid = isValidInternal();
    }

    protected boolean valid = false;
    protected String user = null;
    protected String host = null;
    protected boolean allowI18n = false;
}
