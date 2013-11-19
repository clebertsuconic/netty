/*
 * Copyright 2013 The Netty Project
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
package io.netty.buffer.jni;

import java.nio.ByteBuffer;

/**
 * Native helper methods
 */
final class Native {

    static {
        // Load the library
        // NarSystem is auto-created by the nar plugin
        NarSystem.loadLibrary();
    }

    // Buffer functions
    public static native void freeDirectBuffer(ByteBuffer buf);
    public static native ByteBuffer allocateDirectBuffer(int size);

    // epoll functions

    /**
     * Refer to linux man-pages on epoll. this directly translates as epoll_create
     * @param size
     * @return
     */
    public static native int epollCreate(int size);

    private Native() {
    }
}
