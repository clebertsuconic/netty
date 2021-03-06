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
package io.netty.jni.libaio;

import io.netty.channel.epoll.Native;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * This class is used as an aggregator for the {@link LibaioFile}.
 *
 * It holds native data, and it will share a libaio queue that can be used by multiple files.
 *
 * You need to use the poll methods to read the result of write and read submissions.
 *
 * You also need to use the special buffer created by {@link LibaioFile} as you need special alignments
 * when dealing with O_DIRECT files.
 *
 * A Single controller can server multiple files. There's no need to create one controller per file.
 *
 * <a href="https://ext4.wiki.kernel.org/index.php/Clarifying_Direct_IO's_Semantics">Interesting reading for this.</a>
 */
public class LibaioContext {
    static {
        Native.loadLibrary();
    }

    /**
     *  the native ioContext including the structure created.
     */
    ByteBuffer ioContext;

    /**
     * The queue size here will use resources defined on the kernel parameter
     *    <a href="https://www.kernel.org/doc/Documentation/sysctl/fs.txt">fs.aio-max-nr</a> .
     *
     * @param queueSize the size to be initialize on libaio io_queue_init.
     */
    public LibaioContext(int queueSize) {
        this.ioContext = newContext(queueSize);
    }

    /**
     * This is used to close the libaio queues and cleanup the native data used.
     *
     * It is unsafe to close the controller while you have pending writes or files open as
     * this could cause core dumps or VM crashes.
     */
    public void close() {
        if (ioContext != null) {
            deleteContext(ioContext);
            ioContext = null;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        close();
    }

    /**
     * It will open a file. If you set the direct flag = false then you won't need to use the special buffer.
     * Notice: This will create an empty file if the file doesn't already exist.
     *
     * @param direct should use O_DIRECT when opening the file.
     */
    public LibaioFile openFile(File file, boolean direct) throws IOException {
        return openFile(file.getPath(), direct);
    }

    /**
     * It will open a file. If you set the direct flag = false then you won't need to use the special buffer.
     * Notice: This will create an empty file if the file doesn't already exist.
     *
     * @param direct should use O_DIRECT when opening the file.
     */
    public LibaioFile openFile(String file, boolean direct) throws IOException {
        checkNotNull(file, "path");
        checkNotNull(ioContext, "IOContext");

        // note: the native layer will throw an IOException in case of errors
        int res = LibaioContext.open(file, direct);

        return new LibaioFile(res, ioContext);
    }

    /**
     * It will poll the libaio queue for results. It should block until min is reached
     * Results are placed on the callback.
     *
     * This shouldn't be called concurrently. You should provide your own synchronization if you need more than one
     * Thread polling for any reason.
     *
     * @param callbacks area to receive the callbacks passed on submission. In case of a failure you will see an
     *                  {@link ErrorInfo} as an element. The size of this callback has to be greater than the
     *                  parameter max.
     *
     * @param min the minimum number of elements to receive. It will block until this is achieved.
     * @param max The maximum number of elements to receive.
     * @return Number of callbacks returned.
     *
     * @see LibaioFile#write(long, int, ByteBuffer, Object)
     * @see LibaioFile#read(long, int, ByteBuffer, Object)
     */
    public int poll(Object[] callbacks, int min, int max) {
        return poll(ioContext, callbacks, min, max);
    }

    /**
     * This is the queue for libaio, initialized with queueSize.
     */
    static native ByteBuffer newContext(int queueSize);

    /**
     * Internal method to be used when closing the controller.
     */
    static native void deleteContext(ByteBuffer buffer);

    static native int open(String path, boolean direct);

    static native void close(int fd);

    /**
     * Buffers for O_DIRECT need to use posix_memalign.
     *
     * Documented at {@link LibaioFile#newBuffer(int)}.
     */
    public static native ByteBuffer newAlignedBuffer(int size, int alignment);

    /**
     * This will call posix free to release the inner buffer allocated at {@link #newAlignedBuffer(int, int)}.
     */
    public static native void freeBuffer(ByteBuffer buffer);

    /**
     * Documented at {@link LibaioFile#write(long, int, java.nio.ByteBuffer, Object)}.
     */
    static native boolean submitWrite(int fd,
                                   ByteBuffer libaioContext,
                                   long position, int size, ByteBuffer bufferWrite,
                                   Object callback) throws IOException;

    /**
     * Documented at {@link LibaioFile#read(long, int, java.nio.ByteBuffer, Object)}.
     */
    static native boolean submitRead(int fd,
                                  ByteBuffer libaioContext,
                                  long position, int size, ByteBuffer bufferWrite,
                                  Object callback) throws IOException;

    /**
     * Note: this shouldn't be done concurrently.
     * This method will block until the min condition is satisfied on the poll.
     *
     * The callbacks will include the original callback sent at submit (read or write).
     * In case of error the element will have an instance of {@link ErrorInfo}.
     */
    native int poll(ByteBuffer libaioContext, Object[] callbacks, int min, int max);
}
