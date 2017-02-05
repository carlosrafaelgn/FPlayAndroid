# http://developer.android.com/intl/pt-br/ndk/guides/application_mk.html
# http://developer.android.com/intl/pt-br/ndk/guides/android_mk.html
# http://stackoverflow.com/questions/14516268/ndk-compiling-multiple-libraries
# http://stackoverflow.com/questions/30865110/change-ndk-build-output-locations

APP_ABI          := armeabi-v7a x86 x86_64 #armeabi-v7a arm64-v8a x86 x86_64
APP_OPTIM        := release
APP_STL          := system
APP_PLATFORM     := android-10
TARGET_PLATFORM  := android-10
APP_BUILD_SCRIPT := Android-traditional.mk
