// Authors: OLI Systems GmbH
plugins {
    java
}

group = "snc.openchargingnetwork.plugins"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    compileOnly("snc.openchargingnetwork:node:ocn-v2") {
        isTransitive = false
    }
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.1.20")
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.4.5")
    compileOnly("org.springframework:spring-context:6.2.6")
    compileOnly("org.springframework:spring-web:6.2.6")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa:3.4.5")
    compileOnly("jakarta.persistence:jakarta.persistence-api:3.1.0")
    compileOnly("org.springframework.data:spring-data-jpa:3.4.5")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("snc.openchargingnetwork:node:ocn-v2") {
        isTransitive = false
    }
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.20")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    testImplementation("org.springframework:spring-context:6.2.6")
    testImplementation("org.springframework:spring-web:6.2.6")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<Jar> {
    archiveBaseName.set("ocn-node-edx-plugin")
    manifest {
        attributes(
            "Implementation-Title" to "OCN Node EDX Plugin",
            "Implementation-Version" to project.version
        )
    }
}
