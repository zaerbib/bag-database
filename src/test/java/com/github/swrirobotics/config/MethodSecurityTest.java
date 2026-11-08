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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import javax.inject.Inject;
import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @Secured is enforced on both controllers and services of the single application context.
 */
public class MethodSecurityTest extends WebSecurityConfigurationAware {
    @Inject
    private ConfigService myConfigService;

    @Rule
    public TemporaryFolder myTempFolder = new TemporaryFolder();

    @Test
    public void controllersAreSecured() throws Exception {
        mockMvc.perform(post("/scripts/delete").param("scriptId", "1").with(anonymous()))
            .andExpect(redirectedUrlPattern("**/signin"));
        mockMvc.perform(post("/bags/setTag")
                .param("tagName", "t").param("bagId", "1").with(anonymous()))
            .andExpect(redirectedUrlPattern("**/signin"));
        mockMvc.perform(post("/scripts/delete").param("scriptId", "1").with(user("user").roles("USER")))
            .andExpect(status().isForbidden());
    }

    @Test
    public void controllersAcceptAdmin() throws Exception {
        mockMvc.perform(post("/config/get").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    public void servicesAreSecured() throws Exception {
        // Redirect the settings file so that a regression cannot overwrite the real
        // ~/.ros-bag-database/settings.yml before the assertion fails.
        Object target = AopTestUtils.getUltimateTargetObject(myConfigService);
        Object originalFilename = ReflectionTestUtils.getField(target, "filename");
        File settings = myTempFolder.newFile("settings.yml");
        ReflectionTestUtils.setField(target, "filename", settings.toURI().toURL().toString());
        try {
            myConfigService.setConfiguration(new Configuration());
            fail("setConfiguration should require ROLE_ADMIN");
        }
        catch (AuthenticationCredentialsNotFoundException e) {
            assertEquals(0, settings.length());
        }
        finally {
            ReflectionTestUtils.setField(target, "filename", originalFilename);
        }
    }
}
