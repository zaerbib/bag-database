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

package com.github.swrirobotics.security;

import com.github.swrirobotics.account.AccountRepository;
import com.github.swrirobotics.bags.BagService;
import com.github.swrirobotics.config.WebSecurityConfigurationAware;
import com.github.swrirobotics.scripts.ScriptService;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.FilterInvocation;
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor;

import java.util.Collection;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Access matrix (anonymous / authenticated user / admin) for the endpoints that
 * modify bags or scripts.  The test profile disables CSRF, so CSRF protection is
 * not covered here.
 */
public class AccessControlTest extends WebSecurityConfigurationAware {
    @MockBean
    private BagService myBagService;
    @MockBean
    private ScriptService myScriptService;
    @MockBean
    private AccountRepository myAccountRepository;

    private static final ResultMatcher REDIRECT_TO_SIGNIN = redirectedUrlPattern("**/signin");

    // @WithMockUser does not survive the security filter chain's SecurityContextPersistenceFilter,
    // so the caller is attached to each request instead.
    private static final RequestPostProcessor ANONYMOUS = anonymous();
    private static final RequestPostProcessor USER = user("user").roles("USER");
    private static final RequestPostProcessor ADMIN = user("admin").roles("ADMIN");

    private MockHttpServletRequestBuilder bagsUpdate() {
        return post("/bags/update").with(csrf()).content("{\"totalCount\": 1, \"bags\": [{\"id\": 1}]}");
    }

    private MockHttpServletRequestBuilder bagsUpload() {
        return multipart("/bags/upload")
            .file(new MockMultipartFile("file", "bagfile.bag", "application/octet-stream", new byte[1]))
            .param("targetDirectory", "/")
            .with(csrf());
    }

    private MockHttpServletRequestBuilder bagsSetTag() {
        return post("/bags/setTag").param("tagName", "t").param("value", "v").param("bagId", "1").with(csrf());
    }

    private MockHttpServletRequestBuilder bagsSetTagForBags() {
        return post("/bags/setTagForBags").param("tagName", "t").param("value", "v").param("bagIds", "1", "2").with(csrf());
    }

    private MockHttpServletRequestBuilder bagsRemoveTags() {
        return post("/bags/removeTags").param("tagNames", "t").param("bagId", "1").with(csrf());
    }

    private MockHttpServletRequestBuilder scriptsSave() {
        return post("/scripts/save")
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .content("{\"id\": 0, \"name\": \"s\", \"dockerImage\": \"ros\", \"script\": \"\"}")
            .with(csrf());
    }

    private MockHttpServletRequestBuilder scriptsRun() {
        return post("/scripts/run").param("scriptId", "1").param("bagIds", "1").with(csrf());
    }

    private MockHttpServletRequestBuilder scriptsDelete() {
        return post("/scripts/delete").param("scriptId", "1").with(csrf());
    }

    private void expectAll(RequestPostProcessor caller, ResultMatcher matcher,
                           MockHttpServletRequestBuilder... requests) throws Exception {
        for (MockHttpServletRequestBuilder request : requests) {
            mockMvc.perform(request.with(caller)).andExpect(matcher);
        }
    }

    private MockHttpServletRequestBuilder[] bagWrites() {
        return new MockHttpServletRequestBuilder[]{
            bagsUpdate(), bagsUpload(), bagsSetTag(), bagsSetTagForBags(), bagsRemoveTags()};
    }

    private MockHttpServletRequestBuilder[] scriptWrites() {
        return new MockHttpServletRequestBuilder[]{scriptsSave(), scriptsRun(), scriptsDelete()};
    }

    // The global ExceptionHandler renders failures as a 200 error page, so a 200 alone does not
    // prove that the request reached the controller; check the service calls too.
    private void verifyBagWritesReachedService() throws Exception {
        verify(myBagService).updateBag(any());
        verify(myBagService).uploadBag(any(), eq("/"), eq("default"));
        verify(myBagService, times(3)).setTagForBag(eq("t"), eq("v"), anyLong());
        verify(myBagService).removeTagForBag(anyCollection(), eq(1L));
    }

    private void verifyScriptWritesReachedService() throws Exception {
        verify(myScriptService).addScript(any());
        verify(myScriptService).runScript(eq(1L), anyList());
        verify(myScriptService).removeScript(1L);
    }

    @Test
    public void anonymousCannotModifyBags() throws Exception {
        expectAll(ANONYMOUS, REDIRECT_TO_SIGNIN, bagWrites());
    }

    @Test
    public void anonymousCannotModifyScripts() throws Exception {
        expectAll(ANONYMOUS, REDIRECT_TO_SIGNIN, scriptWrites());
    }

    @Test
    public void anonymousAjaxRequestsGetUnauthorized() throws Exception {
        expectAll(ANONYMOUS, status().isUnauthorized(),
            bagsSetTag().header("X-Requested-With", "XMLHttpRequest"),
            scriptsDelete().header("X-Requested-With", "XMLHttpRequest"));
    }

    @Test
    public void anonymousCanStillReadBagsAndScripts() throws Exception {
        expectAll(ANONYMOUS, status().isOk(),
            get("/bags/search").param("text", ""),
            get("/scripts/list"));
    }

    @Test
    public void anonymousCannotReadCurrentAccount() throws Exception {
        expectAll(ANONYMOUS, REDIRECT_TO_SIGNIN, get("/account/current"));
    }

    @Test
    public void userCanModifyBags() throws Exception {
        expectAll(USER, status().isOk(), bagWrites());
        verifyBagWritesReachedService();
    }

    @Test
    public void userCannotModifyScripts() throws Exception {
        expectAll(USER, status().isForbidden(), scriptWrites());
    }

    @Test
    public void userCannotUseAdminEndpoints() throws Exception {
        expectAll(USER, status().isForbidden(),
            post("/config/get").with(csrf()),
            post("/admin/forceScan").with(csrf()));
    }

    @Test
    public void userCanReadCurrentAccount() throws Exception {
        expectAll(USER, status().isOk(), get("/account/current"));
    }

    @Test
    public void adminCanModifyBags() throws Exception {
        expectAll(ADMIN, status().isOk(), bagWrites());
        verifyBagWritesReachedService();
    }

    @Test
    public void adminCanModifyScripts() throws Exception {
        when(myScriptService.addScript(any())).thenReturn(1L);
        when(myScriptService.runScript(anyLong(), anyList())).thenReturn(UUID.randomUUID());
        expectAll(ADMIN, status().isOk(), scriptWrites());
        verifyScriptWritesReachedService();
    }

    @Test
    public void adminCanReadCurrentAccount() throws Exception {
        expectAll(ADMIN, status().isOk(), get("/account/current"));
    }

    // The @Secured checks only run once the request reaches the controller, i.e. after a
    // multipart body has been fully received. Writes must also be refused by the URL rules
    // of the filter chain, which MockMvc results alone cannot distinguish.
    @Test
    public void writesAreProtectedByUrlRules() {
        // Spring MVC also routes the trailing-slash form to the same handler.
        for (String path : new String[]{"/bags/update", "/bags/upload", "/bags/setTag",
                                         "/bags/setTagForBags", "/bags/removeTags", "/bags/upload/"}) {
            assertEquals(path, "[authenticated]", urlRuleFor(path));
        }
        for (String path : new String[]{"/scripts/save", "/scripts/run", "/scripts/delete", "/scripts/run/"}) {
            assertEquals(path, "[hasRole('ROLE_ADMIN')]", urlRuleFor(path));
        }
        // Reads stay open to anonymous users in non-LDAP mode.
        assertEquals("[permitAll]", urlRuleFor("/bags/search"));
        assertEquals("[permitAll]", urlRuleFor("/scripts/list"));
    }

    private String urlRuleFor(String path) {
        FilterSecurityInterceptor interceptor = wac.getBean(FilterChainProxy.class)
            .getFilters(path).stream()
            .filter(FilterSecurityInterceptor.class::isInstance)
            .map(FilterSecurityInterceptor.class::cast)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No FilterSecurityInterceptor for " + path));
        Collection<ConfigAttribute> attributes = interceptor.getSecurityMetadataSource()
            .getAttributes(new FilterInvocation(path, "POST"));
        return String.valueOf(attributes);
    }
}
