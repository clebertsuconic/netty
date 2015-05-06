/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.libaio;

import io.netty.channel.epoll.Native;
import io.netty.channel.unix.FileDescriptor;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static io.netty.util.internal.ObjectUtil.*;

/**
 * This is an extension to {@link io.netty.channel.unix.FileDescriptor} where the file is open with O_DIRECT *
 */
public final class DirectFileDescriptor extends FileDescriptor {
    static {
        Native.loadLibrary();
    }

    /**
     * This represents a structure allocated on the native
     * this is a io_context_t
     * * *  */
    private ByteBuffer libaioContext;

    private DirectFileDescriptor(int fd, ByteBuffer libaioContext) {
        super(fd);
        this.libaioContext = libaioContext;
    }

    /**
     * Open a new {@link FileDescriptor} for the given path.
     */
    public static DirectFileDescriptor from(String path, int size) throws IOException {
        checkNotNull(path, "path");
        ByteBuffer libaioContext = initContext(size);

        if (libaioContext == null) {
            throw new IOException("Could not initialize libaio context for size " + size);
        }

        int res = open(path);
        if (res < 0) {
            throw Native.newIOException("open", res);
        }

        return new DirectFileDescriptor(res, libaioContext);
    }

    /**
     * Open a new {@link FileDescriptor} for the given {@link java.io.File}.
     */
    public static DirectFileDescriptor from(File file, int size) throws IOException {
        return from(checkNotNull(file, "file").getPath(), size);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        open = false;
        close(fd);
    }

    /**
     * This is the queue for libaio, initialized with i*
     * @param queueSize
     * @return null if there isn't enough space on /ev/MAX_AIO
     */
    public static native ByteBuffer initContext(int queueSize);

    private static native int open(String path);

    private static native void close(int fd);

}
