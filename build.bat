@echo off
chcp 65001 >nul
echo ========================================
echo     Schedule 课表 - 自动打包脚本
echo ========================================
echo.

:: 检查Gradle
if not exist "gradlew.bat" (
    echo [错误] 未找到 gradlew.bat，请确认在项目根目录
    pause
    exit /b 1
)

:: 创建输出目录
set "OUTPUT_DIR=output"
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

:: 获取版本信息
for /f "tokens=2" %%i in ('findstr "versionName" app\build.gradle 2^>nul') do set "VERSION=%%i"
set "VERSION=%VERSION:"=%
if "%VERSION%"=="" set "VERSION=1.0.0"

set "DATETIME=%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%"
set "DATETIME=%DATETIME: =0%"

echo [信息] 当前版本: %VERSION%
echo [信息] 打包时间: %DATETIME%
echo.

:: 清理
echo [1/3] 清理构建...
call gradlew.bat clean --no-daemon -q
if errorlevel 1 (
    echo [错误] 清理失败
    pause
    exit /b 1
)
echo [完成] 清理完成
echo.

:: 构建Release
echo [2/3] 构建Release版本...
call gradlew.bat assembleRelease --no-daemon
if errorlevel 1 (
    echo [错误] 构建失败
    pause
    exit /b 1
)
echo [完成] 构建完成
echo.

:: 复制APK
echo [3/3] 复制APK文件...
set "SRC_APK=app\build\outputs\apk\release\app-release.apk"
set "DST_APK=%OUTPUT_DIR%\Schedule-v%VERSION%-%DATETIME%.apk"

if not exist "%SRC_APK%" (
    echo [错误] 未找到APK文件: %SRC_APK%
    pause
    exit /b 1
)

copy /y "%SRC_APK%" "%DST_APK%" >nul
if errorlevel 1 (
    echo [错误] 复制APK失败
    pause
    exit /b 1
)

echo [完成] APK已复制到: %DST_APK%
echo.
echo ========================================
echo     打包完成！
echo ========================================
echo.
echo APK位置: %CD%\%DST_APK%
echo.

:: 可选：打开输出目录
explorer.exe "%OUTPUT_DIR%"
pause
