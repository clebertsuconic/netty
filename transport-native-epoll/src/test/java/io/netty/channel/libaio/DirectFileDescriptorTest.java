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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

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
        DirectFileDescriptor fileDescriptor = DirectFileDescriptor.from(temporaryFolder.newFile("test.bin"), 50);
        fileDescriptor.close();
    }

}
