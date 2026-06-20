# Error Log and Resolutions

This log documents issues encountered during the development and setup of the concurrent transaction engine project, along with their root causes and resolutions.

---

## 1. Maven Compilation Failure (Unsupported Release Version 25)

### Error Message
```
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.15.0:compile (default-compile) on project transaction-engine: Fatal error compiling: error: release version 25 not supported -> [Help 1]
```

### Root Cause
The project configuration in `pom.xml` specifies Java 25:
```xml
<properties>
    <java.version>25</java.version>
</properties>
```
However, the Maven wrapper execution environment was using the default `JAVA_HOME` environment variable, which was pointing to a Microsoft JDK 17 installation:
```
Java version: 17.0.19, vendor: Microsoft
```
Because JDK 17 does not support compiling target code for release version 25, the compiler failed.

### Resolution
1. Searched the system for an installed JDK 25 and located it at:
   `C:\Program Files\Java\jdk-25.0.3`
2. Prepended commands with an environment variable override in the terminal to ensure Maven compiles using the correct JDK version:
   ```powershell
   $env:JAVA_HOME="C:\Program Files\Java\jdk-25.0.3"
   .\mvnw.cmd clean compile
   ```
   This successfully compiles the application with Java 25.

---

## 2. Spring Boot 4.x and Jackson 3.x Compilation Errors in Tests

### Error Message
```
[ERROR] TransactionEngineApplicationTests.java:[3,38] package com.fasterxml.jackson.databind does not exist
[ERROR] TransactionEngineApplicationTests.java:[13,63] package org.springframework.boot.test.autoconfigure.web.servlet does not exist
[ERROR] TransactionEngineApplicationTests.java:[29,2] cannot find symbol: class AutoConfigureMockMvc
[ERROR] TransactionEngineApplicationTests.java:[46,13] cannot find symbol: class ObjectMapper
```

### Root Cause
1. **Jackson 3.x Upgrade**: Spring Boot 4.x upgrades Jackson to version 3.x. Jackson 3.x changed the base package name from `com.fasterxml.jackson` to `tools.jackson`.
2. **Spring Boot 4.x Test Autoconfigure Modularization**: Spring Boot 4.x packages the MockMvc autoconfigure annotation `AutoConfigureMockMvc` under the package `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` inside the `spring-boot-webmvc-test` dependency, rather than the legacy `org.springframework.boot.test.autoconfigure.web.servlet` package.

### Resolution
Updated imports in the test class as follows:
- Change `import com.fasterxml.jackson.databind.ObjectMapper;` to `import tools.jackson.databind.ObjectMapper;`
- Change `import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;` to `import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;`

