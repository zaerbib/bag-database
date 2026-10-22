// *****************************************************************************
//
// Copyright (c) 2021, Hatchbed, L. L. C.
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

package com.github.swrirobotics.bags;

import com.github.swrirobotics.bags.reader.BagFile;
import com.github.swrirobotics.bags.reader.MessageHandler;
import com.github.swrirobotics.bags.reader.exceptions.BagReaderException;
import com.github.swrirobotics.bags.reader.messages.serialization.MessageCollection;
import com.github.swrirobotics.bags.reader.messages.serialization.MessageType;
import com.github.swrirobotics.bags.reader.messages.serialization.StringType;
import com.github.swrirobotics.config.ConfigService;
import com.github.swrirobotics.config.WebAppConfigurationAware;
import com.github.swrirobotics.persistence.Bag;
import com.github.swrirobotics.persistence.BagRepository;
import com.github.swrirobotics.persistence.MessageTypeRepository;
import com.github.swrirobotics.persistence.Tag;
import com.github.swrirobotics.persistence.TagRepository;
import com.github.swrirobotics.persistence.Topic;
import com.github.swrirobotics.persistence.TopicRepository;
import com.github.swrirobotics.support.web.BagList;
import com.github.swrirobotics.support.web.Configuration;
import com.github.swrirobotics.support.web.ExtJsFilter;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

public class BagServiceTest extends WebAppConfigurationAware {
    @Autowired
    BagService myBagService;
    @MockBean
    ConfigService myConfigService;
    @Autowired
    BagRepository myBagRepository;
    @Autowired
    MessageTypeRepository myMTRepository;
    @Autowired
    TagRepository myTagRepository;
    @Autowired
    TopicRepository myTopicRepository;

    private static final Logger myLogger = LoggerFactory.getLogger(BagServiceTest.class);

    @Test
    public void testGetMetadata() throws BagReaderException {
        // Set up a mock bag file to provide some test data to the service
        BagFile mockBagFile = mock(BagFile.class);
        myLogger.info("testGetMetadata");
        Configuration tmpConfig = new Configuration();
        // Set up one topic that has good data on it, and one topic that has bad data
        tmpConfig.setMetadataTopics(new String[]{"good", "bad"});
        when(myConfigService.getConfiguration()).thenReturn(tmpConfig);
        doAnswer((i) -> {
            // Create a standard ROS std_msgs/String message
            MessageType msg = new MessageType("MSG: std_msgs/String\nstring data", new MessageCollection());
            StringType str = msg.getField("data");
            boolean isGood = i.getArgument(0, String.class).equals("good");
            if (isGood) {
                str.setValue("name: John Doe\nemail: jdoe@example.com\n");
            }
            else {
                str.setValue("name: John Doe\nemail: jdoe@example.com\nbadvalue\n");
            }
            var handler = i.getArgument(1, MessageHandler.class);
            handler.process(msg, null);
            myLogger.info(i.getArgument(1).toString());
            return null;
        }).when(mockBagFile).forMessagesOnTopic(any(), any());

        var metadata = myBagService.getMetadata(mockBagFile);

        assertFalse(metadata.isEmpty());
    }

    @Test
    public void testDecodeBgra() {
        byte[] data = {1, 2, 3, 4};
        var image = myBagService.decodeImage(1, 1, "bgra8", BufferedImage.TYPE_INT_ARGB, data);

        assertEquals(1, image.getHeight());
        assertEquals(1, image.getWidth());

        Color color = new Color(image.getRGB(0, 0));
        assertEquals(3, color.getRed());
        assertEquals(2, color.getGreen());
        assertEquals(1, color.getBlue());
    }

    @Test
    public void testDecodeRgba() {
        byte[] data = {1, 2, 3, 4};
        var image = myBagService.decodeImage(1, 1, "rgba8", BufferedImage.TYPE_INT_ARGB, data);

        assertEquals(1, image.getHeight());
        assertEquals(1, image.getWidth());

        Color color = new Color(image.getRGB(0, 0));
        assertEquals(1, color.getRed());
        assertEquals(2, color.getGreen());
        assertEquals(3, color.getBlue());
    }

    private Bag insertBareBag(String filename, String md5sum) {
        Bag bag = new Bag();
        bag.setMd5sum(md5sum);
        bag.setCreatedOn(new Timestamp(System.currentTimeMillis()));
        bag.setDuration(0.0);
        bag.setStartTime(new Timestamp(System.currentTimeMillis()));
        bag.setEndTime(new Timestamp(System.currentTimeMillis()));
        bag.setFilename(filename);
        bag.setPath("/search/");
        bag.setVersion("2.0");
        bag.setCompressed(false);
        bag.setIndexed(true);
        bag.setMessageCount(0L);
        bag.setMissing(false);
        bag.setSize(0L);
        return myBagRepository.save(bag);
    }

    private void addTag(Bag bag, String key, String value) {
        Tag tag = new Tag();
        tag.setTag(key);
        tag.setValue(value);
        tag.setBag(bag);
        bag.getTags().add(myTagRepository.save(tag));
    }

    private Long insertSearchableBag() {
        Bag bag = insertBareBag("search.bag", "searchmd5sum");
        // Two tags so that the joins produce several rows for the same bag
        addTag(bag, "SearchTagKey", "SearchTagValue");
        addTag(bag, "OtherTagKey", "OtherTagValue");

        // Fully qualified to avoid clashing with the bag reader's MessageType
        var mt = new com.github.swrirobotics.persistence.MessageType();
        mt.setName("search_msgs/SearchType");
        mt.setMd5sum("searchtypemd5sum");
        mt = myMTRepository.save(mt);
        bag.getMessageTypes().add(mt);

        Topic topic = new Topic();
        topic.setBag(bag);
        topic.setMessageCount(0L);
        topic.setConnectionCount(0L);
        topic.setType(mt);
        mt.getTopics().add(topic);
        topic.setTopicName("/search/topic");
        topic = myTopicRepository.save(topic);
        bag.getTopics().add(topic);

        return myBagRepository.save(bag).getId();
    }

    private List<Long> searchBagIds(String text, String... fields) {
        BagList result = myBagService.findBagsContainingText(text, fields, null, 1, 100, "ASC", "filename");
        return result.getBags().stream().map(Bag::getId).collect(Collectors.toList());
    }

    private void assertFoundOnce(Long bagId, String text, String... fields) {
        assertEquals(1, Collections.frequency(searchBagIds(text, fields), bagId));
    }

    private void assertNotFound(Long bagId, String text, String... fields) {
        assertFalse(searchBagIds(text, fields).contains(bagId));
    }

    @Test
    @Transactional
    public void testFindBagsByMessageType() {
        Long bagId = insertSearchableBag();

        assertFoundOnce(bagId, "SEARCHTYPE", "messageType");
        // Text that only exists in the bag's tags and topics
        assertNotFound(bagId, "searchtagkey", "messageType");
        assertNotFound(bagId, "/search/topic", "messageType");
    }

    @Test
    @Transactional
    public void testFindBagsByTags() {
        Long bagId = insertSearchableBag();

        assertFoundOnce(bagId, "searchtagkey", "tags");
        assertFoundOnce(bagId, "searchtagvalue", "tags");
        // Text that only exists in the bag's message types and topics
        assertNotFound(bagId, "searchtype", "tags");
        assertNotFound(bagId, "/search/topic", "tags");
    }

    @Test
    @Transactional
    public void testFindBagsByTopicName() {
        Long bagId = insertSearchableBag();

        assertFoundOnce(bagId, "/SEARCH/TOPIC", "topicName");
        // Text that only exists in the bag's tags and message types
        assertNotFound(bagId, "searchtagvalue", "topicName");
        assertNotFound(bagId, "searchtype", "topicName");
    }

    @Test
    @Transactional
    public void testFindBagsByMultipleFields() {
        Long bagId = insertSearchableBag();
        // A bag without tags, message types or topics must still be found by the
        // other fields, since the collections are LEFT joined
        Long bareBagId = insertBareBag("leftjoin-probe.bag", "leftjoinmd5sum").getId();
        String[] fields = {"filename", "tags", "messageType", "topicName"};

        assertFoundOnce(bagId, "search", fields);
        assertFoundOnce(bareBagId, "leftjoin-probe", fields);
        assertNotFound(bagId, "leftjoin-probe", fields);
    }

    // Fixed instant so that the date filters can bracket it
    private static final long PROBE_TIME = 1_000_000_000_000L;

    private Long insertFilterProbeBag() {
        Bag bag = insertBareBag("filterprobe.bag", "filterprobemd5sum");
        bag.setStorageId("probestorage");
        bag.setLocation("Probe Location");
        bag.setVehicle("Probe Vehicle");
        bag.setDescription("Probe Description");
        bag.setDuration(12.5);
        bag.setSize(7L);
        bag.setMessageCount(42L);
        bag.setCoordinate(myBagService.makePoint(29.5, -98.5));
        bag.setCreatedOn(new Timestamp(PROBE_TIME));
        bag.setUpdatedOn(new Timestamp(PROBE_TIME));
        bag.setStartTime(new Timestamp(PROBE_TIME));
        bag.setEndTime(new Timestamp(PROBE_TIME));
        return myBagRepository.save(bag).getId();
    }

    private static ExtJsFilter filter(String property, String operator, String value) {
        ExtJsFilter filter = new ExtJsFilter();
        filter.setProperty(property);
        filter.setOperator(operator);
        filter.setValue(value);
        return filter;
    }

    private List<Long> filterBagIds(ExtJsFilter... filters) {
        BagList result = myBagService.findBagsContainingText("", null, filters, 1, 100, "ASC", "filename");
        return result.getBags().stream().map(Bag::getId).collect(Collectors.toList());
    }

    private void assertFilterMatches(Long bagId, ExtJsFilter... filters) {
        assertEquals(1, Collections.frequency(filterBagIds(filters), bagId));
    }

    @Test
    @Transactional
    public void testSearchAcceptsEveryUiTextField() {
        Long bagId = insertFilterProbeBag();
        addTag(myBagRepository.findById(bagId).orElseThrow(), "ProbeTagKey", "ProbeTagValue");

        assertFoundOnce(bagId, "filterprobe", "filename");
        assertFoundOnce(bagId, "probe description", "description");
        assertFoundOnce(bagId, "probetagkey", "tags");
        assertFoundOnce(bagId, "/search/", "path");
        assertFoundOnce(bagId, "probe location", "location");
        assertFoundOnce(bagId, "probe vehicle", "vehicle");
        // No message types or topics on this bag; the fields must still be accepted
        assertNotFound(bagId, "filterprobe", "messageType");
        assertNotFound(bagId, "filterprobe", "topicName");
    }

    @Test
    @Transactional
    public void testBlankTextIgnoresFieldNames() {
        Long bagId = insertFilterProbeBag();
        // BagStore.js sends these defaults on the first load; "topic" is not a Bag attribute
        BagList result = myBagService.findBagsContainingText("", new String[]{"messageType", "topic"},
                                                             null, 1, 100, "ASC", "filename");
        assertTrue(result.getBags().stream().anyMatch(b -> b.getId().equals(bagId)));
    }

    @Test
    @Transactional
    public void testFilterAcceptsEveryGridColumn() {
        Long bagId = insertFilterProbeBag();
        String before = String.valueOf(PROBE_TIME - 1);
        String after = String.valueOf(PROBE_TIME + 1);

        assertFilterMatches(bagId, filter("id", "eq", String.valueOf(bagId)));
        assertFilterMatches(bagId, filter("storageId", "like", "PROBESTORAGE"));
        assertFilterMatches(bagId, filter("path", "like", "/search/"));
        assertFilterMatches(bagId, filter("filename", "like", "filterprobe"));
        assertFilterMatches(bagId, filter("location", "like", "probe loc"));
        assertFilterMatches(bagId, filter("vehicle", "like", "probe veh"));
        assertFilterMatches(bagId, filter("description", "like", "probe desc"));
        assertFilterMatches(bagId, filter("latitudeDeg", "gt", "29"), filter("latitudeDeg", "lt", "30"));
        assertFilterMatches(bagId, filter("longitudeDeg", "gt", "-99"), filter("longitudeDeg", "lt", "-98"));
        assertFilterMatches(bagId, filter("missing", "=", "false"));
        assertFilterMatches(bagId, filter("md5sum", "like", "filterprobemd5"));
        assertFilterMatches(bagId, filter("duration", "gt", "12"), filter("duration", "lt", "13"));
        assertFilterMatches(bagId, filter("size", "eq", "7"));
        assertFilterMatches(bagId, filter("messageCount", "eq", "42"));
        for (String dateField : new String[]{"createdOn", "updatedOn", "startTime", "endTime"}) {
            assertFilterMatches(bagId, filter(dateField, "gt", before), filter(dateField, "lt", after));
            assertFilterMatches(bagId, filter(dateField, "eq", String.valueOf(PROBE_TIME)));
        }

        // The filters are applied, not just accepted
        assertFalse(filterBagIds(filter("vehicle", "like", "no such vehicle")).contains(bagId));
        assertFalse(filterBagIds(filter("size", "eq", "8")).contains(bagId));
        assertFalse(filterBagIds(filter("latitudeDeg", "gt", "30")).contains(bagId));
        assertFalse(filterBagIds(filter("missing", "=", "true")).contains(bagId));
        assertFalse(filterBagIds(filter("startTime", "gt", after)).contains(bagId));
    }

    @Test
    @Transactional
    public void testSortAcceptsEveryGridColumn() {
        Long bagId = insertFilterProbeBag();
        String[] sortable = {"id", "storageId", "path", "filename", "location", "vehicle", "description",
                             "latitudeDeg", "longitudeDeg", "missing", "md5sum", "duration", "createdOn",
                             "updatedOn", "startTime", "endTime", "size", "messageCount"};
        for (String sort : sortable) {
            for (String dir : new String[]{"ASC", "DESC"}) {
                BagList result = myBagService.findBagsContainingText("", null, null, 1, 100, dir, sort);
                assertTrue(sort + " " + dir,
                           result.getBags().stream().anyMatch(b -> b.getId().equals(bagId)));
            }
        }
    }

    private void assertRejected(String text, String[] fields, ExtJsFilter[] filters, String sort) {
        try {
            myBagService.findBagsContainingText(text, fields, filters, 1, 100, "ASC", sort);
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException e) {
            // Hibernate also throws IllegalArgumentException for unknown attributes;
            // make sure the allow-list is what rejected it
            assertTrue(e.getMessage(), e.getMessage().startsWith("Unsupported "));
        }
    }

    @Test
    @Transactional
    public void testSearchEndpointRejectsUnknownFields() throws Exception {
        insertFilterProbeBag();
        String[][] badParams = {
            {"fields", "../../etc"},
            {"sort", "version"},
            {"filter", "[{\"property\":\"tags.value\",\"operator\":\"like\",\"value\":\"x\"}]"}
        };
        for (String[] bad : badParams) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("text", "probe");
            params.put("fields", "filename");
            params.put("page", "1");
            params.put("limit", "100");
            params.put("sort", "filename");
            params.put("dir", "ASC");
            params.put(bad[0], bad[1]);
            MockHttpServletRequestBuilder request = get("/bags/search");
            params.forEach(request::param);
            mockMvc.perform(request)
                .andExpect(view().name("error/general"))
                .andExpect(model().attribute("errorMessage", containsString("Unsupported ")))
                .andExpect(content().string(not(containsString("filterprobe.bag"))));
        }
    }

    @Test
    @Transactional
    public void testSearchRejectsUnknownTextField() {
        insertFilterProbeBag();
        for (String field : new String[]{"version", "md5sum", "coordinate", "scriptResults", "tags.value", "../x"}) {
            assertRejected("probe", new String[]{"filename", field}, null, "filename");
        }
    }

    @Test
    @Transactional
    public void testFilterRejectsUnknownProperty() {
        insertFilterProbeBag();
        for (String property : new String[]{"version", "indexed", "coordinate", "scriptResults", "tags.value", null}) {
            assertRejected("", null, new ExtJsFilter[]{filter(property, "like", "x")}, "filename");
        }
    }

    @Test
    @Transactional
    public void testSortRejectsUnknownProperty() {
        insertFilterProbeBag();
        for (String sort : new String[]{"version", "tags", "tags.value", "scriptResults.id", "coordinate"}) {
            assertRejected("", null, null, sort);
            // Also on the Specification path
            assertRejected("probe", new String[]{"filename"}, null, sort);
        }
    }
}
