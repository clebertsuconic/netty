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
#include <pthread.h>
#include "io_netty_channel_libaio_DirectFileDescriptor.h"

struct io_control {
    io_context_t ioContext;
    struct io_event * events;

    // This is used to make sure we don't return IOCB while something else is using them
    // this is to guarantee the submits could be done concurrently with polling
    pthread_mutex_t iocbLock;

    // a resuable pool of iocb
    struct iocb ** iocb;
    int queueSize;
    int iocbPut;
    int iocbGet;
    int used;
};

static inline struct io_control * getIOControl(JNIEnv* env, jobject pointer ) {
    struct io_control * ioControl = (struct io_control *) (*env)->GetDirectBufferAddress(env, pointer);
    return ioControl;
}

/**
 remove an iocb from the pool of IOCBs. Returns null if full
*/
static inline struct iocb * getIOCB(struct io_control * control) {
    pthread_mutex_lock(&(control->iocbLock));

    struct iocb * iocb = 0;


    if (control->used < control->queueSize)
    {
        control->used++;
        iocb = control->iocb[control->iocbGet++];

        if (control->iocbGet >= control->queueSize)
        {
           control->iocbGet = 0;
        }
    }

    pthread_mutex_unlock(&(control->iocbLock));
    return iocb;
}

/**
  Put an iocb back on the pool of IOCBs
*/
static inline void putIOCB(struct io_control * control, struct iocb * iocbBack) {
    pthread_mutex_lock(&(control->iocbLock));

    control->used--;
    control->iocb[control->iocbPut++] = iocbBack;
    if (control->iocbPut > control->queueSize)
    {
       control->iocbPut = 0;
    }
    pthread_mutex_unlock(&(control->iocbLock));
}

static inline void * getBuffer(JNIEnv* env, jobject pointer ) {
    return (*env)->GetDirectBufferAddress(env, pointer);
}



JNIEXPORT jobject JNICALL Java_io_netty_channel_libaio_DirectFileDescriptor_newContext(JNIEnv* env, jclass clazz, jint queueSize) {

    io_context_t libaioContext;
    int i = 0;

    if (io_queue_init(queueSize, &libaioContext)) {
        // Error, so need to release whatever was done before
        free(libaioContext);
        return NULL;
    }

    struct iocb ** iocb = (struct iocb **)malloc(sizeof(struct iocb *) * queueSize);

    for (i = 0; i < queueSize; i++)
    {
       iocb[i] = (struct iocb *)malloc(sizeof(struct iocb));
    }


    struct io_event * events = (struct io_event *)malloc(sizeof(struct io_event) * queueSize);

    struct io_control * theControl = (struct io_control *) malloc(sizeof(struct io_control));

    theControl->ioContext = libaioContext;
    theControl->events = events;
    theControl->iocb = iocb;
    theControl->queueSize = queueSize;
    theControl->iocbPut = 0;
    theControl->iocbGet = 0;
    theControl->used = 0;
    pthread_mutex_init(&(theControl->iocbLock), 0);


    jobject bufferContext = (*env)->NewDirectByteBuffer(env, theControl, sizeof(struct io_control));
    return bufferContext;
}


JNIEXPORT void JNICALL Java_io_netty_channel_libaio_DirectFileDescriptor_deleteContext(JNIEnv* env, jclass clazz, jobject contextPointer) {

    int i;
    struct io_control * ioControl = getIOControl(env, contextPointer);
    io_queue_release(ioControl->ioContext);

    // Releasing each individual iocb
    for (i = 0; i < ioControl->queueSize; i++)
    {
       free(ioControl->iocb[i]);
    }

    free(ioControl->iocb);

    free(ioControl->events);

    free(ioControl);
}


JNIEXPORT int JNICALL Java_io_netty_channel_libaio_DirectFileDescriptor_close(JNIEnv* env, jclass clazz, jint fd) {
   if (close(fd) < 0) {
       return -errno;
   }
   return 0;
}

JNIEXPORT int JNICALL Java_io_netty_channel_libaio_DirectFileDescriptor_open(JNIEnv* env, jclass clazz, jstring path) {

    const char* f_path = (*env)->GetStringUTFChars(env, path, 0);

    //int res = open(f_path, O_WRONLY | O_CREAT | O_DIRECT, 0666);
    int res = open(f_path, O_RDWR | O_CREAT | O_DIRECT, 0666);

    (*env)->ReleaseStringUTFChars(env, path, f_path);

    if (res < 0) {
        return -errno;
    }
    return res;
}

JNIEXPORT jboolean JNICALL Java_io_netty_channel_libaio_DirectFileDescriptor_submitWrite
  (JNIEnv * env, jclass clazz, jint fileHandle, jobject contextPointer, jlong position, jint size, jobject bufferWrite, jobject callback) {

    struct io_control * ioControl = getIOControl(env, contextPointer);

    struct iocb * iocb = getIOCB(ioControl);

    if (iocb == NULL) {
       return JNI_FALSE;
    }

    io_prep_pwrite(iocb, fileHandle, getBuffer(env, bufferWrite), size, position);
    iocb->data = (void *) (*env)->NewGlobalRef(env, callback);

    int result = io_submit(ioControl->ioContext, 1, &iocb);

    if (result < 0) {
        // result could be legally at -EGAIN meaning the queue is full, what to do on that case?
        if (result == -EAGAIN) {
            return JNI_FALSE;
        }
        else {
            fprintf (stderr, "Something wrong %d, %s\n", result, strerror(-result));
            // TODO: throw RuntimeExceptiom
            return JNI_FALSE;
        }
    }
    else {
        return JNI_TRUE;
    }
}

JNIEXPORT jboolean JNICALL Java_io_netty_channel_libaio_DirectFileDescriptor_submitRead
  (JNIEnv * env, jclass clazz, jint fileHandle, jobject contextPointer, jlong position, jint size, jobject bufferRead, jobject callback) {

    struct io_control * ioControl = getIOControl(env, contextPointer);

    struct iocb * iocb = getIOCB(ioControl);


    if (iocb == NULL) {
       return JNI_FALSE;
    }

    io_prep_pread(iocb, fileHandle, getBuffer(env, bufferRead), size, position);
    iocb->data = (void *) (*env)->NewGlobalRef(env, callback);

    int result = io_submit(ioControl->ioContext, 1, &iocb);

    if (result < 0) {
        // result could be legally at -EGAIN meaning the queue is full, what to do on that case?
        if (result == -EAGAIN) {
            return JNI_FALSE;
        }
        else {
            fprintf (stderr, "Something wrong %d, %s\n", result, strerror(-result));
            // TODO: throw RuntimeExceptiom
            return JNI_FALSE;
        }
    }
    else {
        return JNI_TRUE;
    }
}


JNIEXPORT void JNICALL Java_io_netty_channel_libaio_DirectFileDescriptor_freeBuffer
  (JNIEnv * env, jclass clazz, jobject jbuffer) {

  	void *  buffer = (*env)->GetDirectBufferAddress(env, jbuffer);
  	free(buffer);
}

JNIEXPORT jint JNICALL Java_io_netty_channel_libaio_DirectFileDescriptor_poll
  (JNIEnv * env, jclass clazz, jobject contextPointer, jobjectArray callbacks, jint min, jint max) {

    int i = 0;
    struct io_control * ioControl = getIOControl(env, contextPointer);

    jint result = io_getevents(ioControl->ioContext, min, max, ioControl->events, 0);

    for (i = 0; i < result; i++) {
        struct iocb * iocbp = (struct iocb *)ioControl->events[i].obj;

        if (iocbp->data != NULL)
        {
            (*env)->SetObjectArrayElement(env, callbacks, i, (jobject)iocbp->data);
        }

        putIOCB(ioControl, iocbp);
    }
}

JNIEXPORT jobject JNICALL Java_io_netty_channel_libaio_DirectFileDescriptor_newAlignedBuffer
(JNIEnv * env, jclass clazz, jint size, jint alignment) {
    if (size % alignment)
    {
        // throwException(env, NATIVE_ERROR_INVALID_BUFFER, "Buffer size needs to be aligned to 512");
        return 0;
    }

    // This will allocate a buffer, aligned by 512.
    // Buffers created here need to be manually destroyed by destroyBuffer, or this would leak on the process heap away of Java's GC managed memory
    void * buffer = 0;
    if (posix_memalign(&buffer, alignment, size))
    {
        // throwException(env, NATIVE_ERROR_INTERNAL, "Error on posix_memalign");
        return 0;
    }

    // @Norman: to do or not to do?
    // I would prefer leaving it out
    // memset(buffer, 0, (size_t)size);

    jobject jbuffer = (*env)->NewDirectByteBuffer(env, buffer, size);

    return jbuffer;
}
