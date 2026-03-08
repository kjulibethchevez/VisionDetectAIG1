package com.example.myapplication

import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var overlayView: OverlayView
    private lateinit var txtObjectCount: TextView

    private lateinit var btnSelectImage: Button
    private lateinit var btnTakePhoto: Button
    private lateinit var btnLivePreview: Button

    private lateinit var currentPhotoUri: Uri
    private var objectDetector: ObjectDetector? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->

            uri ?: return@registerForActivityResult

            val bitmap =
                BitmapFactory.decodeStream(
                    contentResolver.openInputStream(uri)
                )

            processBitmap(bitmap)
        }

    private val takePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->

            if (!success) return@registerForActivityResult

            try {

                val input =
                    contentResolver.openInputStream(currentPhotoUri)

                val bitmap = BitmapFactory.decodeStream(input)

                if (bitmap == null) {

                    Toast.makeText(
                        this,
                        "No se pudo cargar la imagen",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@registerForActivityResult
                }

                val rotated =
                    rotateImageIfRequired(bitmap, currentPhotoUri)

                processBitmap(rotated)

            } catch (e: Exception) {

                Toast.makeText(
                    this,
                    "Error procesando imagen",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        overlayView = findViewById(R.id.overlay)
        txtObjectCount = findViewById(R.id.txtObjectCount)

        btnSelectImage = findViewById(R.id.btnSelectImage)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        btnLivePreview = findViewById(R.id.btnLivePreview)

        checkPermissions()

        setupObjectDetector()

        btnSelectImage.setOnClickListener {

            pickImageLauncher.launch("image/*")
        }

        btnTakePhoto.setOnClickListener {

            val photoFile = createImageFile()

            currentPhotoUri =
                FileProvider.getUriForFile(
                    this,
                    "${packageName}.provider",
                    photoFile
                )

            takePhotoLauncher.launch(currentPhotoUri)
        }

        btnLivePreview.setOnClickListener {

            startActivity(
                Intent(this, LivePreviewActivity::class.java)
            )
        }
    }

    private fun processBitmap(bitmap: Bitmap) {

        imageView.setImageBitmap(bitmap)

        imageView.post {

            overlayView.setImageBounds(
                bitmap.width,
                bitmap.height,
                imageView.width,
                imageView.height
            )

            val boxes = runObjectDetection(bitmap)

            val result = drawDetections(bitmap, boxes)

            saveImage(result)
        }
    }

    private fun checkPermissions() {

        if (
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            requestPermissions(
                arrayOf(android.Manifest.permission.CAMERA),
                100
            )
        }
    }

    private fun createImageFile(): File {

        val timestamp =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.US
            ).format(Date())

        val dir =
            getExternalFilesDir(
                Environment.DIRECTORY_PICTURES
            )

        return File.createTempFile(
            "IMG_$timestamp",
            ".jpg",
            dir
        )
    }

    private fun setupObjectDetector() {

        try {

            val options =
                ObjectDetector.ObjectDetectorOptions
                    .builder()
                    .setMaxResults(5)
                    .setScoreThreshold(0.5f)
                    .build()

            objectDetector =
                ObjectDetector.createFromFileAndOptions(
                    this,
                    "lite2-detection-metadata.tflite",
                    options
                )

        } catch (e: Exception) {

            Log.e("AI", "Error cargando modelo")
        }
    }

    private fun runObjectDetection(bitmap: Bitmap): List<DetectionBox> {

        val image =
            TensorImage.fromBitmap(bitmap)

        val results =
            objectDetector?.detect(image)

        val boxes =
            results?.map {

                DetectionBox(
                    it.boundingBox,
                    it.categories[0].label,
                    it.categories[0].score
                )

            } ?: listOf()

        overlayView.setBoxes(boxes)

        // MOSTRAR CONTEO
        txtObjectCount.text = "Objetos detectados: ${boxes.size}"

        return boxes
    }

    private fun drawDetections(
        bitmap: Bitmap,
        boxes: List<DetectionBox>
    ): Bitmap {

        val mutable =
            bitmap.copy(Bitmap.Config.ARGB_8888, true)

        val canvas = Canvas(mutable)

        val boxPaint = Paint().apply {

            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }

        val textPaint = Paint().apply {

            color = Color.RED
            textSize = 50f
        }

        boxes.forEach {

            canvas.drawRect(it.rect, boxPaint)

            val text =
                "${it.label} ${(it.score * 100).toInt()}%"

            canvas.drawText(
                text,
                it.rect.left,
                it.rect.top - 10,
                textPaint
            )
        }

        return mutable
    }

    private fun saveImage(bitmap: Bitmap) {

        val name =
            "Detection_${System.currentTimeMillis()}.jpg"

        val resolver = contentResolver

        val values = ContentValues().apply {

            put(
                MediaStore.MediaColumns.DISPLAY_NAME,
                name
            )

            put(
                MediaStore.MediaColumns.MIME_TYPE,
                "image/jpeg"
            )

            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES
            )
        }

        val uri =
            resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )

        uri?.let {

            val stream =
                resolver.openOutputStream(it)

            stream?.use {

                bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    100,
                    it
                )
            }

            Toast.makeText(
                this,
                "Imagen guardada en galería",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun rotateImageIfRequired(
        bitmap: Bitmap,
        uri: Uri
    ): Bitmap {

        val input =
            contentResolver.openInputStream(uri)

        val exif =
            ExifInterface(input!!)

        val orientation =
            exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

        val matrix = Matrix()

        when (orientation) {

            ExifInterface.ORIENTATION_ROTATE_90 ->
                matrix.postRotate(90f)

            ExifInterface.ORIENTATION_ROTATE_180 ->
                matrix.postRotate(180f)

            ExifInterface.ORIENTATION_ROTATE_270 ->
                matrix.postRotate(270f)

            else -> return bitmap
        }

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }

    override fun onDestroy() {

        objectDetector?.close()

        super.onDestroy()
    }
}