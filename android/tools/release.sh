#!/bin/sh

set -e

keyfile=${1:-"${HOME}/.android/upload.jks"}

target="release"
output="app.aab"
tempfile=$(mktemp -u app.tmp.aab.XXXXXXXXXX)

bazel build --config=release ":${target}"
cp "bazel-bin/${target}.aab" "${tempfile}"
jarsigner -keystore "${keyfile}" "${tempfile}" upload
mv "${tempfile}" "${output}"
