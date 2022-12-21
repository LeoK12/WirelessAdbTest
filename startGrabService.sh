version=`getprop ro.build.version.sdk`
echo "Android version:"$version
if [ $version -ge 30 ]; then
	export LD_PRELOAD=libdl_android.so
fi
app_process -Djava.class.path=/data/local/tmp/GrabService.jar /system/bin com.leok12.grabservice.GrabService -D
echo "SUCCESS"
