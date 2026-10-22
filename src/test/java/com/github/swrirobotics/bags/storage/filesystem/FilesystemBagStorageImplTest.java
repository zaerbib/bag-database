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

package com.github.swrirobotics.bags.storage.filesystem;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * Upload path handling. The storage lives in base/, inside a temp folder,
 * so anything that escapes it lands in the temp folder and can be detected.
 */
public class FilesystemBagStorageImplTest {
    @Rule
    public TemporaryFolder myTempFolder = new TemporaryFolder();

    private File myBase;
    private FilesystemBagStorageImpl myStorage;

    @Before
    public void setUp() throws Exception {
        myBase = myTempFolder.newFolder("outer", "base");
        FilesystemBagStorageConfigImpl config = new FilesystemBagStorageConfigImpl();
        config.storageId = "default";
        config.basePath = myBase.getAbsolutePath();
        myStorage = new FilesystemBagStorageImpl(null, null);
        myStorage.loadConfig(config);
    }

    private static MockMultipartFile bag(String filename) {
        return new MockMultipartFile("file", filename, "application/octet-stream", new byte[]{1, 2, 3});
    }

    private long filesOutsideBase() throws IOException {
        try (Stream<java.nio.file.Path> files = Files.walk(myTempFolder.getRoot().toPath())) {
            return files.filter(Files::isRegularFile)
                .filter(p -> !p.startsWith(myBase.toPath()))
                .count();
        }
    }

    private void assertRejected(String targetDirectory, String filename) throws Exception {
        try {
            myStorage.uploadBag(bag(filename), targetDirectory);
            fail("Expected upload of " + filename + " to " + targetDirectory + " to be rejected");
        }
        catch (IOException e) {
            // expected
        }
        assertEquals(0, filesOutsideBase());
    }

    @Test
    public void uploadsToBaseDirectory() throws Exception {
        myStorage.uploadBag(bag("test.bag"), "/");
        assertTrue(new File(myBase, "test.bag").isFile());
    }

    @Test
    public void uploadsToNewSubdirectory() throws Exception {
        myStorage.uploadBag(bag("test.bag"), "a/b");
        assertTrue(new File(myBase, "a/b/test.bag").isFile());
    }

    @Test
    public void leadingSlashIsRelativeToBase() throws Exception {
        myStorage.uploadBag(bag("test.bag"), "/a");
        assertTrue(new File(myBase, "a/test.bag").isFile());
    }

    @Test(expected = IOException.class)
    public void doesNotOverwriteExistingFile() throws Exception {
        myStorage.uploadBag(bag("test.bag"), "/");
        myStorage.uploadBag(bag("test.bag"), "/");
    }

    @Test
    public void rejectsTraversalInTargetDirectory() throws Exception {
        assertRejected("../../etc", "test.bag");
        assertRejected("a/../../x", "test.bag");
        assertRejected("..\\..\\etc", "test.bag");
        assertRejected("C:\\Windows", "test.bag");
    }

    @Test
    public void stripsDirectoriesFromFilename() throws Exception {
        myStorage.uploadBag(bag("../evil.bag"), "/");
        myStorage.uploadBag(bag("..\\..\\evil2.bag"), "/");
        assertTrue(new File(myBase, "evil.bag").isFile());
        assertTrue(new File(myBase, "evil2.bag").isFile());
        assertEquals(0, filesOutsideBase());
    }

    @Test
    public void rejectsUnusableFilenames() throws Exception {
        assertRejected("/", "..");
        assertRejected("/", "");
        assertRejected("/", "a/");
    }
}
