plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.signing)
}

android {
    namespace = "app.rkz.airgapsdk"

    compileSdk = 35

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                groupId = "app.rkz"
                artifactId = "airgap-sdk"
                version = "0.1.0"

                from(components["release"])
                artifact(javadocJar)

                pom {
                    name.set("Airgap")
                    description.set("A library for air-gapped data transfer using QR codes.")
                    url.set("https://github.com/rkz-app/AirgapSDK")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("rkz")
                            name.set("RKZ")
                            email.set("alex@rkz.app")
                        }
                    }
                    scm {
                        connection.set("scm:git:github.com/rkz-app/AirgapSDK.git")
                        developerConnection.set("scm:git:ssh://github.com/rkz-app/AirgapSDK.git")
                        url.set("https://github.com/rkz-app/AirgapSDK")
                    }
                }
            }
        }
        // Define a local repository to generate the bundle structure
        repositories {
            maven {
                name = "bundle"
                url = uri(layout.buildDirectory.dir("repo"))
            }
        }
    }

    signing {
        isRequired = gradle.taskGraph.hasTask("publishToSonatype") || gradle.taskGraph.hasTask("publishReleasePublicationToBundleRepository")
        useGpgCmd()
        sign(publishing.publications["release"])
    }
}

// Task to create the ZIP bundle for manual upload to central.sonatype.com
tasks.register<Zip>("generateBundle") {
    dependsOn("publishReleasePublicationToBundleRepository")
    from(layout.buildDirectory.dir("repo"))
    archiveFileName.set("bundle.zip")
    destinationDirectory.set(layout.buildDirectory)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.airgap)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.compose)
    implementation(platform(libs.compose.bom))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
