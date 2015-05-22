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
package io.netty.channel.libaio.test;

import io.netty.channel.libaio.DirectFileDescriptor;
import io.netty.channel.libaio.DirectFileDescriptorController;
import io.netty.channel.libaio.ErrorInfo;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This test is using a different package from {@link io.netty.channel.libaio.DirectFileDescriptor}
 * as I need to validate public methods on the API
 */
public class DirectFileDescriptorTest {

    @Rule
    public TemporaryFolder temporaryFolder;

    public DirectFileDescriptorController control;

    @Before
    public void setUpFactory() {
        control = new DirectFileDescriptorController(50);
    }

    @After
    public void deleteFactory() {
        control.close();
    }

    public DirectFileDescriptorTest() {
        /**
         *  I didn't use /tmp for three reasons
         *  - Most systems now will use tmpfs which is not compatible with O_DIRECT
         *  - This would fill up /tmp in case of failures.
         *  - target is cleaned up every time you do a mvn clean, so it's safer
         */
        File parent = new File("./target");
        parent.mkdirs();
        temporaryFolder = new TemporaryFolder(parent);
    }

    @Test
    public void testOpen() throws Exception {
        DirectFileDescriptor fileDescriptor = control.newFile(temporaryFolder.newFile("test.bin"));
        fileDescriptor.close();
    }

    @Test
    public void testSubmitWriteAndRead() throws Exception {

        Object callback = new Object();

        Object[] callbacks = new Object[50];

        DirectFileDescriptor fileDescriptor = control.newFile(temporaryFolder.newFile("test.bin"));

        // ByteBuffer buffer = ByteBuffer.allocateDirect(512);
        ByteBuffer buffer = control.newAlignedBuffer(512, 512);

        for (int i = 0; i < 512; i++) {
            buffer.put((byte) 'a');
        }

        buffer.rewind();

        fileDescriptor.write(0, 512, buffer, callback);

        int retValue = control.poll(callbacks, 1, 50);
        System.out.println("Return from poll::" + retValue);
        Assert.assertEquals(1, retValue);

        Assert.assertSame(callback, callbacks[0]);

        DirectFileDescriptorController.freeBuffer(buffer);

        buffer = DirectFileDescriptorController.newAlignedBuffer(512, 512);

        for (int i = 0; i < 512; i++) {
            buffer.put((byte) 'B');
        }

        fileDescriptor.write(0, 512, buffer, null);

        Assert.assertEquals(1, control.poll(callbacks, 1, 50));

        DirectFileDescriptorController.freeBuffer(buffer);

        // ByteBuffer buffer = ByteBuffer.allocateDirect(512);
        buffer = DirectFileDescriptorController.newAlignedBuffer(512, 512);

        buffer.rewind();

        fileDescriptor.read(0, 512, buffer, null);

        Assert.assertEquals(1, control.poll(callbacks, 1, 50));

        for (int i = 0; i < 512; i++) {
            Assert.assertEquals('B', buffer.get());
        }

        DirectFileDescriptorController.freeBuffer(buffer);

        fileDescriptor.close();
    }

    @Test
    public void testSubmitRead() throws Exception {

        Object callback = new Object();

        Object[] callbacks = new Object[50];

        File file = temporaryFolder.newFile("test.bin");

        fillupFile(file, 100);

        DirectFileDescriptor fileDescriptor = control.newFile(file);

        // ByteBuffer buffer = ByteBuffer.allocateDirect(512);
        ByteBuffer buffer = DirectFileDescriptorController.newAlignedBuffer(512, 512);

        fileDescriptor.read(0, 512, buffer, callback);

        Assert.assertEquals(1, control.poll(callbacks, 1, 50));

        Assert.assertSame(callback, callbacks[0]);

        fileDescriptor.read(3, 512, buffer, callback);

        for (int i = 0; i < 512; i++) {
            Assert.assertEquals('A', buffer.get());
        }
    }

    @Test
    public void testInvalidWrite() throws Exception {

        CountClass callback = new CountClass();

        Object[] callbacks = new Object[50];

        File file = temporaryFolder.newFile("test.bin");

        fillupFile(file, 10);

        DirectFileDescriptor fileDescriptor = control.newFile(file);

        ByteBuffer buffer = ByteBuffer.allocateDirect(300);
        for (int i = 0; i < 300; i++) {
            buffer.put((byte) 'z');
        }

        fileDescriptor.write(0, 300, buffer, callback);

        Assert.assertEquals(1, control.poll(callbacks, 1, 50));

        Assert.assertTrue(callbacks[0] instanceof ErrorInfo);

        // Error condition
        Assert.assertSame(((ErrorInfo) callbacks[0]).getCallback(), callback);

        System.out.println("Error:" + callbacks[0]);

        buffer = fileDescriptor.newBuffer(512);
        for (int i = 0; i < 512; i++) {
            buffer.put((byte) 'z');
        }

        fileDescriptor.write(0, 512, buffer, callback);

        Assert.assertEquals(1, control.poll(callbacks, 1, 1));

        Assert.assertSame(callback, callbacks[0]);

        fileDescriptor.write(5, 512, buffer, callback);

        Assert.assertEquals(1, control.poll(callbacks, 1, 1));

        Assert.assertTrue(callbacks[0] instanceof ErrorInfo);

        callbacks = null;
        callback = null;

        CountClass.checkLeaks();
    }

    @Test
    public void testLeaks() throws Exception {
        File file = temporaryFolder.newFile("test.bin");

        fillupFile(file, 100);

        Object[] callbacks = new Object[50];

        DirectFileDescriptor fileDescriptor = control.newFile(file);

        ByteBuffer bufferWrite = DirectFileDescriptorController.newAlignedBuffer(512, 512);
        for (int i = 0; i < 512; i++) {
            bufferWrite.put((byte) 'B');
        }

        for (int j = 0; j < 100; j++) {
            for (int i = 0; i < 49; i++) {
                CountClass countClass = new CountClass();
                fileDescriptor.write(i * 512, 512, bufferWrite, countClass);
            }

            Assert.assertEquals(49, control.poll(callbacks, 49, 50));

            for (int i = 0; i < 49; i++) {
                Assert.assertNotNull(callbacks[i]);
                callbacks[i] = null;
            }
        }

        CountClass.checkLeaks();
    }

    @Test
    public void testIOExceptionConditions() throws Exception {
        boolean exceptionThrown = false;

        try {
            // this should throw an exception, we shouldn't be able to open a directory!
            control.newFile(temporaryFolder.getRoot());
        } catch (IOException expected) {
            exceptionThrown = true;
        }

        Assert.assertTrue(exceptionThrown);

        exceptionThrown = false;

        DirectFileDescriptor fileDescriptor = control.newFile(temporaryFolder.newFile());
        fileDescriptor.close();
        try {
            fileDescriptor.close();
        } catch (IOException expected) {
            exceptionThrown = true;
        }

        Assert.assertTrue(exceptionThrown);

        fileDescriptor = control.newFile(temporaryFolder.newFile());

        ByteBuffer buffer = fileDescriptor.newBuffer(512);

        for (int i = 0; i < 512; i++) {
            buffer.put((byte) 'a');
        }

        exceptionThrown = false;
        try {
            for (int i = 0; i < 60; i++) {
                fileDescriptor.write(i * 512, 512, buffer, new CountClass());
            }
        } catch (IOException e) {
            exceptionThrown = true;
        }

        Assert.assertTrue(exceptionThrown);

        Object[] callbacks = new Object[100];
        Assert.assertEquals(50, control.poll(callbacks, 50, 100));

        // it should be possible to write now after queue space being released
        fileDescriptor.write(0, 512, buffer, new CountClass());
        Assert.assertEquals(1, control.poll(callbacks, 1, 100));

        // to help GC and the checkLeaks
        callbacks = null;

        CountClass.checkLeaks();

        exceptionThrown = false;
        try {
            DirectFileDescriptorController.newAlignedBuffer(300, 512);
        } catch (RuntimeException e) {
            exceptionThrown = true;
        }

        Assert.assertTrue(exceptionThrown);

        exceptionThrown = false;
        try {
            DirectFileDescriptorController.newAlignedBuffer(-512, 512);
        } catch (RuntimeException e) {
            exceptionThrown = true;
        }

        Assert.assertTrue(exceptionThrown);

        DirectFileDescriptorController.freeBuffer(buffer);
    }

    private void fillupFile(File file, int blocks) throws IOException {
        FileOutputStream fileOutputStream = new FileOutputStream(file);
        byte[] bufferWrite = new byte[512];
        for (int i = 0; i < 512; i++) {
            bufferWrite[i] = (byte) 'A';
        }

        for (int i = 0; i < blocks; i++) {
            fileOutputStream.write(bufferWrite);
        }

        fileOutputStream.close();
    }

    static class CountClass {
        static AtomicInteger count = new AtomicInteger(0);

        public CountClass() {
            count.incrementAndGet();
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            count.decrementAndGet();
        }

        public static void checkLeaks() throws InterruptedException {
            long timeout = System.currentTimeMillis() + 5000;

            while (timeout > System.currentTimeMillis() && count.get() != 0) {
                WeakReference reference = new WeakReference(new Object());
                while (reference.get() != null) {
                    System.gc();
                    Thread.sleep(100);
                }
            }

            Assert.assertEquals(0, count.get());
        }
    }

}