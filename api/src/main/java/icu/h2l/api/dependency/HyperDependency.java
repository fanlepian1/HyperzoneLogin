/*
 * This file is derived from LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package icu.h2l.api.dependency;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

/**
 * Adapted from LuckPerms' dependency descriptor for HyperZoneLogin runtime library loading.
 */
public final class HyperDependency {
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final byte[] checksum;

    public HyperDependency(String groupId, String artifactId, String version, String checksum) {
        this.groupId = Objects.requireNonNull(groupId, "groupId");
        this.artifactId = Objects.requireNonNull(artifactId, "artifactId");
        this.version = Objects.requireNonNull(version, "version");
        this.checksum = Base64.getDecoder().decode(Objects.requireNonNull(checksum, "checksum"));
    }

    public String getGroupId() {
        return this.groupId;
    }

    public String getArtifactId() {
        return this.artifactId;
    }

    public String getVersion() {
        return this.version;
    }

    public byte[] getChecksum() {
        return this.checksum.clone();
    }

    public boolean checksumMatches(byte[] hash) {
        return Arrays.equals(this.checksum, hash);
    }

    public String getMavenRepoPath() {
        return this.groupId.replace('.', '/') + "/" + this.artifactId + "/" + this.version + "/" + getFileName();
    }

    public String getFileName() {
        return this.artifactId + "-" + this.version + ".jar";
    }

    public String id() {
        return this.groupId + ":" + this.artifactId + ":" + this.version;
    }

    public String cacheFileName() {
        return (this.groupId + "-" + this.artifactId + "-" + this.version + ".jar")
            .toLowerCase(Locale.ROOT)
            .replace(':', '-')
            .replace('.', '-');
    }

    public static MessageDigest createDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HyperDependency that)) {
            return false;
        }
        return this.groupId.equals(that.groupId)
            && this.artifactId.equals(that.artifactId)
            && this.version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.groupId, this.artifactId, this.version);
    }

    @Override
    public String toString() {
        return id();
    }
}


