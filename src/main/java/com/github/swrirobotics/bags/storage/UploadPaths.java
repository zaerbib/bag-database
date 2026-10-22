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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates the client-supplied parts of an upload destination so that a
 * storage backend can never be asked to write outside of its root.
 */
public final class UploadPaths {
    private UploadPaths() {
    }

    /**
     * Converts a client-supplied target directory into a path relative to the
     * storage root, using '/' as separator. Leading separators are ignored, so
     * "/" and "" both mean the root itself.
     * @throws IOException if the directory contains a ".." segment or starts with a drive letter.
     */
    public static String relativeDirectory(String targetDirectory) throws IOException {
        if (targetDirectory == null) {
            return "";
        }
        List<String> segments = new ArrayList<>();
        for (String segment : targetDirectory.split("[/\\\\]")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            boolean driveLetter = segments.isEmpty() && segment.matches("[A-Za-z]:");
            if (segment.equals("..") || driveLetter || segment.indexOf('\0') >= 0) {
                throw new IOException("Invalid target directory: " + targetDirectory);
            }
            segments.add(segment);
        }
        return String.join("/", segments);
    }

    /**
     * Reduces a client-supplied file name to its last path segment; some
     * browsers send a full client-side path.
     * @throws IOException if nothing usable is left.
     */
    public static String sanitizeFilename(String originalFilename) throws IOException {
        if (originalFilename == null) {
            throw new IOException("Missing file name.");
        }
        String name = originalFilename.substring(
            Math.max(originalFilename.lastIndexOf('/'), originalFilename.lastIndexOf('\\')) + 1);
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.indexOf('\0') >= 0) {
            throw new IOException("Invalid file name: " + originalFilename);
        }
        return name;
    }
}
