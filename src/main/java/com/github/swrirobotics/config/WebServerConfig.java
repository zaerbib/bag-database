// *****************************************************************************
//
// Copyright (c) 2015, Southwest Research Institute® (SwRI®)
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

import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletRegistrationBean;
import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.ErrorPageRegistrar;
import org.springframework.boot.web.servlet.DelegatingFilterProxyRegistrationBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.multipart.support.MultipartFilter;
import org.springframework.web.servlet.DispatcherServlet;

import javax.servlet.DispatcherType;
import javax.servlet.MultipartConfigElement;
import java.util.EnumSet;

/**
 * Registers the DispatcherServlet, the servlet filters and the error page.  Spring Boot applies these
 * registration beans both on the embedded server and when the WAR is deployed to an external Tomcat.
 *
 * Filter order: character encoding, then Spring Session, then multipart parsing, then Spring Security.
 * The session and security filters are registered by bean name, through a DelegatingFilterProxy,
 * so that Boot does not map those Filter beans a second time.
 */
@Configuration
class WebServerConfig {
    static final long MAX_UPLOAD_SIZE_BYTES = 1024L * 1024L * 1024L * 50L; // 50 GB
    static final String DISPATCHER_SERVLET_NAME = "dispatcher";

    static final int CHARACTER_ENCODING_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE;
    static final int SESSION_FILTER_ORDER = SessionRepositoryFilter.DEFAULT_ORDER;
    static final int MULTIPART_FILTER_ORDER = SESSION_FILTER_ORDER + 10;
    static final int SECURITY_FILTER_ORDER = SESSION_FILTER_ORDER + 20;

    // The dispatcher types the old Spring Session and Spring Security initializers used
    private static final EnumSet<DispatcherType> SESSION_AND_SECURITY_DISPATCHER_TYPES =
            EnumSet.of(DispatcherType.REQUEST, DispatcherType.ERROR, DispatcherType.ASYNC);

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofBytes(MAX_UPLOAD_SIZE_BYTES));
        factory.setMaxRequestSize(DataSize.ofBytes(MAX_UPLOAD_SIZE_BYTES));
        return factory.createMultipartConfig();
    }

    @Bean
    public DispatcherServlet dispatcherServlet() {
        return new DispatcherServlet();
    }

    @Bean
    public DispatcherServletRegistrationBean dispatcherServletRegistration(DispatcherServlet dispatcherServlet,
                                                                           MultipartConfigElement multipartConfig) {
        DispatcherServletRegistrationBean registration = new DispatcherServletRegistrationBean(dispatcherServlet, "/");
        registration.setName(DISPATCHER_SERVLET_NAME);
        registration.setLoadOnStartup(1);
        registration.addInitParameter("defaultHtmlEscape", "true");
        registration.setMultipartConfig(multipartConfig);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<CharacterEncodingFilter> characterEncodingFilterRegistration() {
        FilterRegistrationBean<CharacterEncodingFilter> registration =
                new FilterRegistrationBean<>(new CharacterEncodingFilter("UTF-8", true));
        registration.setName("characterEncodingFilter");
        registration.setOrder(CHARACTER_ENCODING_FILTER_ORDER);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.FORWARD,
                                        DispatcherType.INCLUDE, DispatcherType.ASYNC);
        return registration;
    }

    @Bean
    public DelegatingFilterProxyRegistrationBean springSessionRepositoryFilterRegistration() {
        DelegatingFilterProxyRegistrationBean registration =
                new DelegatingFilterProxyRegistrationBean("springSessionRepositoryFilter");
        registration.setOrder(SESSION_FILTER_ORDER);
        registration.setDispatcherTypes(SESSION_AND_SECURITY_DISPATCHER_TYPES);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<MultipartFilter> multipartFilterRegistration() {
        FilterRegistrationBean<MultipartFilter> registration = new FilterRegistrationBean<>(new MultipartFilter());
        registration.setName("multipartFilter");
        registration.setOrder(MULTIPART_FILTER_ORDER);
        registration.setDispatcherTypes(SESSION_AND_SECURITY_DISPATCHER_TYPES);
        return registration;
    }

    @Bean
    public DelegatingFilterProxyRegistrationBean springSecurityFilterChainRegistration() {
        DelegatingFilterProxyRegistrationBean registration =
                new DelegatingFilterProxyRegistrationBean("springSecurityFilterChain");
        registration.setOrder(SECURITY_FILTER_ORDER);
        registration.setDispatcherTypes(SESSION_AND_SECURITY_DISPATCHER_TYPES);
        return registration;
    }

    /**
     * Replaces the error-page element of web.xml; see CustomErrorController.generalError().
     */
    @Bean
    public ErrorPageRegistrar errorPageRegistrar() {
        return registry -> registry.addErrorPages(new ErrorPage("/generalError"));
    }
}
