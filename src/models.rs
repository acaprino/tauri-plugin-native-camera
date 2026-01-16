// Copyright 2026 Kushal Das
// SPDX-License-Identifier: MIT

use serde::{Deserialize, Serialize};

/// Result of a successful photo capture.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CaptureResult {
    /// Base64-encoded JPEG image data.
    pub image_data: String,
    /// Width of the captured image in pixels.
    pub width: u32,
    /// Height of the captured image in pixels.
    pub height: u32,
}
