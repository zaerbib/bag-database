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

import com.github.swrirobotics.bags.storage.filesystem.FilesystemBagStorageConfigImpl;
import com.github.swrirobotics.support.web.Configuration;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.io.IOException;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithMockUser(roles = "ADMIN")
public class ConfigControllerTest extends WebAppConfigurationAware {
    // A spy keeps the real test-profile getConfiguration() (defaults, no file read)
    // while setConfiguration() is stubbed so no settings.yml is ever written.
    @SpyBean
    private ConfigService myConfigService;

    @Test
    public void getConfig() throws Exception {
        doReturn(configWithSecrets()).when(myConfigService).getConfiguration();

        mockMvc.perform(post("/config/get").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.bagPath").value("/bags"))
            .andExpect(jsonPath("$.data.jdbcPassword").doesNotExist())
            .andExpect(jsonPath("$.data.googleApiKey").doesNotExist())
            .andExpect(jsonPath("$.data.bingKey").doesNotExist())
            .andExpect(jsonPath("$.data.adminPassword").doesNotExist())
            .andExpect(jsonPath("$.data.ldapBindPassword").doesNotExist())
            .andDo(document("config/{method-name}",
                preprocessResponse(prettyPrint()),
                responseFields(
                    fieldWithPath("success").description("Whether the configuration was read"),
                    fieldWithPath("data").description("The current configuration"),
                    fieldWithPath("data.bagPath").description("Default path to bag files"),
                    fieldWithPath("data.dockerHost").description("Docker host used to run scripts"),
                    fieldWithPath("data.driver").description("JDBC driver class"),
                    fieldWithPath("data.jdbcUsername").description("JDBC user name"),
                    fieldWithPath("data.jdbcUrl").description("JDBC URL"),
                    fieldWithPath("data.scriptTmpPath").description("Temporary directory for script runs"),
                    fieldWithPath("data.useMapQuest").description("Whether to use MapQuest tiles"),
                    fieldWithPath("data.useBing").description("Whether to use Bing tiles"),
                    fieldWithPath("data.vehicleNameTopics").description("Topics searched for vehicle names"),
                    fieldWithPath("data.metadataTopics").description("Topics searched for metadata"),
                    fieldWithPath("data.gpsTopics").description("Topics searched for GPS positions"),
                    subsectionWithPath("data.openWithUrls").description("Map of \"Open With\" names to URLs"),
                    fieldWithPath("data.debugJavascript").description("Whether to load unminified JavaScript"),
                    fieldWithPath("data.removeOnDeletion").description("Whether to remove bags from the database when deleted from disk"),
                    fieldWithPath("data.fasterCodec").description("Whether to use a faster video codec"),
                    fieldWithPath("data.tileMapUrl").description("Map tile URL template"),
                    fieldWithPath("data.tileWidthPx").description("Map tile width in pixels"),
                    fieldWithPath("data.tileHeightPx").description("Map tile height in pixels"),
                    fieldWithPath("data.ldapBindDn").description("LDAP bind DN"),
                    fieldWithPath("data.ldapSearchBase").description("LDAP search base"),
                    fieldWithPath("data.ldapServer").description("LDAP server URL"),
                    fieldWithPath("data.ldapUserPattern").description("LDAP user DN pattern"),
                    subsectionWithPath("data.storageConfigurations").description("Configured storage backends")
                )
            ));
    }

    @Test
    public void getConfigWithFilesystemStorage() throws Exception {
        FilesystemBagStorageConfigImpl fs = new FilesystemBagStorageConfigImpl();
        fs.storageId = "local2";
        fs.dockerPath = "/bags2";
        fs.basePath = "/data/bags2";
        fs.isLocal = true;
        Configuration config = new Configuration();
        config.getStorageConfigurations().add(fs);
        doReturn(config).when(myConfigService).getConfiguration();

        mockMvc.perform(post("/config/get").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.storageConfigurations.length()").value(1))
            .andExpect(jsonPath("$.data.storageConfigurations[0].keys()",
                                containsInAnyOrder("storageId", "isLocal", "dockerPath", "basePath")))
            .andExpect(jsonPath("$.data.storageConfigurations[0].storageId").value("local2"))
            .andExpect(jsonPath("$.data.storageConfigurations[0].isLocal").value(true))
            .andExpect(jsonPath("$.data.storageConfigurations[0].dockerPath").value("/bags2"))
            .andExpect(jsonPath("$.data.storageConfigurations[0].basePath").value("/data/bags2"))
            .andDo(document("config/{method-name}",
                preprocessResponse(prettyPrint()),
                relaxedResponseFields(
                    fieldWithPath("data.storageConfigurations").description("Configured storage backends"),
                    fieldWithPath("data.storageConfigurations[].storageId").description("ID of the storage backend"),
                    fieldWithPath("data.storageConfigurations[].isLocal").description("Whether the backend is local"),
                    fieldWithPath("data.storageConfigurations[].dockerPath").description("Path to bags inside script containers"),
                    fieldWithPath("data.storageConfigurations[].basePath").description("Directory indexed by this backend")
                )
            ));
    }

    @Test
    public void setConfig() throws Exception {
        doNothing().when(myConfigService).setConfiguration(any());

        mockMvc.perform(post("/config/get")
            .param("bagPath", "/new/bags")
            .param("dockerHost", "http://docker:2375")
            .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andDo(document("config/{method-name}",
                preprocessResponse(prettyPrint()),
                responseFields(
                    fieldWithPath("success").description("Whether the configuration was saved")
                )
            ));

        ArgumentCaptor<Configuration> saved = ArgumentCaptor.forClass(Configuration.class);
        verify(myConfigService).setConfiguration(saved.capture());
        assertEquals("/new/bags", saved.getValue().getBagPath());
        assertEquals("http://docker:2375", saved.getValue().getDockerHost());
    }

    @Test
    public void setConfigKeepsBlankSecrets() throws Exception {
        doReturn(configWithSecrets()).when(myConfigService).getConfiguration();
        doNothing().when(myConfigService).setConfiguration(any());

        mockMvc.perform(post("/config/get")
            .param("bagPath", "/bags")
            .param("jdbcPassword", "")
            .param("googleApiKey", "")
            .param("bingKey", "")
            .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<Configuration> saved = ArgumentCaptor.forClass(Configuration.class);
        verify(myConfigService).setConfiguration(saved.capture());
        assertEquals("jdbcSecret", saved.getValue().getJdbcPassword());
        assertEquals("googleSecret", saved.getValue().getGoogleApiKey());
        assertEquals("bingSecret", saved.getValue().getBingKey());
        assertEquals("adminSecret", saved.getValue().getAdminPassword());
        assertEquals("ldapSecret", saved.getValue().getLdapBindPassword());
    }

    @Test
    public void setConfigReplacesNonBlankSecrets() throws Exception {
        doReturn(configWithSecrets()).when(myConfigService).getConfiguration();
        doNothing().when(myConfigService).setConfiguration(any());

        mockMvc.perform(post("/config/get")
            .param("bagPath", "/bags")
            .param("jdbcPassword", "newJdbc")
            .param("googleApiKey", "newGoogle")
            .param("bingKey", "newBing")
            .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<Configuration> saved = ArgumentCaptor.forClass(Configuration.class);
        verify(myConfigService).setConfiguration(saved.capture());
        assertEquals("newJdbc", saved.getValue().getJdbcPassword());
        assertEquals("newGoogle", saved.getValue().getGoogleApiKey());
        assertEquals("newBing", saved.getValue().getBingKey());
    }

    private static Configuration configWithSecrets() {
        Configuration config = new Configuration();
        config.setJdbcPassword("jdbcSecret");
        config.setGoogleApiKey("googleSecret");
        config.setBingKey("bingSecret");
        config.setAdminPassword("adminSecret");
        config.setLdapBindPassword("ldapSecret");
        return config;
    }

    @Test
    public void setConfigFailure() throws Exception {
        doThrow(new IOException("disk full")).when(myConfigService).setConfiguration(any());

        mockMvc.perform(post("/config/get")
            .param("bagPath", "/new/bags")
            .with(csrf()))
            .andExpect(status().isInternalServerError());
    }
}
