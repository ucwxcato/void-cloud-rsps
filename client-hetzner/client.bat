@echo off
cd /d "%~dp0"
java -Dvoid.server=2.28.141.196 -Dsun.java2d.uiScale=1.0 -Dsun.java2d.dpiaware=false -jar void-client-1.2.0.jar
pause
