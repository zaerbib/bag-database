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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketServletAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Import;

/**
 * The application's single entry point, in both launch modes:
 * <ul>
 *     <li>{@code java -jar bag-database.war} calls {@link #main} and starts an embedded Tomcat;</li>
 *     <li>an external Tomcat finds this {@link SpringBootServletInitializer} when it deploys the WAR.</li>
 * </ul>
 * Everything is still configured explicitly by the imported config classes.  Only the auto-configurations
 * needed to run the embedded server are imported; the rest of Boot's auto-configuration would compete
 * with the hand-wired DataSource, JPA, Liquibase, Spring Session, Thymeleaf, MVC and Security beans.
 */
@SpringBootConfiguration
@Import({ApplicationConfig.class,
         JpaConfig.class,
         LiquibaseConfig.class,
         WebSocketConfig.class,
         SecurityConfig.class,
         WebMvcConfig.class,
         WebServerConfig.class})
// DispatcherServletAutoConfiguration is not needed: WebServerConfig registers the DispatcherServlet itself
@ImportAutoConfiguration({ServletWebServerFactoryAutoConfiguration.class,
                          WebSocketServletAutoConfiguration.class})
public class BagDatabaseApplication extends SpringBootServletInitializer {
    public static void main(String[] args) {
        SpringApplication.run(BagDatabaseApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(BagDatabaseApplication.class);
    }
}
