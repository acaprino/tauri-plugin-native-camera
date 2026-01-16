// Copyright 2026 Kushal Das
// SPDX-License-Identifier: MIT

import { invoke } from "@tauri-apps/api/core";

/**
 * Result of a successful photo capture.
 */
export interface CaptureResult {
  /**
   * Base64-encoded JPEG image data.
   * Can be used directly in an img src: `data:image/jpeg;base64,${imageData}`
   */
  imageData: string;
  /**
   * Width of the captured image in pixels.
   */
  width: number;
  /**
   * Height of the captured image in pixels.
   */
  height: number;
}

/**
 * Take a picture using the device's native camera application.
 *
 * This opens the system camera app (Samsung Camera, Google Camera, etc.)
 * and returns the captured photo as base64-encoded JPEG data.
 *
 * @example
 * ```typescript
 * import { takePicture } from 'tauri-plugin-native-camera-api';
 *
 * try {
 *   const result = await takePicture();
 *   console.log('Dimensions:', result.width, 'x', result.height);
 *
 *   // Use in an img element
 *   const imgSrc = `data:image/jpeg;base64,${result.imageData}`;
 *   document.getElementById('preview').src = imgSrc;
 * } catch (error) {
 *   console.error('Camera error:', error);
 * }
 * ```
 *
 * @returns A promise that resolves with the captured photo data.
 * @throws Will throw an error if:
 *   - The user cancels the camera operation
 *   - Camera permission is denied
 *   - No camera is available on the device
 *   - The photo cannot be read
 */
export async function takePicture(): Promise<CaptureResult> {
  return await invoke<CaptureResult>("plugin:native-camera|take_picture");
}
