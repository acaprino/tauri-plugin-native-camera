// Copyright 2026 Kushal Das
// SPDX-License-Identifier: MIT

//! # Tauri Plugin Native Camera
//!
//! A Tauri plugin that captures photos using the device's native camera application.
//!
//! This plugin uses Android's `ACTION_IMAGE_CAPTURE` intent to open the system camera app
//! (Samsung Camera, Google Camera, etc.) rather than implementing a custom camera UI.
//! This ensures compatibility with all Android devices and respects system UI conventions.
//!
//! ## Features
//!
//! - Uses the device's native camera app for familiar UX
//! - Returns captured photos as base64-encoded JPEG data
//! - Handles camera permissions automatically
//! - Works on all Android devices
//!
//! ## Usage
//!
//! ```rust,ignore
//! // In your Tauri app's lib.rs
//! fn run() {
//!     tauri::Builder::default()
//!         .plugin(tauri_plugin_native_camera::init())
//!         .run(tauri::generate_context!())
//!         .expect("error while running tauri application");
//! }
//! ```
//!
//! ```typescript
//! // In your frontend code
//! import { takePicture } from 'tauri-plugin-native-camera-api';
//!
//! const result = await takePicture();
//! console.log('Photo base64:', result.imageData);
//! console.log('Dimensions:', result.width, 'x', result.height);
//! ```

use tauri::{
    plugin::{Builder, TauriPlugin},
    Runtime,
};

#[cfg(target_os = "android")]
use tauri::Manager;

pub use models::*;

#[cfg(target_os = "android")]
mod mobile;

#[cfg(target_os = "android")]
use mobile::NativeCamera;

mod models;
mod error;

pub use error::{Error, Result};

#[cfg(target_os = "android")]
mod commands {
    use super::*;

    #[tauri::command]
    pub(crate) async fn take_picture<R: Runtime>(
        app: tauri::AppHandle<R>,
        _window: tauri::Window<R>,
    ) -> Result<CaptureResult> {
        let camera = app.state::<NativeCamera<R>>();
        camera.take_picture()
    }
}

/// Extensions to [`tauri::App`], [`tauri::AppHandle`] and [`tauri::Window`] to access the native camera APIs.
#[cfg(target_os = "android")]
pub trait NativeCameraExt<R: Runtime> {
    fn native_camera(&self) -> &NativeCamera<R>;
}

#[cfg(target_os = "android")]
impl<R: Runtime, T: Manager<R>> crate::NativeCameraExt<R> for T {
    fn native_camera(&self) -> &NativeCamera<R> {
        self.state::<NativeCamera<R>>().inner()
    }
}

/// Initializes the plugin.
///
/// # Example
///
/// ```rust,ignore
/// tauri::Builder::default()
///     .plugin(tauri_plugin_native_camera::init())
///     .run(tauri::generate_context!())
///     .expect("error while running tauri application");
/// ```
pub fn init<R: Runtime>() -> TauriPlugin<R> {
    Builder::new("native-camera")
        .setup(|_app, _api| {
            #[cfg(target_os = "android")]
            {
                let handle = _api.register_android_plugin("in.kushaldas.plugin.nativecamera", "NativeCameraPlugin")?;
                _app.manage(NativeCamera::new(handle));
            }
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            #[cfg(target_os = "android")]
            commands::take_picture
        ])
        .build()
}
