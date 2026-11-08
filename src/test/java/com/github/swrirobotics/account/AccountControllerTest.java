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

package com.github.swrirobotics.account;

import com.github.swrirobotics.config.WebAppConfigurationAware;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.test.context.support.WithMockUser;

import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class AccountControllerTest extends WebAppConfigurationAware {
    @MockBean
    private AccountRepository myAccountRepository;

    @Test
    @WithMockUser(username = "user@example.com")
    public void currentAccount() throws Exception {
        Account account = new Account("user@example.com", "secret", "ROLE_USER");
        ReflectionTestUtils.setField(account, "id", 1L);
        when(myAccountRepository.findByEmail("user@example.com")).thenReturn(account);

        mockMvc.perform(get("/account/current")
            .principal(new UsernamePasswordAuthenticationToken("user@example.com", null)))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.email").value("user@example.com"))
            .andExpect(jsonPath("$.role").value("ROLE_USER"))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andDo(document("account/{method-name}",
                preprocessResponse(prettyPrint()),
                responseFields(
                    fieldWithPath("id").description("Database ID of the account"),
                    fieldWithPath("email").description("User name of the account"),
                    fieldWithPath("role").description("Role granted to the account; ROLE_USER or ROLE_ADMIN")
                )
            ));
    }

    @Test
    @WithMockUser(username = "missing@example.com")
    public void currentAccountNotFound() throws Exception {
        when(myAccountRepository.findByEmail("missing@example.com")).thenReturn(null);

        mockMvc.perform(get("/account/current")
            .principal(new UsernamePasswordAuthenticationToken("missing@example.com", null)))
            .andExpect(status().isOk())
            .andExpect(content().string(""));
    }
}
