@echo off
chcp 936 >nul
setlocal enabledelayedexpansion
title 二手房中介管理系统 - 开发环境

REM ============================================================
REM  一键启动开发环境（双击运行）
REM
REM  为什么用 %~dp0 而不是写死路径：
REM    仓库路径含中文，.cmd 文件在中文 Windows 上按 GBK 解析，
REM    写死中文路径容易因编码问题读错。%~dp0 是"本脚本所在目录"，
REM    不经过编码转换，因此最稳。
REM
REM  为什么启动后端要加 -am：
REM    `-pl realestate-web` 只把 web 放进 reactor，它依赖的 realestate-core
REM    就只能从「本地仓库」取 —— 而本地仓库里那个 jar 是上一次 install 时的
REM    样子。core 改了代码却没重新 install，就会报「找不到符号」这类怪事
REM    （本项目已经踩过一次）。
REM    加 -am 会把 core 一并纳入 reactor，直接按源码构建，从此不依赖本地仓库。
REM    （父 POM 与 core 上已把 spring-boot-maven-plugin 设为跳过，否则 -am 时
REM      会因为没有主类而报「Unable to find a suitable main class」。）
REM
REM  想跑桌面版时：先执行一次 `mvnw.cmd -DskipTests install`，
REM    再 `mvnw.cmd -pl realestate-desktop exec:java`；
REM    或直接 `java -jar realestate-desktop\target\realestate-desktop-1.0.0-SNAPSHOT-jar-with-dependencies.jar`
REM    （fat jar 自带全部依赖，不需要 install）。
REM ============================================================

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

echo ============================================================
echo    二手房中介管理系统 · 开发环境启动
echo ============================================================
echo    项目目录：%ROOT%
echo.

REM ---------- 1. Java ----------
if "%JAVA_HOME%"=="" (
    if exist "E:\jdk\bin\java.exe" (
        set "JAVA_HOME=E:\jdk"
        echo [提示] 未检测到 JAVA_HOME，本次临时使用 E:\jdk
        echo        建议永久设置 JAVA_HOME，否则每次都要靠本脚本兜底。
    ) else (
        echo [警告] 未检测到 JAVA_HOME，后端很可能启动失败。
        echo        请将 JAVA_HOME 指向你的 JDK 目录，再重新运行本脚本。
    )
) else (
    echo [OK]   JAVA_HOME = %JAVA_HOME%
)

REM ---------- 2. 前端依赖 ----------
if exist "%ROOT%\frontend\node_modules" (
    echo [OK]   前端依赖已安装
) else (
    echo [提示] 前端依赖尚未安装，改用国内镜像安装...
    pushd "%ROOT%\frontend"
    call npm install --registry=https://registry.npmmirror.com
    popd
    if not exist "%ROOT%\frontend\node_modules" (
        echo [错误] 前端依赖安装失败。
        goto END
    )
)

echo.
echo ---------- 正在打开两个服务窗口 ----------
start "后端：realestate-web（端口 8080）" /D "%ROOT%" cmd /k mvnw.cmd -pl realestate-web -am spring-boot:run
start "前端：Vite（端口 5173）" /D "%ROOT%\frontend" cmd /k npm run dev

echo     已打开 2 个新窗口，请保持它们开着。
echo     关掉那两个窗口 = 关掉服务；本窗口可以随时关闭。
echo.
echo ---------- 等待两个服务就绪，然后自动打开浏览器 ----------

set /a WAIT=0
:LOOP
set /a WAIT+=1
netstat -an | findstr /c:":5173 " >nul 2>&1
if not errorlevel 1 goto FRONT_READY
if !WAIT! geq 60 goto TIMEOUT_FRONT
REM 用 ping 做 2 秒延时：timeout 命令要求真实控制台句柄，
REM 在输出被重定向（脚本被别的程序调用、或日志采集）时会直接报错退出。
ping -n 3 127.0.0.1 >nul 2>&1
goto LOOP

:FRONT_READY
echo [OK]   前端已就绪（约 %WAIT% 次探测）
echo         正在等待后端 —— 首次启动要编译，约十几秒，请稍候...

REM 后端也要等：它要经过 Maven 启动 + 编译 core + 启动 Spring 容器，
REM 比 Vite 慢得多。不等就打开浏览器，用户会撞上「登录没反应」，
REM 而那只是后端还没起来，看起来却像坏了。
set /a WAIT2=0
:LOOP2
set /a WAIT2+=1
netstat -an | findstr /c:":8080 " >nul 2>&1
if not errorlevel 1 goto BOTH_READY
if !WAIT2! geq 60 goto TIMEOUT_BACK
ping -n 3 127.0.0.1 >nul 2>&1
goto LOOP2

:BOTH_READY
echo [OK]   后端已就绪（端口 8080，约 %WAIT2% 次探测），正在打开浏览器...
start "" "http://localhost:5173"
goto END

:TIMEOUT_FRONT
echo [警告] 等待 120 秒前端仍未监听 5173，请切到"前端"窗口查看报错。
echo        若是提示找不到 npm，说明执行策略问题，改用 npm.cmd 重试即可。
goto END

:TIMEOUT_BACK
echo [警告] 等待 120 秒后端仍未监听 8080，请切到"后端"窗口查看报错。
echo        若报「找不到符号」，说明 core 没被一并构建 ——
echo        可在该项目录下手动执行：mvnw.cmd -DskipTests install 后再试。

:END
echo.
echo ============================================================
echo    本窗口可以关闭，不影响已经启动的服务。
echo    浏览器地址：http://localhost:5173
echo ============================================================
pause
