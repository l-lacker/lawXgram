// Neogram: APK signature check (patch misc/signature-check). Compiled into libtmessages only for
// release builds with a release keystore; fork/gradle/neo-library.gradle passes the expected
// certificate (CRC-32 and size of its DER encoding, taken from the keystore at build time) and the
// application id as compile definitions.
#ifndef NEO_COLORADO_H
#define NEO_COLORADO_H

#ifndef NEO_SIGNATURE_CRC
#error "NEO_SIGNATURE_CRC is not defined"
#endif
#ifndef NEO_SIGNATURE_SIZE
#error "NEO_SIGNATURE_SIZE is not defined"
#endif
#ifndef NEO_SIGNATURE_PACKAGE
#error "NEO_SIGNATURE_PACKAGE is not defined"
#endif

#ifdef NDEBUG
#define LOG_DISABLED
#endif

// 1: every APK of this app open in the process carries the expected certificate;
// 0: one of them carries another certificate (the app was re-signed);
// -1: no APK could be read, nothing is known.
int colorado_verify_signature();

#endif // NEO_COLORADO_H
