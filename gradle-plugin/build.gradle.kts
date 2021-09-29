/*
 * Amalgamation
 * Copyright (C) 2021 Astrarre
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

plugins {
    `java-gradle-plugin`
}

extensions.getByType<JavaPluginExtension>().apply {
    sourceCompatibility = JavaVersion.VERSION_16
    targetCompatibility = JavaVersion.VERSION_16
}

val minecraft_version: String by project
val forge_version: String by project
tasks.processResources {
    inputs.properties("forge_version" to forge_version, "minecraft_version" to minecraft_version)

    filesMatching("gradle_data.properties") {
        expand("forge_version" to forge_version, "minecraft_version" to minecraft_version)
    }
}

dependencies {
    compileOnly("org.jetbrains", "annotations", "20.1.0")

    implementation(rootProject.project(":api"))
    implementation("com.google.guava", "guava", "30.1-jre")
    implementation("net.devtech", "signutil", "1.0.0")
    implementation("com.google.code.gson", "gson", "2.8.6")
    implementation("org.ow2.asm", "asm-tree", "9.1")
    implementation("org.cadixdev", "lorenz", "0.5.6")
    implementation("org.apache.commons", "commons-collections4", "4.4")
    implementation("io.github.astrarre", "tiny-remapper", "1.0.0")
    implementation("net.fabricmc", "lorenz-tiny", "3.0.0")
    implementation("net.fabricmc", "dev-launch-injector", "0.2.1+build.8")
    implementation("it.unimi.dsi:fastutil:8.5.6")
    implementation("org.ow2.asm:asm-commons:9.1")
    implementation("net.fabricmc:mapping-io:0.2.1")
    implementation("net.devtech:zip-io:3.0.8")
    //implementation("com.github.javaparser:javaparser-core:3.22.0")
    //implementation("com.github.javaparser:javaparser-symbol-solver-core:3.22.0")
    implementation("net.fabricmc", "access-widener", "1.0.2")
    //implementation("net.minecraftforge:forge:$minecraft_version-$forge_version:installer")
}

gradlePlugin {
    plugins {
        create("base") {
            id = "amalgamation-base"
            implementationClass = "io.github.astrarre.amalgamation.gradle.plugin.base.BaseAmalgamationGradlePlugin"
        }

        create("minecraft") {
            id = "amalgamation-minecraft"
            implementationClass = "io.github.astrarre.amalgamation.gradle.plugin.minecraft.MinecraftAmalgamationGradlePlugin"
        }
    }
}
