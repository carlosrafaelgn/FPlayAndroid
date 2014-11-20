cd D:\Android\workspace\FPlay\obj\local\armeabi\objs\SimpleVisualizerJni
arm-linux-androideabi-objdump -S -d SimpleVisualizerJni.o > armeabi.txt
move armeabi.txt D:\Android\workspace\FPlay\jni\armeabi.txt

cd D:\Android\workspace\FPlay\obj\local\armeabi-v7a-hard\objs\SimpleVisualizerJni
arm-linux-androideabi-objdump -S -d SimpleVisualizerJni.o > armeabi-v7a-hard.txt
move armeabi-v7a-hard.txt D:\Android\workspace\FPlay\jni\armeabi-v7a-hard.txt

pause
