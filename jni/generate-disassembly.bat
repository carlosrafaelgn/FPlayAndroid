cd D:\Android\workspace\FPlay\obj\local\armeabi\objs\SimpleVisualizerJni
arm-linux-androideabi-objdump -S -d SimpleVisualizerJni.o > armeabi.txt
move armeabi.txt D:\Android\workspace\FPlay\jni\armeabi.txt

cd D:\Android\workspace\FPlay\obj\local\armeabi-v7a\objs\SimpleVisualizerJni
arm-linux-androideabi-objdump -S -d SimpleVisualizerJni.o > armeabi-v7a.txt
move armeabi-v7a.txt D:\Android\workspace\FPlay\jni\armeabi-v7a.txt

cd D:\Android\workspace\FPlay\obj\local\x86\objs\SimpleVisualizerJni
objdump -S -d SimpleVisualizerJni.o > x86.txt
move x86.txt D:\Android\workspace\FPlay\jni\x86.txt

pause
