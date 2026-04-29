@echo off
chcp 65001 >nul
echo === 推送到 Gitee ===
cd /d "C:\Users\11692\Desktop\ADB_Audio_Transfer"

echo [1/4] 初始化 Git 仓库...
git init

echo [2/4] 添加文件...
git add .

echo [3/4] 提交...
git commit -m "Initial commit: ADB Audio Transfer App"

echo [4/4] 推送到 Gitee...
git remote add origin https://gitee.com/longking2012/wireless-sound-via-adb.git
git push -u origin master

echo === 完成 ===
pause
