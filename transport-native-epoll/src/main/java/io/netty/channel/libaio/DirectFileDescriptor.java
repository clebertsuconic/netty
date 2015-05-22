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
    public static DirectFileDescriptor from(String path, ByteBuffer ioContext) throws IOException {
        checkNotNull(path, "path");

        if (ioContext == null) {
            throw new IOException("Could not initialize libaio context");
        }

        int res = open(path);
        if (res < 0) {
            throw Native.newIOException("open", res);
        }

        return new DirectFileDescriptor(res, ioContext);
    }

    /**
     * Open a new {@link FileDescriptor} for the given {@link java.io.File}.
     */
    public static DirectFileDescriptor from(File file, ByteBuffer context) throws IOException {
        return from(checkNotNull(file, "file").getPath(), context);
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
    public static native ByteBuffer newContext(int queueSize);

    public static native void deleteContext(ByteBuffer buffer);

    private static native int open(String path);

    private static native void close(int fd);

    public boolean write(long position, int size, ByteBuffer buffer, Object callback)
    {
        // TODO circle IOCB
        return submitWrite(this.fd, libaioContext, position, size, buffer, callback);
    }

    public boolean read(long position, int size, ByteBuffer buffer, Object callback)
    {
        // TODO circle IOCB
        return submitRead(this.fd, libaioContext, position, size, buffer, callback);
    }

    /**
     * Buffers for O_DIRECT need to use posix_memalign *
     * @param size
     * @param alignment
     * @return
     */
    public static native ByteBuffer newAlignedBuffer(int size, int alignment);

    public static native void freeBuffer(ByteBuffer buffer);

    /**
     * Notice: this won't hold a global reference on bufferWrite, callback should hold a reference towards bufferWrite *
     * @param fd
     * @param libaioContext
     * @param position
     * @param size
     * @param bufferWrite
     * @param callback
     */
    private static native boolean submitWrite(int fd,
                                           ByteBuffer libaioContext,
                                           long position, int size, ByteBuffer bufferWrite,
                                           Object callback);

    /**
     * Notice: this won't hold a global reference on bufferWrite, callback should hold a reference towards bufferWrite *
     * @param fd
     * @param libaioContext
     * @param position
     * @param size
     * @param bufferWrite
     * @param callback
     */
    private static native boolean submitRead(int fd,
                                              ByteBuffer libaioContext,
                                              long position, int size, ByteBuffer bufferWrite,
                                              Object callback);

    /**
     * Note: this shouldn't be done concurrently. Only one caller doing poll.
     * We are not adding synchronization as there shouldn't be any *
     * * * */
    public static native int poll(ByteBuffer libaioContext, Object[] callbacks, int min, int max);
}
