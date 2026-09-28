plugins {
    java
}

group = "com.chatbiasa"
version = "0.6.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Minecraft 26.2 "Chaos Cubed" / Paper build 129. 26.3 is alpha on Paper, do not target it.
    compileOnly("io.papermc.paper:paper-api:26.2.build.129-stable")
}

tasks.withType<JavaCompile> {
    // 26.2 requires Java 25
    options.release = 25
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
