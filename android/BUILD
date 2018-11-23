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

        "@android_arch_lifecycle_common_1_1_1//jar",
        "@android_arch_lifecycle_viewmodel_1_1_1//aar",
        "@com_android_support_appcompat_v7_28_0_0//aar",
        "@com_android_support_cardview_v7_28_0_0//aar",
        "@com_android_support_drawerlayout_28_0_0//aar",
        "@com_android_support_recyclerview_v7_28_0_0//aar",
        "@com_android_support_support_compat_28_0_0//aar",
        "@com_android_support_support_core_utils_28_0_0//aar",
        "@com_android_support_support_core_ui_28_0_0//aar",
        "@com_android_support_support_fragment_28_0_0//aar",
    ],
)
