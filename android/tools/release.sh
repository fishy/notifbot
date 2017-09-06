#!/bin/sh

bazel build :app -c opt && \
  rm -f app-tmp.apk && \
  zipalign -p 4 bazel-bin/app_unsigned.apk app-tmp.apk && \
  apksigner sign --ks release.jks --out app.apk app-tmp.apk && \
  rm app-tmp.apk
