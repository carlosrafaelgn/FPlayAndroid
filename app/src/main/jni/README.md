16 KB Page Size
===============

Considering that, currently, 16 KB pages are only supported on arm64 targets with 16 KB kernels, only the `.so` files listed below have been recompiled using r28c NDK. All other `.so` files have remained in their original versions (compiled with the r10e NDK).

- app/src/main/jniLibs/arm64-v8a/libSimpleVisualizerJni.so
- app/src/main/jniLibs/x86_64/libSimpleVisualizerJni.so
- app/src/x/jniLibs/arm64-v8a/libMediaContextJni.so
- app/src/x/jniLibs/x86_64/libMediaContextJni.so

References
----------

- https://source.android.com/docs/core/architecture/16kb-page-size/16kb
- https://android-developers.googleblog.com/2025/07/transition-to-16-kb-page-sizes-android-apps-games-android-studio.html
- https://stackoverflow.com/a/56467008/3569421
