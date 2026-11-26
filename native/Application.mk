APP_ABI := arm64-v8a
APP_PLATFORM := android-21
APP_OPTIM := release
APP_STL := c++_shared
APP_CPPFLAGS := -std=c++14 -frtti -fexceptions
# Android 15+ devices may use 16 KB memory pages
APP_LDFLAGS := -Wl,-z,max-page-size=16384
