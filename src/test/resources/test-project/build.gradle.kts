import com.goncalossilva.useanybrowser.useAnyBrowser

plugins {
    kotlin("multiplatform") version "2.3.0"
    id("com.goncalossilva.useanybrowser") version "0.0.0-SNAPSHOT"
}

repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    js {
        browser {
            testTask {
                useKarma {
                    useAnyBrowser()
                }
            }
        }
    }

    sourceSets {
        jsTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
