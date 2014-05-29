@echo off

copy AndroidOthers.mk Android.mk
copy ApplicationAllClean.mk Application.mk
call ndk-build clean

del Android.mk
del Application.mk
copy AndroidV7.mk Android.mk
copy ApplicationV7.mk Application.mk
call ndk-build

move ..\libs\armeabi-v7a\libSimpleVisualizerJni.so tmp.so

del Application.mk
del Android.mk
copy ApplicationOthers.mk Application.mk
copy AndroidOthers.mk Android.mk
call ndk-build

move tmp.so ..\libs\armeabi-v7a\libSimpleVisualizerJni.so

del Application.mk
del Android.mk

pause
