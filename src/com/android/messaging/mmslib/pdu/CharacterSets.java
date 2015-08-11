/*
 * Copyright (C) 2007 Esmertec AG.
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.messaging.mmslib.pdu;

import android.support.v4.util.SimpleArrayMap;
import android.util.SparseArray;

import java.io.UnsupportedEncodingException;

public class CharacterSets {
    /**
     * IANA assigned MIB enum numbers.
     *
     * From wap-230-wsp-20010705-a.pdf
     * Any-charset = <Octet 128>
     * Equivalent to the special RFC2616 charset value "*"
     *
     * Link to all the charsets: http://www.iana.org/assignments/character-sets/character-sets.xhtml
     * Use Charset.availableCharsets() to see if a potential charset is supported in java:
     *     private void dumpCharsets() {
     *         logvv("dumpCharsets");
     *         SortedMap<String, Charset> charsets = Charset.availableCharsets();
     *         for (Entry<String, Charset> entry : charsets.entrySet()) {
     *             String key = entry.getKey();
     *             Charset value = entry.getValue();
     *             logvv("charset key: " + key + " value: " + value);
     *         }
     *     }
     *
     * As of March 21, 2014, here is the list from dumpCharsets:
     * Adobe-Standard-Encoding value: java.nio.charset.CharsetICU[Adobe-Standard-Encoding]
     * Big5 value: java.nio.charset.CharsetICU[Big5]
     * Big5-HKSCS value: java.nio.charset.CharsetICU[Big5-HKSCS]
     * BOCU-1 value: java.nio.charset.CharsetICU[BOCU-1]
     * CESU-8 value: java.nio.charset.CharsetICU[CESU-8]
     * cp1363 value: java.nio.charset.CharsetICU[cp1363]
     * cp851 value: java.nio.charset.CharsetICU[cp851]
     * cp864 value: java.nio.charset.CharsetICU[cp864]
     * EUC-JP value: java.nio.charset.CharsetICU[EUC-JP]
     * EUC-KR value: java.nio.charset.CharsetICU[EUC-KR]
     * GB18030 value: java.nio.charset.CharsetICU[GB18030]
     * GBK value: java.nio.charset.CharsetICU[GBK]
     * hp-roman8 value: java.nio.charset.CharsetICU[hp-roman8]
     * HZ-GB-2312 value: java.nio.charset.CharsetICU[HZ-GB-2312]
     * IBM-Thai value: java.nio.charset.CharsetICU[IBM-Thai]
     * IBM00858 value: java.nio.charset.CharsetICU[IBM00858]
     * IBM01140 value: java.nio.charset.CharsetICU[IBM01140]
     * IBM01141 value: java.nio.charset.CharsetICU[IBM01141]
     * IBM01142 value: java.nio.charset.CharsetICU[IBM01142]
     * IBM01143 value: java.nio.charset.CharsetICU[IBM01143]
     * IBM01144 value: java.nio.charset.CharsetICU[IBM01144]
     * IBM01145 value: java.nio.charset.CharsetICU[IBM01145]
     * IBM01146 value: java.nio.charset.CharsetICU[IBM01146]
     * IBM01147 value: java.nio.charset.CharsetICU[IBM01147]
     * IBM01148 value: java.nio.charset.CharsetICU[IBM01148]
     * IBM01149 value: java.nio.charset.CharsetICU[IBM01149]
     * IBM037 value: java.nio.charset.CharsetICU[IBM037]
     * IBM1026 value: java.nio.charset.CharsetICU[IBM1026]
     * IBM1047 value: java.nio.charset.CharsetICU[IBM1047]
     * IBM273 value: java.nio.charset.CharsetICU[IBM273]
     * IBM277 value: java.nio.charset.CharsetICU[IBM277]
     * IBM278 value: java.nio.charset.CharsetICU[IBM278]
     * IBM280 value: java.nio.charset.CharsetICU[IBM280]
     * IBM284 value: java.nio.charset.CharsetICU[IBM284]
     * IBM285 value: java.nio.charset.CharsetICU[IBM285]
     * IBM290 value: java.nio.charset.CharsetICU[IBM290]
     * IBM297 value: java.nio.charset.CharsetICU[IBM297]
     * IBM420 value: java.nio.charset.CharsetICU[IBM420]
     * IBM424 value: java.nio.charset.CharsetICU[IBM424]
     * IBM437 value: java.nio.charset.CharsetICU[IBM437]
     * IBM500 value: java.nio.charset.CharsetICU[IBM500]
     * IBM775 value: java.nio.charset.CharsetICU[IBM775]
     * IBM850 value: java.nio.charset.CharsetICU[IBM850]
     * IBM852 value: java.nio.charset.CharsetICU[IBM852]
     * IBM855 value: java.nio.charset.CharsetICU[IBM855]
     * IBM857 value: java.nio.charset.CharsetICU[IBM857]
     * IBM860 value: java.nio.charset.CharsetICU[IBM860]
     * IBM861 value: java.nio.charset.CharsetICU[IBM861]
     * IBM862 value: java.nio.charset.CharsetICU[IBM862]
     * IBM863 value: java.nio.charset.CharsetICU[IBM863]
     * IBM865 value: java.nio.charset.CharsetICU[IBM865]
     * IBM866 value: java.nio.charset.CharsetICU[IBM866]
     * IBM868 value: java.nio.charset.CharsetICU[IBM868]
     * IBM869 value: java.nio.charset.CharsetICU[IBM869]
     * IBM870 value: java.nio.charset.CharsetICU[IBM870]
     * IBM871 value: java.nio.charset.CharsetICU[IBM871]
     * IBM918 value: java.nio.charset.CharsetICU[IBM918]
     * ISO-2022-CN value: java.nio.charset.CharsetICU[ISO-2022-CN]
     * ISO-2022-CN-EXT value: java.nio.charset.CharsetICU[ISO-2022-CN-EXT]
     * ISO-2022-JP value: java.nio.charset.CharsetICU[ISO-2022-JP]
     * ISO-2022-JP-1 value: java.nio.charset.CharsetICU[ISO-2022-JP-1]
     * ISO-2022-JP-2 value: java.nio.charset.CharsetICU[ISO-2022-JP-2]
     * ISO-2022-KR value: java.nio.charset.CharsetICU[ISO-2022-KR]
     * ISO-8859-1 value: java.nio.charset.CharsetICU[ISO-8859-1]
     * ISO-8859-10 value: java.nio.charset.CharsetICU[ISO-8859-10]
     * ISO-8859-13 value: java.nio.charset.CharsetICU[ISO-8859-13]
     * ISO-8859-14 value: java.nio.charset.CharsetICU[ISO-8859-14]
     * ISO-8859-15 value: java.nio.charset.CharsetICU[ISO-8859-15]
     * ISO-8859-2 value: java.nio.charset.CharsetICU[ISO-8859-2]
     * ISO-8859-3 value: java.nio.charset.CharsetICU[ISO-8859-3]
     * ISO-8859-4 value: java.nio.charset.CharsetICU[ISO-8859-4]
     * ISO-8859-5 value: java.nio.charset.CharsetICU[ISO-8859-5]
     * ISO-8859-6 value: java.nio.charset.CharsetICU[ISO-8859-6]
     * ISO-8859-7 value: java.nio.charset.CharsetICU[ISO-8859-7]
     * ISO-8859-8 value: java.nio.charset.CharsetICU[ISO-8859-8]
     * ISO-8859-9 value: java.nio.charset.CharsetICU[ISO-8859-9]
     * KOI8-R value: java.nio.charset.CharsetICU[KOI8-R]
     * KOI8-U value: java.nio.charset.CharsetICU[KOI8-U]
     * macintosh value: java.nio.charset.CharsetICU[macintosh]
     * SCSU value: java.nio.charset.CharsetICU[SCSU]
     * Shift_JIS value: java.nio.charset.CharsetICU[Shift_JIS]
     * TIS-620 value: java.nio.charset.CharsetICU[TIS-620]
     * US-ASCII value: java.nio.charset.CharsetICU[US-ASCII]
     * UTF-16 value: java.nio.charset.CharsetICU[UTF-16]
     * UTF-16BE value: java.nio.charset.CharsetICU[UTF-16BE]
     * UTF-16LE value: java.nio.charset.CharsetICU[UTF-16LE]
     * UTF-32 value: java.nio.charset.CharsetICU[UTF-32]
     * UTF-32BE value: java.nio.charset.CharsetICU[UTF-32BE]
     * UTF-32LE value: java.nio.charset.CharsetICU[UTF-32LE]
     * UTF-7 value: java.nio.charset.CharsetICU[UTF-7]
     * UTF-8 value: java.nio.charset.CharsetICU[UTF-8]
     * windows-1250 value: java.nio.charset.CharsetICU[windows-1250]
     * windows-1251 value: java.nio.charset.CharsetICU[windows-1251]
     * windows-1252 value: java.nio.charset.CharsetICU[windows-1252]
     * windows-1253 value: java.nio.charset.CharsetICU[windows-1253]
     * windows-1254 value: java.nio.charset.CharsetICU[windows-1254]
     * windows-1255 value: java.nio.charset.CharsetICU[windows-1255]
     * windows-1256 value: java.nio.charset.CharsetICU[windows-1256]
     * windows-1257 value: java.nio.charset.CharsetICU[windows-1257]
     * windows-1258 value: java.nio.charset.CharsetICU[windows-1258]
     * x-compound-text value: java.nio.charset.CharsetICU[x-compound-text]
     * x-ebcdic-xml-us value: java.nio.charset.CharsetICU[x-ebcdic-xml-us]
     * x-gsm-03.38-2000 value: java.nio.charset.CharsetICU[x-gsm-03.38-2000]
     * x-ibm-1047-s390 value: java.nio.charset.CharsetICU[x-ibm-1047-s390]
     * x-ibm-1125_P100-1997 value: java.nio.charset.CharsetICU[x-ibm-1125_P100-1997]
     * x-ibm-1129_P100-1997 value: java.nio.charset.CharsetICU[x-ibm-1129_P100-1997]
     * x-ibm-1130_P100-1997 value: java.nio.charset.CharsetICU[x-ibm-1130_P100-1997]
     * x-ibm-1131_P100-1997 value: java.nio.charset.CharsetICU[x-ibm-1131_P100-1997]
     * x-ibm-1132_P100-1998 value: java.nio.charset.CharsetICU[x-ibm-1132_P100-1998]
     * x-ibm-1133_P100-1997 value: java.nio.charset.CharsetICU[x-ibm-1133_P100-1997]
     * x-ibm-1137_P100-1999 value: java.nio.charset.CharsetICU[x-ibm-1137_P100-1999]
     * x-ibm-1140-s390 value: java.nio.charset.CharsetICU[x-ibm-1140-s390]
     * x-ibm-1141-s390 value: java.nio.charset.CharsetICU[x-ibm-1141-s390]
     * x-ibm-1142-s390 value: java.nio.charset.CharsetICU[x-ibm-1142-s390]
     * x-ibm-1143-s390 value: java.nio.charset.CharsetICU[x-ibm-1143-s390]
     * x-ibm-1144-s390 value: java.nio.charset.CharsetICU[x-ibm-1144-s390]
     * x-ibm-1145-s390 value: java.nio.charset.CharsetICU[x-ibm-1145-s390]
     * x-ibm-1146-s390 value: java.nio.charset.CharsetICU[x-ibm-1146-s390]
     * x-ibm-1147-s390 value: java.nio.charset.CharsetICU[x-ibm-1147-s390]
     * x-ibm-1148-s390 value: java.nio.charset.CharsetICU[x-ibm-1148-s390]
     * x-ibm-1149-s390 value: java.nio.charset.CharsetICU[x-ibm-1149-s390]
     * x-ibm-1153-s390 value: java.nio.charset.CharsetICU[x-ibm-1153-s390]
     * x-ibm-1154_P100-1999 value: java.nio.charset.CharsetICU[x-ibm-1154_P100-1999]
     * x-ibm-1155_P100-1999 value: java.nio.charset.CharsetICU[x-ibm-1155_P100-1999]
     * x-ibm-1156_P100-1999 value: java.nio.charset.CharsetICU[x-ibm-1156_P100-1999]
     * x-ibm-1157_P100-1999 value: java.nio.charset.CharsetICU[x-ibm-1157_P100-1999]
     * x-ibm-1158_P100-1999 value: java.nio.charset.CharsetICU[x-ibm-1158_P100-1999]
     * x-ibm-1160_P100-1999 value: java.nio.charset.CharsetICU[x-ibm-1160_P100-1999]
     * x-ibm-1162_P100-1999 value: java.nio.charset.CharsetICU[x-ibm-1162_P100-1999]
     * x-ibm-1164_P100-1999 value: java.nio.charset.CharsetICU[x-ibm-1164_P100-1999]
     * x-ibm-1250_P100-1995 value: java.nio.charset.CharsetICU[x-ibm-1250_P100-1995]
     * x-ibm-1251_P100-1995 value: java.nio.charset.CharsetICU[x-ibm-1251_P100-1995]
     * x-ibm-1252_P100-2000 value: java.nio.charset.CharsetICU[x-ibm-1252_P100-2000]
     * x-ibm-1253_P100-1995 value: java.nio.charset.CharsetICU[x-ibm-1253_P100-1995]
     * x-ibm-1254_P100-1995 value: java.nio.charset.CharsetICU[x-ibm-1254_P100-1995]
     * x-ibm-1255_P100-1995 value: java.nio.charset.CharsetICU[x-ibm-1255_P100-1995]
     * x-ibm-1256_P110-1997 value: java.nio.charset.CharsetICU[x-ibm-1256_P110-1997]
     * x-ibm-1257_P100-1995 value: java.nio.charset.CharsetICU[x-ibm-1257_P100-1995]
     * x-ibm-1258_P100-1997 value: java.nio.charset.CharsetICU[x-ibm-1258_P100-1997]
     * x-ibm-12712-s390 value: java.nio.charset.CharsetICU[x-ibm-12712-s390]
     * x-ibm-12712_P100-1998 value: java.nio.charset.CharsetICU[x-ibm-12712_P100-1998]
     * x-ibm-1373_P100-2002 value: java.nio.charset.CharsetICU[x-ibm-1373_P100-2002]
     * x-ibm-1383_P110-1999 value: java.nio.charset.CharsetICU[x-ibm-1383_P110-1999]
     * x-ibm-1386_P100-2001 value: java.nio.charset.CharsetICU[x-ibm-1386_P100-2001]
     * x-ibm-16684_P110-2003 value: java.nio.charset.CharsetICU[x-ibm-16684_P110-2003]
     * x-ibm-16804-s390 value: java.nio.charset.CharsetICU[x-ibm-16804-s390]
     * x-ibm-16804_X110-1999 value: java.nio.charset.CharsetICU[x-ibm-16804_X110-1999]
     * x-ibm-25546 value: java.nio.charset.CharsetICU[x-ibm-25546]
     * x-ibm-33722_P12A_P12A-2009_U2 value:
     *     java.nio.charset.CharsetICU[x-ibm-33722_P12A_P12A-2009_U2]
     * x-ibm-37-s390 value: java.nio.charset.CharsetICU[x-ibm-37-s390]
     * x-ibm-4517_P100-2005 value: java.nio.charset.CharsetICU[x-ibm-4517_P100-2005]
     * x-ibm-4899_P100-1998 value: java.nio.charset.CharsetICU[x-ibm-4899_P100-1998]
     * x-ibm-4909_P100-1999 value: java.nio.charset.CharsetICU[x-ibm-4909_P100-1999]
     * x-ibm-4971_P100-1999 value: java.nio.charset.CharsetICU[x-ibm-4971_P100-1999]
     * x-ibm-5123_P100-1999 value: java.nio.charset.CharsetICU[x-ibm-5123_P100-1999]
     * x-ibm-5351_P100-1998 value: java.nio.charset.CharsetICU[x-ibm-5351_P100-1998]
     * x-ibm-5352_P100-1998 value: java.nio.charset.CharsetICU[x-ibm-5352_P100-1998]
     * x-ibm-5353_P100-1998 value: java.nio.charset.CharsetICU[x-ibm-5353_P100-1998]
     * x-ibm-5478_P100-1995 value: java.nio.charset.CharsetICU[x-ibm-5478_P100-1995]
     * x-ibm-803_P100-1999 value: java.nio.charset.CharsetICU[x-ibm-803_P100-1999]
     * x-ibm-813_P100-1995 value: java.nio.charset.CharsetICU[x-ibm-813_P100-1995]
     * x-ibm-8482_P100-1999 value: java.nio.charset.CharsetICU[x-ibm-8482_P100-1999]
     * x-ibm-901_P100-1999 value: java.nio.charset.CharsetICU[x-ibm-901_P100-1999]
     * x-ibm-902_P100-1999 value: java.nio.charset.CharsetICU[x-ibm-902_P100-1999]
     * x-ibm-9067_X100-2005 value: java.nio.charset.CharsetICU[x-ibm-9067_X100-2005]
     * x-ibm-916_P100-1995 value: java.nio.charset.CharsetICU[x-ibm-916_P100-1995]
     * x-IBM1006 value: java.nio.charset.CharsetICU[x-IBM1006]
     * x-IBM1025 value: java.nio.charset.CharsetICU[x-IBM1025]
     * x-IBM1097 value: java.nio.charset.CharsetICU[x-IBM1097]
     * x-IBM1098 value: java.nio.charset.CharsetICU[x-IBM1098]
     * x-IBM1112 value: java.nio.charset.CharsetICU[x-IBM1112]
     * x-IBM1122 value: java.nio.charset.CharsetICU[x-IBM1122]
     * x-IBM1123 value: java.nio.charset.CharsetICU[x-IBM1123]
     * x-IBM1124 value: java.nio.charset.CharsetICU[x-IBM1124]
     * x-IBM1153 value: java.nio.charset.CharsetICU[x-IBM1153]
     * x-IBM1363 value: java.nio.charset.CharsetICU[x-IBM1363]
     * x-IBM1364 value: java.nio.charset.CharsetICU[x-IBM1364]
     * x-IBM1371 value: java.nio.charset.CharsetICU[x-IBM1371]
     * x-IBM1388 value: java.nio.charset.CharsetICU[x-IBM1388]
     * x-IBM1390 value: java.nio.charset.CharsetICU[x-IBM1390]
     * x-IBM1399 value: java.nio.charset.CharsetICU[x-IBM1399]
     * x-IBM33722 value: java.nio.charset.CharsetICU[x-IBM33722]
     * x-IBM720 value: java.nio.charset.CharsetICU[x-IBM720]
     * x-IBM737 value: java.nio.charset.CharsetICU[x-IBM737]
     * x-IBM856 value: java.nio.charset.CharsetICU[x-IBM856]
     * x-IBM867 value: java.nio.charset.CharsetICU[x-IBM867]
     * x-IBM875 value: java.nio.charset.CharsetICU[x-IBM875]
     * x-IBM922 value: java.nio.charset.CharsetICU[x-IBM922]
     * x-IBM930 value: java.nio.charset.CharsetICU[x-IBM930]
     * x-IBM933 value: java.nio.charset.CharsetICU[x-IBM933]
     * x-IBM935 value: java.nio.charset.CharsetICU[x-IBM935]
     * x-IBM937 value: java.nio.charset.CharsetICU[x-IBM937]
     * x-IBM939 value: java.nio.charset.CharsetICU[x-IBM939]
     * x-IBM942 value: java.nio.charset.CharsetICU[x-IBM942]
     * x-IBM943 value: java.nio.charset.CharsetICU[x-IBM943]
     * x-IBM949 value: java.nio.charset.CharsetICU[x-IBM949]
     * x-IBM949C value: java.nio.charset.CharsetICU[x-IBM949C]
     * x-IBM950 value: java.nio.charset.CharsetICU[x-IBM950]
     * x-IBM954 value: java.nio.charset.CharsetICU[x-IBM954]
     * x-IBM964 value: java.nio.charset.CharsetICU[x-IBM964]
     * x-IBM970 value: java.nio.charset.CharsetICU[x-IBM970]
     * x-IBM971 value: java.nio.charset.CharsetICU[x-IBM971]
     * x-IMAP-mailbox-name value: java.nio.charset.CharsetICU[x-IMAP-mailbox-name]
     * x-iscii-be value: java.nio.charset.CharsetICU[x-iscii-be]
     * x-iscii-gu value: java.nio.charset.CharsetICU[x-iscii-gu]
     * x-iscii-ka value: java.nio.charset.CharsetICU[x-iscii-ka]
     * x-iscii-ma value: java.nio.charset.CharsetICU[x-iscii-ma]
     * x-iscii-or value: java.nio.charset.CharsetICU[x-iscii-or]
     * x-iscii-pa value: java.nio.charset.CharsetICU[x-iscii-pa]
     * x-iscii-ta value: java.nio.charset.CharsetICU[x-iscii-ta]
     * x-iscii-te value: java.nio.charset.CharsetICU[x-iscii-te]
     * x-ISCII91 value: java.nio.charset.CharsetICU[x-ISCII91]
     * x-ISO-2022-CN-CNS value: java.nio.charset.CharsetICU[x-ISO-2022-CN-CNS]
     * x-iso-8859-11 value: java.nio.charset.CharsetICU[x-iso-8859-11]
     * x-JavaUnicode value: java.nio.charset.CharsetICU[x-JavaUnicode]
     * x-JavaUnicode2 value: java.nio.charset.CharsetICU[x-JavaUnicode2]
     * x-JIS7 value: java.nio.charset.CharsetICU[x-JIS7]
     * x-JIS8 value: java.nio.charset.CharsetICU[x-JIS8]
     * x-LMBCS-1 value: java.nio.charset.CharsetICU[x-LMBCS-1]
     * x-mac-centraleurroman value: java.nio.charset.CharsetICU[x-mac-centraleurroman]
     * x-mac-cyrillic value: java.nio.charset.CharsetICU[x-mac-cyrillic]
     * x-mac-greek value: java.nio.charset.CharsetICU[x-mac-greek]
     * x-mac-turkish value: java.nio.charset.CharsetICU[x-mac-turkish]
     * x-MS950-HKSCS value: java.nio.charset.CharsetICU[x-MS950-HKSCS]
     * x-UnicodeBig value: java.nio.charset.CharsetICU[x-UnicodeBig]
     * x-UTF-16LE-BOM value: java.nio.charset.CharsetICU[x-UTF-16LE-BOM]
     * x-UTF16_OppositeEndian value: java.nio.charset.CharsetICU[x-UTF16_OppositeEndian]
     * x-UTF16_PlatformEndian value: java.nio.charset.CharsetICU[x-UTF16_PlatformEndian]
     * x-UTF32_OppositeEndian value: java.nio.charset.CharsetICU[x-UTF32_OppositeEndian]
     * x-UTF32_PlatformEndian value: java.nio.charset.CharsetICU[x-UTF32_PlatformEndian]
     *
     */
    public static final int ANY_CHARSET = 0x00;
    public static final int US_ASCII    = 0x03;
    public static final int ISO_8859_1  = 0x04;
    public static final int ISO_8859_2  = 0x05;
    public static final int ISO_8859_3  = 0x06;
    public static final int ISO_8859_4  = 0x07;
    public static final int ISO_8859_5  = 0x08;
    public static final int ISO_8859_6  = 0x09;
    public static final int ISO_8859_7  = 0x0A;
    public static final int ISO_8859_8  = 0x0B;
    public static final int ISO_8859_9  = 0x0C;
    public static final int SHIFT_JIS   = 0x11;
    public static final int EUC_JP      = 0x12;
    public static final int EUC_KR      = 0x26;
    public static final int ISO_2022_JP = 0x27;
    public static final int ISO_2022_JP_2 = 0x28;
    public static final int UTF_8       = 0x6A;
    public static final int GBK         = 0x71;
    public static final int GB18030     = 0x72;
    public static final int GB2312      = 0x07E9;
    public static final int BIG5        = 0x07EA;
    public static final int UCS2        = 0x03E8;
    public static final int UTF_16      = 0x03F7;
    public static final int HZ_GB_2312  = 0x0825;

    /**
     * If the encoding of given data is unsupported, use UTF_8 to decode it.
     */
    public static final int DEFAULT_CHARSET = UTF_8;

    /**
     * Array of MIB enum numbers.
     */
    private static final int[] MIBENUM_NUMBERS = {
            ANY_CHARSET,
            US_ASCII,
            ISO_8859_1,
            ISO_8859_2,
            ISO_8859_3,
            ISO_8859_4,
            ISO_8859_5,
            ISO_8859_6,
            ISO_8859_7,
            ISO_8859_8,
            ISO_8859_9,
            SHIFT_JIS,
            EUC_JP,
            EUC_KR,
            ISO_2022_JP,
            ISO_2022_JP_2,
            UTF_8,
            GBK,
            GB18030,
            GB2312,
            BIG5,
            UCS2,
            UTF_16,
            HZ_GB_2312,
    };

    /**
     * The Well-known-charset Mime name.
     */
    public static final String MIMENAME_ANY_CHARSET = "*";
    public static final String MIMENAME_US_ASCII    = "us-ascii";
    public static final String MIMENAME_ISO_8859_1  = "iso-8859-1";
    public static final String MIMENAME_ISO_8859_2  = "iso-8859-2";
    public static final String MIMENAME_ISO_8859_3  = "iso-8859-3";
    public static final String MIMENAME_ISO_8859_4  = "iso-8859-4";
    public static final String MIMENAME_ISO_8859_5  = "iso-8859-5";
    public static final String MIMENAME_ISO_8859_6  = "iso-8859-6";
    public static final String MIMENAME_ISO_8859_7  = "iso-8859-7";
    public static final String MIMENAME_ISO_8859_8  = "iso-8859-8";
    public static final String MIMENAME_ISO_8859_9  = "iso-8859-9";
    public static final String MIMENAME_SHIFT_JIS   = "shift_JIS";
    public static final String MIMENAME_EUC_JP      = "euc-jp";
    public static final String MIMENAME_EUC_KR      = "euc-kr";
    public static final String MIMENAME_ISO_2022_JP = "iso-2022-jp";
    public static final String MIMENAME_ISO_2022_JP_2 = "iso-2022-jp-2";
    public static final String MIMENAME_UTF_8       = "utf-8";
    public static final String MIMENAME_GBK         = "gbk";
    public static final String MIMENAME_GB18030     = "gb18030";
    public static final String MIMENAME_GB2312      = "gb2312";
    public static final String MIMENAME_BIG5        = "big5";
    public static final String MIMENAME_UCS2        = "iso-10646-ucs-2";
    public static final String MIMENAME_UTF_16      = "utf-16";
    public static final String MIMENAME_HZ_GB_2312  = "hz-gb-2312";

    public static final String DEFAULT_CHARSET_NAME = MIMENAME_UTF_8;

    /**
     * Array of the names of character sets.
     */
    private static final String[] MIME_NAMES = {
            MIMENAME_ANY_CHARSET,
            MIMENAME_US_ASCII,
            MIMENAME_ISO_8859_1,
            MIMENAME_ISO_8859_2,
            MIMENAME_ISO_8859_3,
            MIMENAME_ISO_8859_4,
            MIMENAME_ISO_8859_5,
            MIMENAME_ISO_8859_6,
            MIMENAME_ISO_8859_7,
            MIMENAME_ISO_8859_8,
            MIMENAME_ISO_8859_9,
            MIMENAME_SHIFT_JIS,
            MIMENAME_EUC_JP,
            MIMENAME_EUC_KR,
            MIMENAME_ISO_2022_JP,
            MIMENAME_ISO_2022_JP_2,
            MIMENAME_UTF_8,
            MIMENAME_GBK,
            MIMENAME_GB18030,
            MIMENAME_GB2312,
            MIMENAME_BIG5,
            MIMENAME_UCS2,
            MIMENAME_UTF_16,
            MIMENAME_HZ_GB_2312,
    };

    private static final SparseArray<String> MIBENUM_TO_NAME_MAP;

    private static final SimpleArrayMap<String, Integer> NAME_TO_MIBENUM_MAP;

    static {
        // Create the HashMaps.
        MIBENUM_TO_NAME_MAP = new SparseArray<String>();
        NAME_TO_MIBENUM_MAP = new SimpleArrayMap<String, Integer>();
        assert (MIBENUM_NUMBERS.length == MIME_NAMES.length);
        final int count = MIBENUM_NUMBERS.length - 1;
        for (int i = 0; i <= count; i++) {
            MIBENUM_TO_NAME_MAP.put(MIBENUM_NUMBERS[i], MIME_NAMES[i]);
            NAME_TO_MIBENUM_MAP.put(MIME_NAMES[i], MIBENUM_NUMBERS[i]);
        }
    }

    private CharacterSets() {
    } // Non-instantiatable

    /**
     * Map an MIBEnum number to the name of the charset which this number
     * is assigned to by IANA.
     *
     * @param mibEnumValue An IANA assigned MIBEnum number.
     * @return The name string of the charset.
     */
    public static String getMimeName(final int mibEnumValue)
            throws UnsupportedEncodingException {
        final String name = MIBENUM_TO_NAME_MAP.get(mibEnumValue);
        if (name == null) {
            throw new UnsupportedEncodingException();
        }
        return name;
    }

    /**
     * Map a well-known charset name to its assigned MIBEnum number.
     *
     * @param mimeName The charset name.
     * @return The MIBEnum number assigned by IANA for this charset.
     */
    public static int getMibEnumValue(final String mimeName)
            throws UnsupportedEncodingException {
        if (null == mimeName) {
            return -1;
        }

        final Integer mibEnumValue = NAME_TO_MIBENUM_MAP.get(mimeName);
        if (mibEnumValue == null) {
            throw new UnsupportedEncodingException();
        }
        return mibEnumValue;
    }
}
