#!/bin/sh

set -e

REPIN=1 bazel run @maven//:pin
