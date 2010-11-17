all:
	ant debug
install:
	adb install -r bin/Gyaco-debug.apk
uninstall:
	adb uninstall com.pitecan.gyaco
debug:
	adb logcat | grep Gyaco
clean:
	/bin/rm -r -f bin/classes
update:
	git update
push:
	git push

