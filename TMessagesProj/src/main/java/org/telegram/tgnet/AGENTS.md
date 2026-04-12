# org.telegram.tgnet

- This is the TL and MTProto serialization layer. Treat it as compatibility-sensitive code.
- Keep changes minimal and flag-safe, especially in `TLRPC.java`.
- Preserve constructor IDs, bit flags, serialization order, and read or write symmetry.
- Coordinate Java protocol changes with `TMessagesProj/jni/tgnet` and JNI wrappers when native code is involved.
- Avoid cleanup or refactor passes here unless the task is explicitly about schema or protocol maintenance.
