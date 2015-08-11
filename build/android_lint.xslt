<!--
    Copyright (C) 2015 The Android Open Source Project

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
-->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:output method="text" version="1.0" encoding="UTF-8" omit-xml-declaration="yes"/>
<xsl:template match="/issues">
  <xsl:for-each select="issue">
    <!-- Exclude errors/warnings with
       /android/support in the location, these are outside our control
       /com/google/common in the location, these are outside our control
       res/values- in the location, these are localized resources, and we only need to be notified of the neutral resource issues
       .class in the location, these don't have source we can do anything about -->
    <xsl:if test="not(./location) or (not(contains(./location/@file, '/android/support/')) and not(contains(./location/@file, '/com/google/common/')) and not(starts-with(./location/@file, 'res/values-')) and not(contains(./location/@file, '.class')))">
      <xsl:value-of select="@severity" />: <xsl:value-of select="@message" disable-output-escaping="yes"/><xsl:text>  </xsl:text>[<xsl:value-of select="@id" />]<xsl:text>&#xa;</xsl:text>
      <xsl:for-each select="./location">
        <xsl:text>  </xsl:text><xsl:value-of select="@file" />
        <xsl:if test="@line">
          <xsl:text>:</xsl:text><xsl:value-of select="@line" />
          <xsl:if test="@column">
            <xsl:text>,</xsl:text><xsl:value-of select="@column" />
          </xsl:if>
        </xsl:if>
        <xsl:text>&#xa;</xsl:text>
      </xsl:for-each>
    </xsl:if>
  </xsl:for-each>
</xsl:template>
</xsl:stylesheet>
