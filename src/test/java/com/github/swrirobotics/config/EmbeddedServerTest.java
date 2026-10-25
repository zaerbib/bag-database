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

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts BagDatabaseApplication on the embedded Tomcat and checks what the old servlet initializers
 * and web.xml used to provide: views, static resources, SockJS, the error page, Spring Session,
 * the security filter chain and the order of the servlet filters.
 */
@RunWith(SpringRunner.class)
@ActiveProfiles("test")
@SpringBootTest(classes = {BagDatabaseApplication.class, EmbeddedDataSourceConfig.class},
                webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                properties = "server.tomcat.basedir=target/tomcat-server-test")
public class EmbeddedServerTest {
    @Autowired
    private TestRestTemplate myRestTemplate;

    @Autowired
    private ServletWebServerApplicationContext myContext;

    @Autowired
    private DataSource myDataSource;

    @Test
    public void servesTheIndexPage() {
        ResponseEntity<String> response = myRestTemplate.getForEntity("/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("text/html");
    }

    @Test
    public void servesStaticResources() {
        ResponseEntity<String> response = myRestTemplate.getForEntity("/resources/css/core.css", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    public void servesTheCompressedJavaScriptFromTheClasspath() {
        // Written to target/classes/static/resources/js by yuicompressor during process-resources
        ResponseEntity<String> response = myRestTemplate.getForEntity("/resources/js/compressed.js", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Ext.define");
    }

    @Test
    public void templatesResolveI18nMessages() {
        ResponseEntity<String> response = myRestTemplate.getForEntity("/", String.class);

        // An unresolved message would render as ??view.index.title_en??
        assertThat(response.getBody()).contains("<title>Bag Database</title>").doesNotContain("??");
    }

    @Test
    public void noDefaultServletIsRegistered() {
        // Static files come from the classpath through the /resources/** handler, not from a document root
        assertThat(tomcatContext().findChild("default")).isNull();
    }

    @Test
    public void sockJsEndpointAnswers() {
        ResponseEntity<String> response = myRestTemplate.getForEntity("/register/info", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"websocket\"");
    }

    @Test
    public void unknownUrlRendersTheGeneralErrorPage() {
        // Under /bags/**, which anonymous users may access; elsewhere they are sent to the login page first
        ResponseEntity<String> response = myRestTemplate.getForEntity("/bags/no/such/page", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("<title>Error page</title>").contains("/bags/no/such/page");
    }

    @Test
    public void sessionIsCreatedBySpringSessionJdbc() {
        Integer before = sessionCount();

        ResponseEntity<String> response = myRestTemplate.getForEntity("/", String.class);

        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).anyMatch(cookie -> cookie.startsWith("SESSION="));
        assertThat(cookies).noneMatch(cookie -> cookie.startsWith("JSESSIONID="));
        assertThat(sessionCount()).isGreaterThan(before);
    }

    @Test
    public void anonymousUploadIsRefused() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Requested-With", "XMLHttpRequest");

        ResponseEntity<String> response = myRestTemplate.exchange(
                "/bags/upload", HttpMethod.POST, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    public void filtersAreMappedOnceInOrder() {
        List<String> filterNames = Arrays.stream(tomcatContext().findFilterMaps())
                .map(FilterMap::getFilterName)
                .collect(Collectors.toList());

        // Tomcat's own WsFilter for the WebSocket upgrade comes last
        assertThat(filterNames).containsExactly(
                "characterEncodingFilter",
                "springSessionRepositoryFilter",
                "multipartFilter",
                "springSecurityFilterChain",
                "Tomcat WebSocket (JSR356) Filter");
    }

    @Test
    public void dispatcherServletAcceptsLargeUploads() {
        Wrapper dispatcher = (Wrapper) tomcatContext().findChild(WebServerConfig.DISPATCHER_SERVLET_NAME);

        assertThat(dispatcher.getMultipartConfigElement().getMaxFileSize())
                .isEqualTo(WebServerConfig.MAX_UPLOAD_SIZE_BYTES);
        assertThat(dispatcher.getMultipartConfigElement().getMaxRequestSize())
                .isEqualTo(WebServerConfig.MAX_UPLOAD_SIZE_BYTES);
    }

    private Context tomcatContext() {
        return (Context) ((TomcatWebServer) myContext.getWebServer()).getTomcat().getHost().findChildren()[0];
    }

    private Integer sessionCount() {
        return new JdbcTemplate(myDataSource).queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class);
    }
}
