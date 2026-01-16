// Copyright 2026 Kushal Das
// SPDX-License-Identifier: MIT

const COMMANDS: &[&str] = &["take_picture"];

fn main() {
    tauri_plugin::Builder::new(COMMANDS)
        .android_path("android")
        .build();
}
