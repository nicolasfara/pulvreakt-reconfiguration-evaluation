plugins {
    id("com.gradle.enterprise") version "3.16"
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0"
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        publishOnFailure()
    }
}

rootProject.name = "pulvreakt-reconfiguration-evaluation"
