#!/bin/sh

set -e

keyfile=${1:-"${HOME}/.android/release.jks"}

target="release"
output="app.aab"
tempfile=$(mktemp -u app.tmp.aab.XXXXXXXXXX)

bazel build --config=release ":${target}"
cp "bazel-bin/${target}.aab" "${tempfile}"
jarsigner -keystore "${keyfile}" "${tempfile}" release
mv "${tempfile}" "${output}"
