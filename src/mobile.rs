// Copyright 2026 Kushal Das
// SPDX-License-Identifier: MIT

use tauri::{plugin::PluginHandle, Runtime};

use crate::{models::CaptureResult, Error, Result};

/// Access to the native camera APIs.
pub struct NativeCamera<R: Runtime>(PluginHandle<R>);

impl<R: Runtime> NativeCamera<R> {
    pub(crate) fn new(handle: PluginHandle<R>) -> Self {
        Self(handle)
    }

    /// Take a picture using the device's native camera app.
    ///
    /// This will open the system camera application (Samsung Camera, Google Camera, etc.)
    /// and return the captured photo as base64-encoded JPEG data.
    ///
    /// # Returns
    ///
    /// Returns a `CaptureResult` containing:
    /// - `image_data`: Base64-encoded JPEG image data
    /// - `width`: Image width in pixels
    /// - `height`: Image height in pixels
    ///
    /// # Errors
    ///
    /// Returns an error if:
    /// - The user cancels the operation
    /// - Camera permission is denied
    /// - No camera is available on the device
    /// - The photo cannot be read
    pub fn take_picture(&self) -> Result<CaptureResult> {
        self.0
            .run_mobile_plugin("takePicture", ())
            .map_err(|e| Error::Unknown(e.to_string()))
    }
}
