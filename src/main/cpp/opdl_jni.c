#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <poll.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#define LOG_TAG "OPDL Transfer/JNI"
#define OPDL_DEVICE_PATH "/dev/opdl0"
#define IO_BUFFER_SIZE (64 * 1024)
#define READ_POLL_TIMEOUT_MS 20

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static bool open_opdl_device(int flags, int *out_fd) {
  int fd = open(OPDL_DEVICE_PATH, flags | O_CLOEXEC);
  if (fd < 0) {
    return false;
  }
  *out_fd = fd;
  return true;
}

JNIEXPORT jboolean JNICALL
Java_org_opdl_transfer_Helpers_OpdlKernelBridge_nativeHasKernelDevice(
    JNIEnv *env, jobject thiz) {
  (void)env;
  (void)thiz;

  int fd = -1;
  if (!open_opdl_device(O_RDWR, &fd)) {
    return JNI_FALSE;
  }
  close(fd);
  return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_org_opdl_transfer_Helpers_OpdlKernelBridge_nativeSendPayloadFile(
    JNIEnv *env, jobject thiz, jstring device_id, jstring payload_path,
    jlong payload_size) {
  (void)thiz;
  (void)device_id;

  if (payload_path == NULL || payload_size < 0) {
    return JNI_FALSE;
  }

  const char *path = (*env)->GetStringUTFChars(env, payload_path, NULL);
  if (path == NULL) {
    return JNI_FALSE;
  }

  int src_fd = open(path, O_RDONLY | O_CLOEXEC);
  if (src_fd < 0) {
    LOGW("Failed to open payload file '%s': %s", path, strerror(errno));
    (*env)->ReleaseStringUTFChars(env, payload_path, path);
    return JNI_FALSE;
  }

  int opdl_fd = -1;
  if (!open_opdl_device(O_WRONLY, &opdl_fd)) {
    LOGW("Failed to open %s for write: %s", OPDL_DEVICE_PATH, strerror(errno));
    close(src_fd);
    (*env)->ReleaseStringUTFChars(env, payload_path, path);
    return JNI_FALSE;
  }

  char buffer[IO_BUFFER_SIZE];
  jlong total_written = 0;
  bool ok = true;

  while (total_written < payload_size) {
    size_t to_read = (size_t)((payload_size - total_written) > IO_BUFFER_SIZE
                                  ? IO_BUFFER_SIZE
                                  : (payload_size - total_written));
    ssize_t read_count = read(src_fd, buffer, to_read);
    if (read_count < 0) {
      LOGW("Read failed from payload file: %s", strerror(errno));
      ok = false;
      break;
    }
    if (read_count == 0) {
      break;
    }

    ssize_t offset = 0;
    while (offset < read_count) {
      ssize_t written =
          write(opdl_fd, buffer + offset, (size_t)(read_count - offset));
      if (written < 0) {
        LOGW("Write failed to %s: %s", OPDL_DEVICE_PATH, strerror(errno));
        ok = false;
        break;
      }
      offset += written;
      total_written += written;
    }

    if (!ok) {
      break;
    }
  }

  if (ok && total_written != payload_size) {
    LOGW("Incomplete OPDL write: wrote=%lld expected=%lld",
         (long long)total_written, (long long)payload_size);
    ok = false;
  }

  close(opdl_fd);
  close(src_fd);
  (*env)->ReleaseStringUTFChars(env, payload_path, path);

  return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_org_opdl_transfer_Helpers_OpdlKernelBridge_nativeSendPayloadFD(
    JNIEnv *env, jobject thiz, jstring device_id, jint fd, jlong payload_size) {
  (void)env;
  (void)thiz;
  (void)device_id;

  if (fd < 0 || payload_size < 0) {
    return JNI_FALSE;
  }

  int opdl_fd = -1;
  if (!open_opdl_device(O_WRONLY, &opdl_fd)) {
    LOGW("Failed to open %s for write: %s", OPDL_DEVICE_PATH, strerror(errno));
    return JNI_FALSE;
  }

  char buffer[IO_BUFFER_SIZE];
  jlong total_written = 0;
  bool ok = true;

  while (total_written < payload_size) {
    size_t to_read = (size_t)((payload_size - total_written) > IO_BUFFER_SIZE
                                  ? IO_BUFFER_SIZE
                                  : (payload_size - total_written));
    ssize_t read_count = read(fd, buffer, to_read);
    if (read_count < 0) {
      LOGW("Read failed from source FD %d: %s", fd, strerror(errno));
      ok = false;
      break;
    }
    if (read_count == 0) {
      break;
    }

    ssize_t offset = 0;
    while (offset < read_count) {
      ssize_t written =
          write(opdl_fd, buffer + offset, (size_t)(read_count - offset));
      if (written < 0) {
        LOGW("Write failed to %s: %s", OPDL_DEVICE_PATH, strerror(errno));
        ok = false;
        break;
      }
      offset += written;
      total_written += written;
    }

    if (!ok) {
      break;
    }
  }

  if (ok && total_written != payload_size) {
    LOGW("Incomplete OPDL write via FD: wrote=%lld expected=%lld",
         (long long)total_written, (long long)payload_size);
    ok = false;
  }

  close(opdl_fd);
  return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_org_opdl_transfer_Helpers_OpdlKernelBridge_nativeReceivePayloadFile(
    JNIEnv *env, jobject thiz, jstring device_id, jstring destination_path,
    jlong expected_size) {
  (void)thiz;
  (void)device_id;

  if (destination_path == NULL || expected_size <= 0) {
    return (jlong)-1;
  }

  const char *path = (*env)->GetStringUTFChars(env, destination_path, NULL);
  if (path == NULL) {
    return (jlong)-1;
  }

  int dst_fd = open(path, O_CREAT | O_TRUNC | O_WRONLY | O_CLOEXEC, 0600);
  if (dst_fd < 0) {
    LOGW("Failed to open destination file '%s': %s", path, strerror(errno));
    (*env)->ReleaseStringUTFChars(env, destination_path, path);
    return (jlong)-1;
  }

  int opdl_fd = -1;
  if (!open_opdl_device(O_RDONLY, &opdl_fd)) {
    LOGW("Failed to open %s for read: %s", OPDL_DEVICE_PATH, strerror(errno));
    close(dst_fd);
    (*env)->ReleaseStringUTFChars(env, destination_path, path);
    return (jlong)-1;
  }

  struct pollfd pfd;
  pfd.fd = opdl_fd;
  pfd.events = POLLIN;
  pfd.revents = 0;

  int poll_ret = poll(&pfd, 1, READ_POLL_TIMEOUT_MS);
  if (poll_ret <= 0) {
    close(opdl_fd);
    close(dst_fd);
    unlink(path);
    (*env)->ReleaseStringUTFChars(env, destination_path, path);
    return (jlong)-1;
  }

  char buffer[IO_BUFFER_SIZE];
  jlong total_read = 0;

  while (total_read < expected_size) {
    size_t to_read = (size_t)((expected_size - total_read) > IO_BUFFER_SIZE
                                  ? IO_BUFFER_SIZE
                                  : (expected_size - total_read));
    ssize_t read_count = read(opdl_fd, buffer, to_read);
    if (read_count < 0) {
      LOGW("Read failed from %s: %s", OPDL_DEVICE_PATH, strerror(errno));
      total_read = -1;
      break;
    }
    if (read_count == 0) {
      break;
    }

    ssize_t offset = 0;
    while (offset < read_count) {
      ssize_t written =
          write(dst_fd, buffer + offset, (size_t)(read_count - offset));
      if (written < 0) {
        LOGW("Write failed to destination file: %s", strerror(errno));
        total_read = -1;
        break;
      }
      offset += written;
      total_read += written;
    }

    if (total_read < 0) {
      break;
    }
  }

  if (fsync(dst_fd) < 0) {
    LOGI("fsync failed for destination payload file: %s", strerror(errno));
  }

  close(opdl_fd);
  close(dst_fd);
  (*env)->ReleaseStringUTFChars(env, destination_path, path);

  if (total_read <= 0) {
    unlink(path);
    return (jlong)-1;
  }

  return total_read;
}

JNIEXPORT jlong JNICALL
Java_org_opdl_transfer_Helpers_OpdlKernelBridge_nativeReceivePayloadFD(
    JNIEnv *env, jobject thiz, jstring device_id, jint fd,
    jlong expected_size) {
  (void)env;
  (void)thiz;
  (void)device_id;

  if (fd < 0 || expected_size <= 0) {
    return (jlong)-1;
  }

  int opdl_fd = -1;
  if (!open_opdl_device(O_RDONLY, &opdl_fd)) {
    LOGW("Failed to open %s for read: %s", OPDL_DEVICE_PATH, strerror(errno));
    return (jlong)-1;
  }

  struct pollfd pfd;
  pfd.fd = opdl_fd;
  pfd.events = POLLIN;
  pfd.revents = 0;

  int poll_ret = poll(&pfd, 1, READ_POLL_TIMEOUT_MS);
  if (poll_ret <= 0) {
    close(opdl_fd);
    return (jlong)-1;
  }

  char buffer[IO_BUFFER_SIZE];
  jlong total_read = 0;

  while (total_read < expected_size) {
    size_t to_read = (size_t)((expected_size - total_read) > IO_BUFFER_SIZE
                                  ? IO_BUFFER_SIZE
                                  : (expected_size - total_read));
    ssize_t read_count = read(opdl_fd, buffer, to_read);
    if (read_count < 0) {
      LOGW("Read failed from %s: %s", OPDL_DEVICE_PATH, strerror(errno));
      total_read = -1;
      break;
    }
    if (read_count == 0) {
      break;
    }

    ssize_t offset = 0;
    while (offset < read_count) {
      ssize_t written =
          write(fd, buffer + offset, (size_t)(read_count - offset));
      if (written < 0) {
        LOGW("Write failed to destination FD %d: %s", fd, strerror(errno));
        total_read = -1;
        break;
      }
      offset += written;
      total_read += written;
    }

    if (total_read < 0) {
      break;
    }
  }

  close(opdl_fd);
  return total_read;
}

JNIEXPORT jint JNICALL
Java_org_opdl_transfer_Helpers_OpdlKernelBridge_nativeRead(
    JNIEnv *env, jobject thiz, jbyteArray buffer, jint offset, jint len) {
  (void)thiz;

  int opdl_fd = -1;
  if (!open_opdl_device(O_RDONLY, &opdl_fd)) {
    return -1;
  }

  jbyte *buf = (*env)->GetByteArrayElements(env, buffer, NULL);
  if (buf == NULL) {
    close(opdl_fd);
    return -1;
  }

  ssize_t read_count = read(opdl_fd, buf + offset, (size_t)len);

  (*env)->ReleaseByteArrayElements(env, buffer, buf, 0);
  close(opdl_fd);

  return (jint)read_count;
}
