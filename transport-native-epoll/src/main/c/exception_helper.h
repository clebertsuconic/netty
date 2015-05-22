void throwRuntimeException(JNIEnv* env, char* message);

void throwIOException(JNIEnv* env, char* message);

void throwClosedChannelException(JNIEnv* env);

void throwOutOfMemoryError(JNIEnv* env);

char* exceptionMessage(char* msg, int error);
