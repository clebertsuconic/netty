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
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "javautilities.h"
#include "io_netty_buffer_jni_Native.h"


JNIEXPORT void JNICALL Java_io_netty_buffer_jni_Native_freeDirectBuffer(JNIEnv * env, jclass, jobject jbuffer)
{
    if (jbuffer == 0)
    {
		throwRuntimeException(env,"Invalid Buffer");
		return;
    }

	void *  buffer = env->GetDirectBufferAddress(jbuffer);
	free(buffer);
}

JNIEXPORT jobject JNICALL Java_io_netty_buffer_jni_Native_allocateDirectBuffer(JNIEnv *env, jclass clazz, jint size) {

  if (size <= 0)
  {
     throwRuntimeException(env, "Buffer size can't be 0 or less than 0");
     return NULL;
  }

  // TODO: most functions on libaio require 512 bytes alignment ... wouldn't be the case also with some network io functions?
  void *mem = malloc(size);

  if (mem == NULL)
  {
     throwRuntimeException(env, "Error allocating native buffer");
     return NULL;
  }

  // TODO: should we reset the byte-buffer here? Java does
  // It is a good practice to zero the buffers since malloc won't guarantee zeroes.
  // However we may remove this if not needed
  memset(mem, 0, (size_t)size);

  jobject directBuffer = env->NewDirectByteBuffer(mem, size);
  return directBuffer;
}

