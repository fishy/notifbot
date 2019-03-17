load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "kt_android_library")
load("@rules_jvm_external//:defs.bzl", "artifact")

PACKAGE = "com.yhsif.notifbot"
MANIFEST = "AndroidManifest.xml"
MANIFEST_VALUES = {"PACKAGE": PACKAGE}

android_binary(
    name = "app",
    custom_package = PACKAGE,
    manifest = MANIFEST,
    manifest_values = MANIFEST_VALUES,
    deps = [
        ":notifbot",
    ],
)

kt_android_library(
    name = "notifbot",
    srcs = glob(["src/**/*.kt"]),
    custom_package = PACKAGE,
    manifest = MANIFEST,
    resource_files = glob(["res/**/*"]),
    deps = [
        artifact("com.squareup.okhttp3:okhttp"),

        artifact("androidx.appcompat:appcompat"),
        artifact("androidx.cardview:cardview"),
        artifact("androidx.core:core:aar"),
        artifact("androidx.recyclerview:recyclerview"),

        # indirect deps:
        artifact("androidx.drawerlayout:drawerlayout"),
        artifact("androidx.fragment:fragment"),
        artifact("androidx.lifecycle:lifecycle-common"),
        artifact("androidx.lifecycle:lifecycle-viewmodel"),
    ],
)
