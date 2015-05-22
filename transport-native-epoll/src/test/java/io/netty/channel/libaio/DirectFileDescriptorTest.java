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

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public class DirectFileDescriptorTest {
    @Rule
    public TemporaryFolder temporaryFolder;

    public DirectFileDescriptorTest() {
        File parent = new File("./target");
        parent.mkdirs();
        temporaryFolder = new TemporaryFolder(parent);
    }

    @Test
    public void testOpen() throws Exception {
        ByteBuffer context = DirectFileDescriptor.newContext(50);
        DirectFileDescriptor fileDescriptor = DirectFileDescriptor.from(temporaryFolder.newFile("test.bin"), context);
        fileDescriptor.close();
        DirectFileDescriptor.deleteContext(context);
    }

    @Test
    public void testSubmitWriteAndRead() throws Exception {

        Object callback = new Object();

        Object[] callbacks = new Object[50];

        ByteBuffer context = DirectFileDescriptor.newContext(50);

        DirectFileDescriptor fileDescriptor = DirectFileDescriptor.from(temporaryFolder.newFile("test.bin"), context);

        // ByteBuffer buffer = ByteBuffer.allocateDirect(512);
        ByteBuffer buffer = DirectFileDescriptor.newAlignedBuffer(512, 512);

        for (int i = 0; i < 512; i++) {
            buffer.put((byte) 'a');
        }

        buffer.rewind();

        System.out.println(fileDescriptor.write(0, 512, buffer, callback));

        int retValue = DirectFileDescriptor.poll(context, callbacks, 1, 50);
        System.out.println("Return from poll::" + retValue);
        Assert.assertEquals(1, retValue);

        Assert.assertSame(callback, callbacks[0]);

        DirectFileDescriptor.freeBuffer(buffer);

        buffer = DirectFileDescriptor.newAlignedBuffer(512, 512);

        for (int i = 0; i < 512; i++) {
            buffer.put((byte) 'B');
        }

        System.out.println(fileDescriptor.write(0, 512, buffer, null));

        Assert.assertEquals(1, DirectFileDescriptor.poll(context, callbacks, 1, 50));

        DirectFileDescriptor.freeBuffer(buffer);

        // ByteBuffer buffer = ByteBuffer.allocateDirect(512);
        buffer = DirectFileDescriptor.newAlignedBuffer(512, 512);

        buffer.rewind();

        System.out.println(fileDescriptor.read(0, 512, buffer, null));

        Assert.assertEquals(1, DirectFileDescriptor.poll(context, callbacks, 1, 50));

        for (int i = 0 ; i < 512; i++) {
            Assert.assertEquals('B', buffer.get());
        }

        DirectFileDescriptor.freeBuffer(buffer);

        fileDescriptor.close();

        DirectFileDescriptor.deleteContext(context);
    }

    @Test
    public void testSubmitRead() throws Exception {

        Object callback = new Object();

        Object[] callbacks = new Object[50];

        ByteBuffer context = DirectFileDescriptor.newContext(50);

        File file = temporaryFolder.newFile("test.bin");

        FileOutputStream fileOutputStream = new FileOutputStream(file);
        byte[] bufferWrite = new byte[512];
        for (int i = 0; i < 512; i++) {
            bufferWrite[i] = (byte) 'A';
        }
        fileOutputStream.write(bufferWrite);

        fileOutputStream.close();

        DirectFileDescriptor fileDescriptor = DirectFileDescriptor.from(file, context);

        // ByteBuffer buffer = ByteBuffer.allocateDirect(512);
        ByteBuffer buffer = DirectFileDescriptor.newAlignedBuffer(512, 512);

        System.out.println(fileDescriptor.read(0, 512, buffer, callback));

        Assert.assertEquals(1, DirectFileDescriptor.poll(context, callbacks, 1, 50));

        Assert.assertSame(callback, callbacks[0]);

        for (int i = 0 ; i < 512; i++) {
            Assert.assertEquals('A', buffer.get());
        }

        DirectFileDescriptor.deleteContext(context);
    }

    @Test
    public void testLeaks() {
        // todo a test to validate leak scenarios on callbacks and byte-buffers

    }

}
