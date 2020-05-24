#!/bin/sh

set -e

bazel run @unpinned_maven//:pin
