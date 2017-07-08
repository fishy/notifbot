scalaVersion := "2.11.8"

enablePlugins(AndroidApp)
android.useSupportVectors

versionCode := Some(4)
version := "0.3.1-beta"

instrumentTestRunner :=
  "android.support.test.runner.AndroidJUnitRunner"

platformTarget := "android-25"

minSdkVersion := "23"

javacOptions in Compile ++= "-source" :: "1.7" :: "-target" :: "1.7" :: Nil

proguardOptions ++= Seq(
  "-dontwarn okio.**",
  "-dontwarn javax.annotation.Nullable",
  "-dontwarn javax.annotation.ParametersAreNonnullByDefault")

libraryDependencies ++=
  "com.squareup.okhttp3" % "okhttp" % "3.8.0" ::
  "com.android.support" % "appcompat-v7" % "25.3.0" ::
  "com.android.support" % "cardview-v7" % "25.3.0" ::
  "com.android.support" % "recyclerview-v7" % "25.3.0" ::
  "com.android.support" % "support-v4" % "25.3.0" ::
  "com.android.support.test" % "runner" % "0.5" % "androidTest" ::
  "com.android.support.test.espresso" % "espresso-core" % "2.2.2" % "androidTest" ::
  Nil
