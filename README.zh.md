# Savanna Simulation v3.3-perf-preview

[English](README.md)

**开发预览**

沿用 v3.2 生态默认值，包含 S0–S4 提速与 2f 抽样审计。

项目从 David J. Barnes 和 Michael Kölling 的 BlueJ 狐狸与兔子示例扩展而来，模拟五种动物的捕食、放牧、疾病与天气。

## 使用 Java 17 或以上版本

```sh
java -jar savanna-simulation-v3.3-perf-preview.jar
```

## 3D 浏览器预览

```sh
java -Xmx2g -cp savanna-simulation-v3.3-perf-preview.jar savanna.SimServer --no-open
```

打开 http://127.0.0.1:8787/。

## 从源码启动

```sh
BUILD_ID=local-dev ./build.sh
./run.sh
```

运行 ./check.sh 执行工程测试。带 3D 的版本需要 Node.js 运行前端模块测试。
