load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "kt_android_library")

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

        "@androidx_appcompat_appcompat_1_0_0//aar",
        "@androidx_cardview_cardview_1_0_0//aar",
        "@androidx_core_core_1_0_0//aar",
        "@androidx_recyclerview_recyclerview_1_0_0//aar",

        # indirect deps:
        "@androidx_drawerlayout_drawerlayout_1_0_0//aar",
        "@androidx_fragment_fragment_1_0_0//aar",
        "@androidx_lifecycle_lifecycle_common_2_0_0//jar",
        "@androidx_lifecycle_lifecycle_viewmodel_2_0_0//aar",
    ],
)
