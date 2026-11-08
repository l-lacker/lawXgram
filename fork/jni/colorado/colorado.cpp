#include <jni.h>
#include <dirent.h>
#include <unistd.h>
#include <zlib.h>

#include <cstdlib>
#include <string>
#include <string_view>

#include "colorado.h"
#include "logging.h"
#include "obfs-string.h"
#include "utils.h"

// Scans the files open in this process for the app's own APKs (base and splits), wherever the
// system installed them (/data/app, adoptable storage /mnt/expand/<uuid>/app), reads the first
// certificate of the APK Signature Scheme v2 block straight from the file and compares it with the
// certificate of the release keystore. Reading the file (not PackageManager) is what makes a
// repackaged APK hard to pass off as the original.
int colorado_verify_signature() {
    DIR *dir = opendir("/proc/self/fd"_iobfs.c_str());
    if (dir == nullptr) {
        return -1;
    }
    int dir_fd = dirfd(dir);
    struct dirent *ent;
    char buf[PATH_MAX];
    int result = -1;
    while ((ent = readdir(dir)) != nullptr) {
        if (ent->d_name[0] == '.') continue;

        ssize_t len = readlinkat(dir_fd, ent->d_name, buf, PATH_MAX);
        if (len <= 0) {
            continue;
        }

        std::string_view real_path(buf, len);
        // installed APKs live under an "app" directory; files the app keeps in its own data
        // directory (a downloaded update, for example) are not the running code
        if (!ends_with(real_path, ".apk"_iobfs.c_str()) ||
            !contains(real_path, "/app/"_iobfs.c_str()) ||
            !contains(real_path, NEO_SIGNATURE_PACKAGE)) {
            continue;
        }

        std::string cert = read_certificate(atoi(ent->d_name));
        if (cert.empty()) {
            continue; // not a readable v2-signed APK: inconclusive for this file
        }
        uLong crc = crc32(0, (unsigned const char *) cert.data(), cert.length());
        if (cert.size() == (size_t) NEO_SIGNATURE_SIZE && crc == (uLong) NEO_SIGNATURE_CRC) {
            result = 1;
        } else {
            LOGE("colorado: certificate mismatch");
            result = 0;
            break;
        }
    }
    closedir(dir);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_ru_llacker_neogram_security_NeoSignature_nativeVerify(JNIEnv *env, jclass clazz) {
    return colorado_verify_signature();
}
