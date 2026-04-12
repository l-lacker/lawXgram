# org.telegram.messenger

- This package holds app lifecycle, controllers, storage, notifications, and core service logic.
- Prefer existing controller and account-scoped patterns over new global singletons.
- Watch threading, queues, persistence, and multi-account behavior closely.
- Avoid logging private data; use the existing `BuildVars` and `FileLog` gates.
- Changes here often ripple into `org.telegram.tgnet`, UI code, resources, and JNI.
