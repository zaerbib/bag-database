// *****************************************************************************
//
// Copyright (c) 2020, Southwest Research Institute® (SwRI®)
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright
//       notice, this list of conditions and the following disclaimer in the
//       documentation and/or other materials provided with the distribution.
//     * Neither the name of Southwest Research Institute® (SwRI®) nor the
//       names of its contributors may be used to endorse or promote products
//       derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL Southwest Research Institute® BE LIABLE
// FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
// DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
// LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
// OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
// DAMAGE.
//
// *****************************************************************************

package com.github.swrirobotics.support.web;

import com.github.swrirobotics.bags.storage.BagStorageConfiguration;

import java.util.List;
import java.util.Map;

/**
 * The configuration as sent to the web interface. It mirrors {@link Configuration}
 * without its secrets (JDBC, admin and LDAP bind passwords, Google and Bing keys);
 * S3 credentials inside storageConfigurations are hidden by their own class.
 */
public class ConfigurationDto {
    private final Configuration myConfig;

    private ConfigurationDto(Configuration config) {
        myConfig = config;
    }

    public static ConfigurationDto from(Configuration config) {
        return new ConfigurationDto(config);
    }

    @SuppressWarnings("deprecation")
    public String getBagPath() {
        return myConfig.getBagPath();
    }

    public String getDockerHost() {
        return myConfig.getDockerHost();
    }

    public String getDriver() {
        return myConfig.getDriver();
    }

    public String getJdbcUsername() {
        return myConfig.getJdbcUsername();
    }

    public String getJdbcUrl() {
        return myConfig.getJdbcUrl();
    }

    public String getScriptTmpPath() {
        return myConfig.getScriptTmpPath();
    }

    public Boolean getUseMapQuest() {
        return myConfig.getUseMapQuest();
    }

    public Boolean getUseBing() {
        return myConfig.getUseBing();
    }

    public String[] getVehicleNameTopics() {
        return myConfig.getVehicleNameTopics();
    }

    public String[] getMetadataTopics() {
        return myConfig.getMetadataTopics();
    }

    public String[] getGpsTopics() {
        return myConfig.getGpsTopics();
    }

    public Map<String, String[]> getOpenWithUrls() {
        return myConfig.getOpenWithUrls();
    }

    public Boolean getDebugJavascript() {
        return myConfig.getDebugJavascript();
    }

    public Boolean getRemoveOnDeletion() {
        return myConfig.getRemoveOnDeletion();
    }

    public Boolean getFasterCodec() {
        return myConfig.getFasterCodec();
    }

    public String getTileMapUrl() {
        return myConfig.getTileMapUrl();
    }

    public Integer getTileWidthPx() {
        return myConfig.getTileWidthPx();
    }

    public Integer getTileHeightPx() {
        return myConfig.getTileHeightPx();
    }

    public String getLdapBindDn() {
        return myConfig.getLdapBindDn();
    }

    public String getLdapSearchBase() {
        return myConfig.getLdapSearchBase();
    }

    public String getLdapServer() {
        return myConfig.getLdapServer();
    }

    public String getLdapUserPattern() {
        return myConfig.getLdapUserPattern();
    }

    public List<BagStorageConfiguration> getStorageConfigurations() {
        return myConfig.getStorageConfigurations();
    }
}
