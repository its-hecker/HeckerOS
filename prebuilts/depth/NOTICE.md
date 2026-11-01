HeckerOS depth wallpaper extraction

The local coarse subject-mask pipeline is adapted from Lunaris OS / AxionOS
PortraitSegmenter and WallpaperSubjectExtractorService, retaining the
Apache-2.0 source attribution. Hecker's Java adapter uses the coarse model
without the optional deep-matting and foreground-estimation stages.

Source snapshot: Lunaris-AOSP/packages_apps_Singularity at
0c892ebd04205889b230e986dd4194af4f4d0e24:

- src/org/lunaris/settings/utils/PortraitSegmenter.kt
- src/org/lunaris/settings/utils/WallpaperSubjectExtractorService.kt
- assets/mobile_bg_removal_mosaic_dm1_w_metadata.f16.tflite

The model is fetched byte for byte as app/assets/depth/subject.tflite during build preparation.
Its checksum and those of the packaged runtime are in inputs.lock.json.

The private interpreter runtime is Google's LiteRT 1.4.0 Android release,
using the compatible org.tensorflow.lite Java API and the arm64 JNI library.
The original runtime license notices and ProGuard rules are retained here.

Runtime source artifacts:
https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert/1.4.0/litert-1.4.0.aar
https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert-api/1.4.0/litert-api-1.4.0.aar

Only the separate Hecker Suite app includes the interpreter. SystemUI receives
the prepared layers through its existing protected provider; it never performs
model inference during drawing, wake, unlock, or AOD.

Build preparation

Run `python3 tools/fetch_depth_assets.py` before Soong or standalone source
checks. The Coral local-manifest build helper and source-check workflow do this
automatically. Downloads stay on the build machine; the four binary outputs
are ignored by Git. Both source downloads and extracted outputs must match the
pinned SHA-256 values and source download sizes in inputs.lock.json. Use
`--offline` to require a previously prepared, valid set. The model is bundled
in the built Suite APK; the phone does not download it.
