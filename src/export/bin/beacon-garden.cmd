@echo off
rem Copyright 2026 Graham Lynch
rem SPDX-License-Identifier: Apache-2.0

setlocal
set "APPLICATION_HOME=%~dp0.."
set "JAVA_COMMAND=java"
if defined JAVA_HOME set "JAVA_COMMAND=%JAVA_HOME%\bin\java.exe"

"%JAVA_COMMAND%" @jscene3d.example.firstThreadArgument@ -classpath "%APPLICATION_HOME%\lib\*" io.github.glynch.jscene3d.project.desktop.DesktopProjectLauncher @project.parent.version@ "%APPLICATION_HOME%\project" "%APPLICATION_HOME%\content"
