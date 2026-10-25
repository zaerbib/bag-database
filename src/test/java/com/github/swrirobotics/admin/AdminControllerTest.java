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

package com.github.swrirobotics.admin;

import com.github.swrirobotics.account.Account;
import com.github.swrirobotics.account.AccountRepository;
import com.github.swrirobotics.bags.BagService;
import com.github.swrirobotics.bags.storage.BagScanner;
import com.github.swrirobotics.config.WebAppConfigurationAware;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockUser(roles = "ADMIN")
public class AdminControllerTest extends WebAppConfigurationAware {
    @MockBean
    private BagScanner myBagScanner;
    @MockBean
    private BagService myBagService;
    @MockBean
    private AccountRepository myAccountRepository;

    private ResultActions postAdmin(String action) throws Exception {
        return mockMvc.perform(post("/admin/" + action).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(content().string(""))
            .andDo(document("admin/{method-name}"));
    }

    @Test
    public void updateLatLons() throws Exception {
        postAdmin("updateLatLons");
        verify(myBagScanner).updateAllLatLons();
    }

    @Test
    public void updateLocations() throws Exception {
        postAdmin("updateLocations");
        verify(myBagScanner).updateAllLocations();
    }

    @Test
    public void updateGpsPaths() throws Exception {
        postAdmin("updateGpsPaths");
        verify(myBagScanner).updateAllGpsPaths();
    }

    @Test
    public void updateVehicleNames() throws Exception {
        postAdmin("updateVehicleNames");
        verify(myBagScanner).updateAllVehicleNames();
    }

    @Test
    public void updateTags() throws Exception {
        postAdmin("updateTags");
        verify(myBagScanner).updateAllTags();
    }

    @Test
    public void removeMissingBags() throws Exception {
        postAdmin("removeMissingBags");
        verify(myBagService).removeMissingBags();
    }

    @Test
    public void removeDuplicates() throws Exception {
        postAdmin("removeDuplicates");
        verify(myBagService).removeDuplicateBags();
    }

    @Test
    public void forceScan() throws Exception {
        postAdmin("forceScan");
        verify(myBagScanner).scanAllStorages(false);
    }

    @Test
    public void forceFullScan() throws Exception {
        postAdmin("forceFullScan");
        verify(myBagScanner).scanAllStorages(true);
    }

    @Test
    public void changePassword() throws Exception {
        Account admin = new Account("admin", "old", "ROLE_ADMIN");
        when(myAccountRepository.findByEmail("admin")).thenReturn(admin);

        mockMvc.perform(post("/admin/changePassword")
            .param("password", "newPassword")
            .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(content().string(""))
            .andDo(document("admin/{method-name}"));

        verify(myAccountRepository).save(same(admin));
        assertEquals("newPassword", admin.getPassword());
    }
}
