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

package com.github.swrirobotics.config;

import com.github.swrirobotics.support.web.Configuration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;

import static org.junit.Assert.assertEquals;

/**
 * Exercises ConfigService against a real settings file. It is constructed
 * directly with no active profile, so getConfiguration() reads the file
 * instead of returning test defaults.
 */
public class ConfigServiceTest {
    @Rule
    public TemporaryFolder myTempFolder = new TemporaryFolder();

    private ConfigService myConfigService;

    @Before
    public void setUp() throws Exception {
        File settingsFile = new File(myTempFolder.getRoot(), "settings.yml");
        myConfigService = new ConfigService(null, new MockEnvironment());
        ReflectionTestUtils.setField(myConfigService, "filename",
            settingsFile.toURI().toURL().toString());

        Configuration initial = new Configuration();
        initial.setJdbcPassword("oldJdbc");
        initial.setGoogleApiKey("oldGoogle");
        initial.setBingKey("oldBing");
        initial.setAdminPassword("oldAdmin");
        initial.setLdapBindPassword("oldLdap");
        myConfigService.setConfiguration(initial);
    }

    @Test
    public void blankJdbcPasswordKeepsExistingValue() throws Exception {
        Configuration update = myConfigService.getConfiguration();
        update.setJdbcPassword("");
        myConfigService.setConfiguration(update);

        assertEquals("oldJdbc", myConfigService.getConfiguration().getJdbcPassword());
    }

    @Test
    public void otherBlankSecretsAreWrittenAsGiven() throws Exception {
        // AdminController's startup hook clears adminPassword this way once it
        // has been applied to the admin account; it must not come back.
        Configuration update = myConfigService.getConfiguration();
        update.setAdminPassword("");
        update.setGoogleApiKey("");
        update.setBingKey("");
        update.setLdapBindPassword("");
        myConfigService.setConfiguration(update);

        Configuration saved = myConfigService.getConfiguration();
        assertEquals("", saved.getAdminPassword());
        assertEquals("", saved.getGoogleApiKey());
        assertEquals("", saved.getBingKey());
        assertEquals("", saved.getLdapBindPassword());
    }
}
