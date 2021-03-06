
import ca.stellardrift.build.common.adventure
import ca.stellardrift.build.common.configurate
import kr.entree.spigradle.kotlin.bungeecord
import net.kyori.indra.sonatypeSnapshots

/*
 * PermissionsEx
 * Copyright (C) zml and PermissionsEx contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

plugins {
    id("pex-platform")
    id("ca.stellardrift.localization")
    id("kr.entree.spigradle.bungee")
}

repositories {
    sonatypeSnapshots()
}

dependencies {
    val adventurePlatformVersion: String by project
    val slf4jVersion: String by project

    api(project(":impl-blocks:minecraft")) {
        exclude("com.google.code.gson")
        exclude("com.google.guava")
        exclude("org.yaml", "snakeyaml")
    }

    implementation(configurate("yaml")) {
        exclude("org.yaml", "snakeyaml")
    }
    implementation(adventure("platform-bungeecord", adventurePlatformVersion)) {
        exclude("com.google.code.gson")
    }
    implementation("org.slf4j:slf4j-jdk14:$slf4jVersion")
    implementation(project(":impl-blocks:minecraft")) { isTransitive = false }
    api(project(":impl-blocks:proxy-common")) { isTransitive = false }
    implementation(project(":impl-blocks:hikari-config"))

    shadow(bungeecord("1.16-R0.4-SNAPSHOT"))
}

bungee {
    val pexDescription: String by project
    val pexSuffix: String by project
    name = rootProject.name
    version = "${project.version}$pexSuffix"
    description = pexDescription
    debug {
        args("--nojline")
    }
}

pexPlatform {
    relocate(
        "com.github.benmanes",
        "com.typesafe",
        "com.zaxxer",
        "io.leangen.geantyref",
        "kotlin",
        "kotlinx",
        "net.kyori",
        "org.antlr.v4.runtime",
        "org.jetbrains.annotations",
        "org.slf4j",
        "org.spongepowered.configurate"
    )
}
