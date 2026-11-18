# Savanna Simulation v3.3-p1-herbivore-preview

[English](README.md)

**开发预览**

采用 P-1 草食动物默认参数。P-2 捕食者调优和 P-3 口渴验收仍在进行。

项目从 David J. Barnes 和 Michael Kölling 的 BlueJ 狐狸与兔子示例扩展而来，模拟五种动物的捕食、放牧、疾病与天气。

## 使用 Java 17 或以上版本

```sh
java -jar savanna-simulation-v3.3-p1-herbivore-preview.jar
```

## 3D 浏览器预览

```sh
java -Xmx2g -cp savanna-simulation-v3.3-p1-herbivore-preview.jar savanna.SimServer --no-open
```

打开 http://127.0.0.1:8787/。

## 从源码启动

```sh
BUILD_ID=local-dev ./build.sh
./run.sh
```

运行 ./check.sh 执行工程测试。带 3D 的版本需要 Node.js 运行前端模块测试。
