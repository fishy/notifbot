load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "kt_android_library")
load("@gmaven_rules//:defs.bzl", "gmaven_artifact")

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
        "@com_squareup_okhttp3_okhttp//jar",
        "@com_squareup_okio_okio//jar",

        gmaven_artifact("androidx.appcompat:appcompat:aar:1.0.0"),
        gmaven_artifact("androidx.cardview:cardview:aar:1.0.0"),
        gmaven_artifact("androidx.core:core:aar:1.0.0"),
        gmaven_artifact("androidx.recyclerview:recyclerview:aar:1.0.0"),

        # indirect deps:
        gmaven_artifact("androidx.drawerlayout:drawerlayout:aar:1.0.0"),
        gmaven_artifact("androidx.fragment:fragment:aar:1.0.0"),
        gmaven_artifact("androidx.lifecycle:lifecycle-common:jar:2.0.0"),
        gmaven_artifact("androidx.lifecycle:lifecycle-viewmodel:aar:2.0.0"),
    ],
)
