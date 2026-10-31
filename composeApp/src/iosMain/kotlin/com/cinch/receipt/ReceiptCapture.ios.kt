@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.cinch.receipt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import com.cinch.shared.receipt.OcrLine
import com.cinch.shared.receipt.OcrScan
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageOrientation
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import platform.VisionKit.VNDocumentCameraScan
import platform.VisionKit.VNDocumentCameraViewController
import platform.VisionKit.VNDocumentCameraViewControllerDelegateProtocol
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * iOS: belge tarayıcı (`VNDocumentCameraViewController`: kenar bulur, kırpar; simülatörde yok, galeriye düşer),
 * fotoğraf seçici (`PHPickerViewController`: izin istemez) ve Vision metin tanıma. Hepsi telefonda; fotoğraf
 * okunduktan sonra tutulmaz.
 */
@Composable
actual fun rememberReceiptCapture(maxImages: Int, onResult: (ReceiptCaptureResult) -> Unit): ReceiptCapture {
    val host = LocalUIViewController.current
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    return remember(host, maxImages) { IosReceiptCapture(host, maxImages, scope) { currentOnResult(it) } }
}

private class IosReceiptCapture(
    private val host: UIViewController,
    private val maxImages: Int,
    private val scope: CoroutineScope,
    private val onResult: (ReceiptCaptureResult) -> Unit,
) : ReceiptCapture {

    override val isSupported: Boolean = true

    // UIKit delegeleri zayıf tutar; burada güçlü tutulmazsa pencere açıkken silinir.
    private val scannerDelegate = ScannerDelegate(
        onImages = { images -> read { images.take(maxImages) } },
        onCancel = { onResult(ReceiptCaptureResult.Cancelled) },
        onError = { onResult(ReceiptCaptureResult.Failed("Tarayıcı açılamadı. Galeriden seçmeyi dene.")) },
    )
    private val pickerDelegate = PickerDelegate { results ->
        if (results.isEmpty()) onResult(ReceiptCaptureResult.Cancelled) else read { results.mapNotNull { it.loadImage() } }
    }

    override fun scan() {
        if (!VNDocumentCameraViewController.supported) {
            pickFromGallery()
            return
        }
        val scanner = VNDocumentCameraViewController()
        scanner.delegate = scannerDelegate
        topController().presentViewController(scanner, animated = true, completion = null)
    }

    override fun pickFromGallery() {
        val configuration = PHPickerConfiguration()
        configuration.filter = PHPickerFilter.imagesFilter
        configuration.selectionLimit = maxImages.toLong()
        val picker = PHPickerViewController(configuration)
        picker.delegate = pickerDelegate
        topController().presentViewController(picker, animated = true, completion = null)
    }

    private fun topController(): UIViewController {
        var controller = host
        while (true) controller = controller.presentedViewController ?: return controller
    }

    private fun read(images: suspend () -> List<UIImage>) {
        onResult(ReceiptCaptureResult.Reading)
        scope.launch {
            val result = try {
                val pages = withContext(Dispatchers.Default) { images().map { VisionText.read(it) } }
                if (pages.all { page -> page.all { it.text.isBlank() } }) {
                    ReceiptCaptureResult.Failed("Fotoğrafta yazı bulamadık. Düz ve aydınlık bir açıyla tekrar dene.")
                } else {
                    ReceiptCaptureResult.Read(OcrScan(pages))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ReceiptCaptureResult.Failed("Fotoğraf okunamadı. Bir daha dener misin?")
            }
            onResult(result)
        }
    }
}

private class ScannerDelegate(
    private val onImages: (List<UIImage>) -> Unit,
    private val onCancel: () -> Unit,
    private val onError: () -> Unit,
) : NSObject(), VNDocumentCameraViewControllerDelegateProtocol {

    override fun documentCameraViewController(controller: VNDocumentCameraViewController, didFinishWithScan: VNDocumentCameraScan) {
        val images = (0uL until didFinishWithScan.pageCount).map { didFinishWithScan.imageOfPageAtIndex(it) }
        controller.dismissViewControllerAnimated(true, completion = null)
        onImages(images)
    }

    override fun documentCameraViewControllerDidCancel(controller: VNDocumentCameraViewController) {
        controller.dismissViewControllerAnimated(true, completion = null)
        onCancel()
    }

    override fun documentCameraViewController(controller: VNDocumentCameraViewController, didFailWithError: NSError) {
        controller.dismissViewControllerAnimated(true, completion = null)
        onError()
    }
}

private class PickerDelegate(private val onResults: (List<PHPickerResult>) -> Unit) :
    NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onResults(didFinishPicking.filterIsInstance<PHPickerResult>())
    }
}

/** Seçilen fotoğrafın verisi (HEIC, JPEG…); okunamazsa null. */
private suspend fun PHPickerResult.loadImage(): UIImage? = suspendCancellableCoroutine { cont ->
    itemProvider.loadDataRepresentationForTypeIdentifier(UTTypeImage.identifier) { data, _ ->
        cont.resume(data?.let { UIImage(data = it) })
    }
}

/** Vision ile satır satır metin ve konumları (sol üst köşe başlangıçlı piksel), Android'deki ML Kit çıktısı gibi. */
private object VisionText {

    private val PREFERRED_LANGUAGES = listOf("tr-TR", "en-US")

    fun read(image: UIImage): List<OcrLine> {
        val upright = image.upright()
        val cgImage = upright.CGImage ?: return emptyList()
        val width = CGImageGetWidth(cgImage).toDouble()
        val height = CGImageGetHeight(cgImage).toDouble()

        val request = VNRecognizeTextRequest()
        request.recognitionLevel = VNRequestTextRecognitionLevelAccurate
        request.usesLanguageCorrection = true
        val supported = request.supportedRecognitionLanguagesAndReturnError(null)?.filterIsInstance<String>().orEmpty()
        PREFERRED_LANGUAGES.filter { it in supported }.takeIf { it.isNotEmpty() }?.let { request.recognitionLanguages = it }

        val handler = VNImageRequestHandler(cgImage, emptyMap<Any?, Any?>())
        if (!handler.performRequests(listOf(request), null)) return emptyList()

        return request.results.orEmpty().filterIsInstance<VNRecognizedTextObservation>().mapNotNull { observation ->
            val text = (observation.topCandidates(1u).firstOrNull() as? VNRecognizedText)?.string
            if (text.isNullOrBlank()) {
                null
            } else {
                // Vision: 0–1 arası, sol alt köşe başlangıçlı.
                observation.boundingBox.useContents {
                    OcrLine(
                        text = text,
                        left = (origin.x * width).toFloat(),
                        top = ((1 - origin.y - size.height) * height).toFloat(),
                        right = ((origin.x + size.width) * width).toFloat(),
                        bottom = ((1 - origin.y) * height).toFloat(),
                    )
                }
            }
        }
    }

    /** Galeriden gelen fotoğraf yan dönük kaydedilmiş olabilir; Vision'a dik hâli verilir. */
    private fun UIImage.upright(): UIImage {
        if (imageOrientation == UIImageOrientation.UIImageOrientationUp) return this
        val format = UIGraphicsImageRendererFormat()
        format.scale = scale
        val (w, h) = size.useContents { width to height }
        return UIGraphicsImageRenderer(size, format).imageWithActions { drawInRect(CGRectMake(0.0, 0.0, w, h)) }
    }
}
