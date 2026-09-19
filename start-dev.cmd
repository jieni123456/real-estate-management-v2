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
REM  为什么用 mvnw.cmd / npm（而不是 ./mvnw）：
REM    cmd 里没有 sh 脚本，走的是 .cmd / .cmd 分支。
REM    在 PowerShell 里则是另一套（npm.ps1 受执行策略约束）。
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

REM ---------- 2. core 模块是否已安装到本地仓库 ----------
if not exist "%USERPROFILE%\.m2\repository\com\realestate\realestate-core" (
    echo [提示] 首次运行：正在构建并安装各模块到本地仓库，约 1 分钟...
    call mvnw.cmd -q -DskipTests install
    if errorlevel 1 (
        echo [错误] 构建失败，请查看上面的输出。
        goto END
    )
)
echo [OK]   各模块已就绪

REM ---------- 3. 前端依赖 ----------
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
start "后端：realestate-web（端口 8080）" /D "%ROOT%" cmd /k mvnw.cmd -pl realestate-web spring-boot:run
start "前端：Vite（端口 5173）" /D "%ROOT%\frontend" cmd /k npm run dev

echo     已打开 2 个新窗口，请保持它们开着。
echo     关掉那两个窗口 = 关掉服务；本窗口可以随时关闭。
echo.
echo ---------- 等待前端就绪，然后自动打开浏览器 ----------

set /a WAIT=0
:LOOP
set /a WAIT+=1
netstat -an | findstr /c:":5173 " >nul 2>&1
if not errorlevel 1 goto READY
if !WAIT! geq 30 goto TIMEOUT
REM 用 ping 做 2 秒延时：timeout 命令要求真实控制台句柄，
REM 在输出被重定向（脚本被别的程序调用、或日志采集）时会直接报错退出。
ping -n 3 127.0.0.1 >nul 2>&1
goto LOOP

:READY
echo [OK]   前端已就绪（约 %WAIT% 次探测），正在打开浏览器...
start "" "http://localhost:5173"
goto END

:TIMEOUT
echo [警告] 等待 60 秒仍未就绪，请切到"前端"窗口查看报错。
echo        若是提示找不到 npm，说明执行策略问题，改用 npm.cmd 重试即可。

:END
echo.
echo ============================================================
echo    本窗口可以关闭，不影响已经启动的服务。
echo    浏览器地址：http://localhost:5173
echo ============================================================
pause
