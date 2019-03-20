package my.fastcampu.vision.api

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.AsyncTask
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.vision.v1.Vision
import com.google.api.services.vision.v1.VisionRequest
import com.google.api.services.vision.v1.VisionRequestInitializer
import com.google.api.services.vision.v1.model.*
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.util.*

class LabelDetectionTask(
    private val packageName: String,
    private val packageManager: PackageManager,
    private val activity: MainActivity
) {
    private val CLOUD_VISION_API_KEY = "AIzaSyCr40u94WkSviinBJKewEyZXPdaubZ6IyY"
    private val ANDROID_PACKAGE_HEADER = "X-Android-Package"
    private val ANDROID_CERT_HEADER = "X-Android_Cert"
    private val MAX_RESULTS = 10
    private var labelDetectionNotifierInterface: LabelDetectionNotifierInterface? = null
    private var requestType: String? = null

    interface LabelDetectionNotifierInterface {
        fun notifiyResult(result: String)
    }

    fun requestCloudVisionApi(
        bitmap: Bitmap,
        labelDetectionNotifierInterface: LabelDetectionNotifierInterface,
        requestType: String
    ) {
        this.requestType = requestType
        this.labelDetectionNotifierInterface = labelDetectionNotifierInterface
        val visionTask = ImageRequestTask(prepareImageRequest(bitmap))
        visionTask.execute()
    }


    inner class ImageRequestTask constructor(
        val request: Vision.Images.Annotate
    ) : AsyncTask<Any, Void, String>() {

        private val weakReference: WeakReference<MainActivity>

        init {
            weakReference = WeakReference(activity)
        }

        override fun doInBackground(vararg params: Any?): String {
            try {
                val response = request.execute()
                return findProperResponseType(response)

            } catch (e: Exception) {
            }
            return "분석 실패"
        }

        override fun onPostExecute(result: String?) {
            val activity = weakReference.get()
            if (activity != null && !activity.isFinishing) {
                result?.let { labelDetectionNotifierInterface?.notifiyResult(it) }
            }
        }
    }


    private fun prepareImageRequest(bitmap: Bitmap): Vision.Images.Annotate {
        val httpTransport = AndroidHttp.newCompatibleTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()

        val requestInitializer = object : VisionRequestInitializer(CLOUD_VISION_API_KEY) {
            override fun initializeVisionRequest(request: VisionRequest<*>?) {
                super.initializeVisionRequest(request)
                val packageName = packageName
                request?.requestHeaders?.set(ANDROID_PACKAGE_HEADER, packageName)
                val sig = PackageManagerUtil().getSignature(packageManager, packageName)
                request?.requestHeaders?.set(ANDROID_CERT_HEADER, sig)
            }
        }
        val builder = Vision.Builder(httpTransport, jsonFactory, null)
        builder.setVisionRequestInitializer(requestInitializer)
        val vision = builder.build()

        val batchAnnotateImageRequest = BatchAnnotateImagesRequest()
        batchAnnotateImageRequest.requests = object : ArrayList<AnnotateImageRequest>() {
            init {
                val annotateImageRequest = AnnotateImageRequest()

                val base64EncodedImage = Image()
                val byteArrayOutputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream)
                val imageBytes = byteArrayOutputStream.toByteArray()

                base64EncodedImage.encodeContent(imageBytes)
                annotateImageRequest.image = base64EncodedImage

                annotateImageRequest.features = object : ArrayList<Feature>() {
                    init {
                        val labelDetection = Feature()
                        when (requestType) {
                            activity.LABEL_DETECTION_REQUEST -> {
                                labelDetection.type = "LABEL_DETECTION"
                            }
                            activity.LANDMARK_DETECTION_REQUEST -> {
                                labelDetection.type = "LANDMARK_DETECTION"
                            }
                        }
                        labelDetection.maxResults = MAX_RESULTS
                        add(labelDetection)
                    }
                }
                add(annotateImageRequest)
            }
        }
        val annotateRequest = vision.images().annotate(batchAnnotateImageRequest)
        annotateRequest.setDisableGZipContent(true)
        return annotateRequest
    }


    private fun findProperResponseType(response: BatchAnnotateImagesResponse): String {
        when (requestType) {
            activity.LABEL_DETECTION_REQUEST -> {
                return convertResponseToString(response.responses[0].labelAnnotations)
            }
            activity.LANDMARK_DETECTION_REQUEST -> {
                return convertResponseToString(response.responses[0].landmarkAnnotations)
            }
        }
        return "분석 실패"
    }

    private fun convertResponseToString(labels: List<EntityAnnotation>): String {
        val message = StringBuilder("분석 결과\n")
        labels.forEach {
            message.append(String.format(Locale.US, "%.3f: %s", it.score, it.description))
            message.append("\n")
        }
        return message.toString()
    }

}