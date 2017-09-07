# NotifBot

An Android app and Telegram bot that forwards Android notifications to Telegram.

## Building

For Android part, the building system used is [Bazel](https://bazel.build/).
You only need to manually install Bazel,
and Bazel will get all the dependencies for you
(including Kotlin and third-party Bazel rules).

The Bazel rules depends on the `ANDROID_HOME` environment variable.

To get the release apk, use the script in `android/tools/release.sh`.

## License

BSD 3-Clause, refer to LICENSE file for more details.
