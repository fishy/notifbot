# NotifBot

An [Android app][play] and [Telegram bot][bot] that forwards Android
notifications to Telegram.

## Building

For Android part, the building system used is [Bazel].
You only need to manually install Bazel,
and Bazel will get all the dependencies for you
(including Kotlin and third-party Bazel rules).

The Bazel rules depends on the `ANDROID_HOME` environment variable.

Run [`tools/release.sh`](android/tools/release.sh) to sign the apk with a
release key. [More details].

## License

BSD 3-Clause, refer to LICENSE file for more details.

[play]: https://play.google.com/store/apps/details?id=com.yhsif.notifbot
[bot]: https://t.me/AndroidNotificationBot?start=1
[Bazel]: https://bazel.build/
[More details]: https://developer.android.com/studio/publish/app-signing.html#signing-manually
