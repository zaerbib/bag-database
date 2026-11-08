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

import org.apache.catalina.connector.Connector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the Tomcat connector settings that src/main/docker/server.xml used to provide, now set as
 * server.* properties in application.yaml.
 */
@RunWith(SpringRunner.class)
@ActiveProfiles("test")
@SpringBootTest(classes = {BagDatabaseApplication.class, EmbeddedDataSourceConfig.class},
                webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                properties = {"server.tomcat.basedir=target/tomcat-settings-test",
                              "server.tomcat.accesslog.buffered=false"})
public class EmbeddedServerSettingsTest {
    // Larger than Tomcat's default 2 MB limit for form posts
    private static final int UPLOAD_SIZE_BYTES = 3 * 1024 * 1024;

    @Autowired
    private TestRestTemplate myRestTemplate;

    @Autowired
    private ServletWebServerApplicationContext myContext;

    // java.net.http does not decompress responses, so Content-Encoding stays visible
    private final HttpClient myHttpClient = HttpClient.newHttpClient();

    @Test
    public void connectorUsesTheServerXmlSettings() {
        Connector connector = ((TomcatWebServer) myContext.getWebServer()).getTomcat().getConnector();

        assertThat(connector.getProperty("maxThreads")).isEqualTo(150);
        assertThat(connector.getProperty("minSpareThreads")).isEqualTo(25);
        assertThat(connector.getProperty("connectionTimeout")).isEqualTo(20000);
        assertThat(connector.getProperty("maxSwallowSize")).isEqualTo(-1);
    }

    @Test
    public void largeResponsesAreGzipEncoded() throws Exception {
        HttpResponse<byte[]> response = getAcceptingGzip("/resources/js/views/BagGrid.js");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue(HttpHeaders.CONTENT_ENCODING)).contains("gzip");
    }

    @Test
    public void responsesBelowTwoKilobytesAreNotCompressed() throws Exception {
        HttpResponse<byte[]> response = getAcceptingGzip("/register/info");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse("")).startsWith("application/json");
        assertThat(response.headers().firstValue(HttpHeaders.CONTENT_ENCODING)).isEmpty();
    }

    @Test
    public void accessLogIsWritten() throws Exception {
        String path = "/resources/css/core.css?accessLogProbe=" + System.nanoTime();
        assertThat(myRestTemplate.getForEntity(path, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        File logDir = new File(((TomcatWebServer) myContext.getWebServer()).getTomcat().getServer().getCatalinaBase(),
                               "logs");
        assertThat(logDir.getCanonicalFile()).isEqualTo(new File("target/tomcat-settings-test/logs").getCanonicalFile());
        // Buffering is off in this test, but the entry is written after the response has been sent
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (accessLogContains(logDir, "\"GET " + path + " HTTP/1.1\" 200")) {
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("No access log entry for " + path + " in " + logDir + ": " + describe(logDir));
    }

    @Test
    public void multipartUploadsLargerThanTheFormLimitSucceed() {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("text", "");
        body.add("file", new ByteArrayResource(new byte[UPLOAD_SIZE_BYTES]) {
            @Override
            public String getFilename() {
                return "large.bag";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // An anonymous endpoint: the multipart body is parsed by MultipartFilter before the controller runs
        ResponseEntity<String> response = myRestTemplate.postForEntity(
                "/bags/filteredcount", new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
    }

    private HttpResponse<byte[]> getAcceptingGzip(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(myRestTemplate.getRootUri() + path))
                .header(HttpHeaders.ACCEPT_ENCODING, "gzip")
                .GET()
                .build();
        return myHttpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String describe(File logDir) throws IOException {
        File[] files = logDir.listFiles();
        if (files == null) {
            return "directory does not exist";
        }
        StringBuilder description = new StringBuilder();
        for (File file : files) {
            description.append(file.getName()).append(" [").append(Files.readString(file.toPath())).append("] ");
        }
        return description.toString();
    }

    private static boolean accessLogContains(File logDir, String text) throws IOException {
        File[] logs = logDir.listFiles((dir, name) -> name.startsWith("localhost_access_log"));
        if (logs == null) {
            return false;
        }
        for (File log : logs) {
            if (Files.readString(log.toPath(), StandardCharsets.UTF_8).contains(text)) {
                return true;
            }
        }
        return false;
    }
}
