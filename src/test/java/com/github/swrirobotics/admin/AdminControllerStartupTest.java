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

import com.github.swrirobotics.account.AccountRepository;
import com.github.swrirobotics.config.ConfigService;
import com.github.swrirobotics.support.web.Configuration;
import org.junit.After;
import org.junit.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ConfigService.setConfiguration is @Secured("ROLE_ADMIN"); the startup hook that applies
 * adminPassword from settings.yml runs with nobody logged in and must still be allowed to call it.
 */
public class AdminControllerStartupTest {
    @After
    public void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void checkAdminAccountSavesConfigurationAsAdminAndRestoresContext() throws Exception {
        ConfigService configService = mock(ConfigService.class);
        Configuration config = new Configuration();
        config.setAdminPassword("fromSettings");
        when(configService.getConfiguration()).thenReturn(config);

        AtomicReference<Authentication> authDuringSave = new AtomicReference<>();
        doAnswer(invocation -> {
            authDuringSave.set(SecurityContextHolder.getContext().getAuthentication());
            return null;
        }).when(configService).setConfiguration(any());

        AdminController controller = new AdminController();
        ReflectionTestUtils.setField(controller, "myConfigService", configService);
        ReflectionTestUtils.setField(controller, "myAccountRepository", mock(AccountRepository.class));
        ReflectionTestUtils.setField(controller, "myTxManager", mock(PlatformTransactionManager.class));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        controller.checkAdminAccount();

        verify(configService).setConfiguration(config);
        assertEquals("", config.getAdminPassword());
        assertNotNull(authDuringSave.get());
        assertTrue(authDuringSave.get().getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch("ROLE_ADMIN"::equals));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
