APP_PLATFORM := android-25
APP_ABI :=  armeabi-v7a #x86 mips armeabi
#NDK_TOOLCHAIN_VERSION:= clang
#APP_OPTIM := debug
APP_OPTIM := release
#APP_STL:=stlport_static
APP_STL := c++_shared
#gnustl_static
APP_CPPFLAGS := -std=c++11 -frtti -fexceptions
