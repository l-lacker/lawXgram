#include <stdbool.h>

#ifdef NDEBUG
#define LOG_DISABLED
#endif
#define PACKAGE_NAME "ru.llacker.lawxgram"_iobfs.c_str()
#define CERT_HASH 0xeb04a51a
#define CERT_SIZE 0x368

#ifdef __cplusplus
extern "C" {
#endif

bool check_signature();

#ifdef __cplusplus
}
#endif
