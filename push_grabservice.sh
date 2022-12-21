adb push ./grabservice/build/intermediates/merged_native_libs/debug/out/lib/arm64-v8a/libgrabservice.so /data/local/tmp/;
dx --dex --output=GrabService.jar ./grabservice/build/intermediates/aar_main_jar/debug/classes.jar
adb push GrabService.jar /data/local/tmp/
adb push libdl_android.so /data/local/tmp/
adb push startGrabService.sh /data/local/tmp/
adb shell "sh /data/local/tmp/startGrabService.sh"
