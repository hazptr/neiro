plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)
}

android {
    namespace = "mihon.telemetry"
}

dependencies {
    implementation(projects.core.common)
}
