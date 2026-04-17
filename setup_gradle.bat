@echo off
echo 正在设置Gradle Wrapper...

REM 创建必要的目录
if not exist "gradle\wrapper" mkdir "gradle\wrapper"

REM 下载gradle-wrapper.jar
echo 下载gradle-wrapper.jar...
powershell -Command "Invoke-WebRequest -Uri 'https://github.com/gradle/gradle/raw/v7.5.0/gradle/wrapper/gradle-wrapper.jar' -OutFile 'gradle\wrapper\gradle-wrapper.jar'"

if exist "gradle\wrapper\gradle-wrapper.jar" (
    echo Gradle Wrapper设置完成！
    echo 现在可以运行: gradlew build
) else (
    echo 下载失败，请手动下载gradle-wrapper.jar
    echo 下载地址: https://github.com/gradle/gradle/raw/v7.5.0/gradle/wrapper/gradle-wrapper.jar
    echo 保存到: gradle\wrapper\gradle-wrapper.jar
)

pause