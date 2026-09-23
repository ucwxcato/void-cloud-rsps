@echo off
cd /d "%~dp0"
java -Dsun.java2d.uiScale=1.0 -Dsun.java2d.dpiaware=false -jar void-client-1.2.0.jar -ip 2.28.141.196 -p 43594
pause
