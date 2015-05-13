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


#ifndef _GNU_SOURCE
// libaio, O_DIRECT and other things won't be available without this define
#define _GNU_SOURCE
#endif

#include <jni.h>
#include <unistd.h>
#include <errno.h>
#include <libaio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <stdlib.h>
#include "io_netty_channel_libaio_DirectFileDescriptor.h"


JNIEXPORT int JNICALL Java_io_netty_channel_libaio_DirectFileDescriptor_close(JNIEnv* env, jclass clazz, jint fd) {
   if (close(fd) < 0) {
       return -errno;
   }
   return 0;
}

JNIEXPORT int JNICALL Java_io_netty_channel_libaio_DirectFileDescriptor_open(JNIEnv* env, jclass clazz, jstring path) {

    const char* f_path = (*env)->GetStringUTFChars(env, path, 0);

    int res = open(f_path, O_WRONLY | O_CREAT | O_DIRECT, 0666);

    (*env)->ReleaseStringUTFChars(env, path, f_path);

    if (res < 0) {
        return -errno;
    }
    return res;
}


JNIEXPORT jobject JNICALL Java_io_netty_channel_libaio_DirectFileDescriptor_initContext(JNIEnv * env, jclass clazz, jint queueSize) {

    io_context_t * libaioContext = (io_context_t *) malloc (sizeof(io_context_t));

    if (io_queue_init(queueSize, &libaioQueue))
    {
        free(libaioContext);
        return NULL;
    }

    jobject bufferContext = (*env)->NewDirectByteBuffer(libaioContext, queueSize);
    return bufferContext;
}
