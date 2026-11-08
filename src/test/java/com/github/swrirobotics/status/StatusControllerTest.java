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

package com.github.swrirobotics.status;

import com.github.swrirobotics.config.WebAppConfigurationAware;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.Map;
import java.util.Queue;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class StatusControllerTest extends WebAppConfigurationAware {
    @MockBean
    private StatusService myStatusService;

    public FieldDescriptor[] getStatusFields() {
        return new FieldDescriptor[] {
            fieldWithPath("state").description("State of the component; one of IDLE, WORKING, ERROR"),
            fieldWithPath("message").description("Human-readable status message"),
            fieldWithPath("time").description("Timestamp of the status update").type(JsonFieldType.NUMBER)
        };
    }

    @Test
    public void getErrors() throws Exception {
        Queue<Status> errors = Queues.newArrayDeque();
        errors.add(new Status(Status.State.ERROR, "Unable to read bag"));
        when(myStatusService.getErrors()).thenReturn(errors);

        mockMvc.perform(get("/status/errors"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].state").value("ERROR"))
            .andExpect(jsonPath("$[0].message").value("Unable to read bag"))
            .andDo(document("status/{method-name}",
                preprocessResponse(prettyPrint()),
                responseFields(
                    fieldWithPath("[]").description("All errors reported since they were last cleared")
                ).andWithPrefix("[].", getStatusFields())
            ));
    }

    @Test
    public void getStates() throws Exception {
        Map<String, Status> states = Maps.newHashMap();
        states.put("Bag Scanner", new Status(Status.State.WORKING, "Scanning"));
        when(myStatusService.getStates()).thenReturn(states);

        mockMvc.perform(get("/status/list"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$['Bag Scanner'].state").value("WORKING"))
            .andExpect(jsonPath("$['Bag Scanner'].message").value("Scanning"))
            .andDo(document("status/{method-name}",
                preprocessResponse(prettyPrint()),
                responseFields(
                    fieldWithPath("['Bag Scanner']").description("Most recent status of each component, keyed by component name")
                ).andWithPrefix("['Bag Scanner'].", getStatusFields())
            ));
    }

    @Test
    public void getLatest() throws Exception {
        StatusService.LatestStatus latest = new StatusService().new LatestStatus();
        latest.update("Bag Scanner", new Status(Status.State.IDLE, "Done"));
        when(myStatusService.getLatestStatus()).thenReturn(latest);

        mockMvc.perform(get("/status/latest"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.source").value("Bag Scanner"))
            .andExpect(jsonPath("$.errorCount").value(0))
            .andExpect(jsonPath("$.status.state").value("IDLE"))
            .andExpect(jsonPath("$.status.message").value("Done"))
            .andDo(document("status/{method-name}",
                preprocessResponse(prettyPrint()),
                responseFields(
                    fieldWithPath("source").description("Name of the component that sent the most recent status"),
                    fieldWithPath("errorCount").description("Number of errors reported since they were last cleared"),
                    fieldWithPath("status").description("The most recent status")
                ).andWithPrefix("status.", getStatusFields())
            ));
    }

    @Test
    public void clearErrors() throws Exception {
        mockMvc.perform(get("/status/clear"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().string("true"))
            .andDo(document("status/{method-name}",
                preprocessResponse(prettyPrint())));

        verify(myStatusService).clearErrors();
    }
}
