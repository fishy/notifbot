# NotifBot

An Android app and Telegram bot that forwards Android notifications to Telegram.

## Building

For Android part, the building system used is [Bazel](https://bazel.build/).
You only need to manually install Bazel,
and Bazel will get all the dependencies for you
(including Kotlin and third-party Bazel rules).

The Bazel rules depends on the `ANDROID_HOME` environment variable.

Run [`tools/release.sh`](android/tools/release.sh) to sign the apk with a
release key.
[More details](https://developer.android.com/studio/publish/app-signing.html#signing-manually).

## License

BSD 3-Clause, refer to LICENSE file for more details.
