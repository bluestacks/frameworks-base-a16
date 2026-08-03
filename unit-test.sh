#!/bin/bash

set -euo pipefail

android_home=${1:-}
out_path=${2:-}

if [[ -z "$android_home" || -z "$out_path" ]]; then
    echo "usage: $0 <android-source-root> <out-dir>" >&2
    exit 2
fi

android_home=$(realpath "$android_home")
out_path=$(realpath "$out_path")
aidl="$out_path/host/linux-x86/bin/aidl"
aidl_source="frameworks/base/core/java/com/bluestacks/os"
native_headers="frameworks/native/libs/binder/include/binder"
services=(IBstFilterAppsService IBstUtilsService)

if [[ ! -x "$aidl" ]]; then
    echo "aidl compiler not found: $aidl" >&2
    exit 1
fi

tmp_dir=$(mktemp -d "${TMPDIR:-/tmp}/bst-aidl-order.XXXXXX")
trap 'rm -rf "$tmp_dir"' EXIT

cd "$android_home"
for service in "${services[@]}"; do
    aidl_file="$aidl_source/$service.aidl"
    java_file="$tmp_dir/$service.java"
    header_file="$native_headers/$service.h"
    java_order="$tmp_dir/$service.java.order"
    native_order="$tmp_dir/$service.native.order"

    [[ -f "$aidl_file" ]] || { echo "missing $aidl_file" >&2; exit 1; }
    [[ -f "$header_file" ]] || { echo "missing $header_file" >&2; exit 1; }

    "$aidl" -d"$tmp_dir/$service.P" -b -Iframeworks/base/core/java \
        "$aidl_file" "$java_file"

    sed -n -E \
        's/^[[:space:]]*static final int TRANSACTION_([A-Za-z0-9_]+) = \(android\.os\.IBinder\.FIRST_CALL_TRANSACTION \+ ([0-9]+)\);$/T_\1 = IBinder::FIRST_CALL_TRANSACTION + \2/p' \
        "$java_file" > "$java_order"
    sed -n -E \
        's/^[[:space:]]*(T_[A-Za-z0-9_]+)[[:space:]]*=[[:space:]]*IBinder::FIRST_CALL_TRANSACTION[[:space:]]*\+[[:space:]]*([0-9]+),?$/\1 = IBinder::FIRST_CALL_TRANSACTION + \2/p' \
        "$header_file" > "$native_order"

    if ! diff -u "$java_order" "$native_order"; then
        echo "Binder transaction order mismatch: $aidl_file vs $header_file" >&2
        exit 1
    fi
done

echo "BlueStacks Java/native Binder transaction order matches"
