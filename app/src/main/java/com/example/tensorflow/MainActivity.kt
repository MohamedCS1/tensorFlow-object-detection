package com.example.tensorflow

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import com.example.tensorflow.databinding.ActivityMainBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions

class MainActivity : AppCompatActivity() {

    lateinit var binding:ActivityMainBinding
    lateinit var objectDetector:ObjectDetector
    lateinit var cameraProviderFuture:ListenableFuture<ProcessCameraProvider>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this ,R.layout.activity_main)

        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            binPreview(cameraProvider = cameraProvider)

        }, ContextCompat.getMainExecutor(this))

        val localModel = LocalModel.Builder()
            .setAssetFilePath("object_detection.tflite")
            .build()


        val customObjectDetectorOptions = CustomObjectDetectorOptions.Builder(localModel)
            .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .setClassificationConfidenceThreshold(0.5f)
            .setMaxPerObjectLabelCount(3)
            .build()

        objectDetector = ObjectDetection.getClient(customObjectDetectorOptions)

    }

    private fun binPreview(cameraProvider: ProcessCameraProvider)
    {
        val preview = Preview.Builder().build()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview.setSurfaceProvider(binding.previewView.surfaceProvider)

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280 ,720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this),{
            imageProxy->
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val image = imageProxy.image
            if (image != null)
            {
                val processImage = InputImage.fromMediaImage(image ,rotationDegrees)

                objectDetector.process(processImage)
                    .addOnSuccessListener {
                            objects ->
                        for (ob in objects)
                        {
//                            Toast.makeText(this ,ob.toString() ,Toast.LENGTH_SHORT).show()
                            if (binding.parentLayout.childCount > 1) binding.parentLayout.removeViewAt(1)
                            val element = Draw(context = this , rect = ob.boundingBox , text = ob.labels.firstOrNull()?.text ?:"Undefined")
                            binding.parentLayout.addView(element)
                        }
                        imageProxy.close()
                    }

                    .addOnFailureListener {
                        Log.d("tracking" ,it.toString())
//                        Toast.makeText(this ,it.message.toString() ,Toast.LENGTH_SHORT).show()
                        imageProxy.close()
                    }
            }

        })

        cameraProvider.bindToLifecycle(this as LifecycleOwner ,cameraSelector ,imageAnalysis ,preview)
    }
}