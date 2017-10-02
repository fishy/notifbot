load("@org_pubref_rules_kotlin//kotlin:rules.bzl", "kotlin_android_library")

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

kotlin_android_library(
    name = "notifbot",
    srcs = glob(["src/**/*.kt"]),
    custom_package = PACKAGE,
    manifest = MANIFEST,
    resource_files = glob(["res/**/*"]),
    java_deps = [
        "@com_squareup_okhttp3_okhttp//jar",
        "@com_squareup_okio_okio//jar",
    ],
    aar_deps = [
        "@appcompat_v7//aar",
        "@cardview_v7//aar",
        "@recyclerview_v7//aar",
        "@support_compat//aar",
        "@support_core_ui//aar",
        "@support_core_utils//aar",
        "@support_fragment//aar",
        "@support_vector_drawable//aar",
    ],
)
