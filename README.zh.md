# Savanna Simulation v3.2-2d-final

[English](README.md)

**正式 2D 版**

关闭口渴时，历史验收覆盖 5,000 步。已记录的 20,000 步测试中，猎豹在第 10,900 步灭绝。3D 浏览器界面随包提供预览。

项目从 David J. Barnes 和 Michael Kölling 的 BlueJ 狐狸与兔子示例扩展而来，模拟五种动物的捕食、放牧、疾病与天气。

## 使用 Java 17 或以上版本

```sh
java -jar savanna-simulation-v3.2-2d-final.jar
```

## 3D 浏览器预览

```sh
java -Xmx2g -cp savanna-simulation-v3.2-2d-final.jar savanna.SimServer --no-open
```

打开 http://127.0.0.1:8787/。

## 从源码启动

```sh
BUILD_ID=local-dev ./build.sh
./run.sh
```

运行 ./check.sh 执行工程测试。带 3D 的版本需要 Node.js 运行前端模块测试。
