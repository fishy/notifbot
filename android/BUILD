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
        "@android_arch_core_common_1_0_0//jar",
        "@android_arch_lifecycle_common_1_0_3//jar",
        "@android_arch_lifecycle_runtime_1_0_3//aar",
        "@com_android_support_appcompat_v7_27_0_1//aar",
        "@com_android_support_cardview_v7_27_0_1//aar",
        "@com_android_support_recyclerview_v7_27_0_1//aar",
        "@com_android_support_support_compat_27_0_1//aar",
        "@com_android_support_support_core_utils_27_0_1//aar",
        "@com_android_support_support_core_ui_27_0_1//aar",
        "@com_android_support_support_fragment_27_0_1//aar",
        "@com_android_support_support_vector_drawable_27_0_1//aar",
    ],
)
