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
        "@androidsdk//com.android.support:appcompat-v7-25.3.1",
        "@androidsdk//com.android.support:cardview-v7-25.3.1",
        "@androidsdk//com.android.support:recyclerview-v7-25.3.1",
    ],
)
