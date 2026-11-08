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

package com.github.swrirobotics.bags.storage;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class UploadPathsTest {
    @Test
    public void relativeDirectoryNormalizesSeparators() throws Exception {
        assertEquals("", UploadPaths.relativeDirectory("/"));
        assertEquals("", UploadPaths.relativeDirectory(""));
        assertEquals("", UploadPaths.relativeDirectory(null));
        assertEquals("a/b", UploadPaths.relativeDirectory("/a//b/"));
        assertEquals("a/b", UploadPaths.relativeDirectory("a\\b"));
        assertEquals("a/b", UploadPaths.relativeDirectory("./a/./b"));
        // Colons are legal in directory names on Linux; only a leading drive letter is refused.
        assertEquals("runs/2020-01-01T10:00", UploadPaths.relativeDirectory("runs/2020-01-01T10:00"));
    }

    @Test(expected = IOException.class)
    public void relativeDirectoryRejectsParentSegment() throws Exception {
        UploadPaths.relativeDirectory("a/../b");
    }

    @Test(expected = IOException.class)
    public void relativeDirectoryRejectsBackslashParentSegment() throws Exception {
        UploadPaths.relativeDirectory("..\\etc");
    }

    @Test(expected = IOException.class)
    public void relativeDirectoryRejectsDriveLetter() throws Exception {
        UploadPaths.relativeDirectory("C:\\Windows");
    }

    @Test
    public void sanitizeFilenameKeepsLastSegment() throws Exception {
        assertEquals("test.bag", UploadPaths.sanitizeFilename("test.bag"));
        assertEquals("evil.bag", UploadPaths.sanitizeFilename("../../evil.bag"));
        assertEquals("evil.bag", UploadPaths.sanitizeFilename("C:\\Users\\me\\evil.bag"));
    }

    @Test(expected = IOException.class)
    public void sanitizeFilenameRejectsDotDot() throws Exception {
        UploadPaths.sanitizeFilename("..");
    }

    @Test(expected = IOException.class)
    public void sanitizeFilenameRejectsEmpty() throws Exception {
        UploadPaths.sanitizeFilename("dir/");
    }

    @Test(expected = IOException.class)
    public void sanitizeFilenameRejectsNull() throws Exception {
        UploadPaths.sanitizeFilename(null);
    }

    @Test(expected = IOException.class)
    public void sanitizeFilenameRejectsNulCharacter() throws Exception {
        UploadPaths.sanitizeFilename("a\0.bag");
    }
}
