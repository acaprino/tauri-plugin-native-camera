// Copyright 2026 Kushal Das
// SPDX-License-Identifier: MIT

use serde::{Deserialize, Serialize};

/// Errors that can occur when using the native camera plugin.
#[derive(Debug, thiserror::Error, Serialize, Deserialize)]
pub enum Error {
    /// The user cancelled the camera operation.
    #[error("Camera operation was cancelled by user")]
    Cancelled,
    /// Camera permission was denied.
    #[error("Camera permission denied")]
    PermissionDenied,
    /// Failed to create temporary file for photo.
    #[error("Failed to create temporary file: {0}")]
    FileCreationFailed(String),
    /// Failed to read the captured photo.
    #[error("Failed to read captured photo: {0}")]
    ReadFailed(String),
    /// No camera available on the device.
    #[error("No camera available on this device")]
    NoCameraAvailable,
    /// Plugin not initialized or not available on this platform.
    #[error("Plugin not available: {0}")]
    PluginNotAvailable(String),
    /// An unknown error occurred.
    #[error("Unknown error: {0}")]
    Unknown(String),
}

/// Result type for native camera operations.
pub type Result<T> = std::result::Result<T, Error>;
