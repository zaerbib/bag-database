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

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Only the rejection path is tested: with the PostgreSQL driver, dataSource() opens a real connection.
public class JpaConfigTest {
    private JpaConfig configWithDriver(String driver) {
        DataSourceProperties properties = mock(DataSourceProperties.class);
        when(properties.getDriver()).thenReturn(driver);
        when(properties.getUrl()).thenReturn("jdbc:test");
        when(properties.getUsername()).thenReturn("user");
        when(properties.getPassword()).thenReturn("secret");

        JpaConfig config = new JpaConfig();
        ReflectionTestUtils.setField(config, "properties", properties);
        return config;
    }

    @Test
    public void dataSourceFailsFastOnH2Driver() {
        assertThatThrownBy(() -> configWithDriver("org.h2.Driver").dataSource())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("org.postgresql.Driver");
    }

    @Test
    public void dataSourceFailsFastOnHsqldbDriver() {
        assertThatThrownBy(() -> configWithDriver("org.hsqldb.jdbcDriver").dataSource())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("org.postgresql.Driver");
    }

    @Test
    public void dataSourceFailsFastOnMissingDriver() {
        assertThatThrownBy(() -> configWithDriver(null).dataSource())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("org.postgresql.Driver");
    }
}
