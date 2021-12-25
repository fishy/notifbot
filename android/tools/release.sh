#!/bin/sh

set -e

keyfile=${1:-"${HOME}/.android/release.jks"}
tempfile=$(mktemp -u app.tmp.apk.XXXXXXXXXX)

bazel build --config=release :app
zipalign -p 4 bazel-bin/app_unsigned.apk "${tempfile}"
apksigner sign --ks "${keyfile}" --out app.apk "${tempfile}"
rm "${tempfile}"
