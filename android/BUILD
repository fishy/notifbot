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
        "@maven//:androidx_appcompat_appcompat",
        "@maven//:androidx_cardview_cardview",
        "@maven//:androidx_core_core_ktx",
        "@maven//:androidx_recyclerview_recyclerview",
        "@maven//:com_google_android_gms_play_services_cronet",
        "@maven//:com_google_android_material_material",
        "@maven//:org_chromium_net_cronet_fallback",
    ],
)
