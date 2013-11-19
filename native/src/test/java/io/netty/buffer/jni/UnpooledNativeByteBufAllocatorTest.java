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


import io.netty.buffer.AbstractByteBufTest;
import io.netty.buffer.ByteBuf;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteOrder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;


/**
 * To debug this test add this to the VM settings on your IDE:
 *
 * -Djava.library.path=<YOUR-PROJECT-HOME>/place-to-your-JNI
 */
public class UnpooledNativeByteBufAllocatorTest extends AbstractByteBufTest {

    private ByteBuf buffer;

    @Override
    protected ByteBuf newBuffer(int length) {
        buffer = UnpooledNativeByteBufAllocator.DEFAULT.directBuffer(length);
        Assert.assertTrue(buffer.isDirect());
        assertSame(ByteOrder.BIG_ENDIAN, buffer.order());
        assertEquals(0, buffer.writerIndex());
        return buffer;
    }

    @Override
    protected ByteBuf[] components() {
        return new ByteBuf[] { buffer };
    }

   // It's important to validate for exceptions, as these things could crash the VM
   @Test
   public void testExceptions() {
      try {
         Native.allocateDirectBuffer(0);
         Assert.fail("Exception was expected");
      } catch (RuntimeException e) {
         // expected
      }

      try {
         Native.allocateDirectBuffer(-10);
         Assert.fail("Exception was expected");
      } catch (RuntimeException e) {
         // expected
      }
   }
}
