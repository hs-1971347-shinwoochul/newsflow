package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Task
import com.google.gson.annotations.SerializedName
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.yalantis.ucrop.UCrop
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date


class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var inputText: EditText

    private val SELECT_IMAGE_REQUEST = 1
    private val CAPTURE_IMAGE_REQUEST = 2
    private val CROP_IMAGE_REQUEST = 3

    //private val apiClient = ApiClient.getApiClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        inputText = findViewById(R.id.inputText)

        val summarizeButton = findViewById<Button>(R.id.summarizeButton)
        val selectImageButton = findViewById<Button>(R.id.selectImageButton)
        val captureImageButton = findViewById<Button>(R.id.captureImageButton)

        summarizeButton.setOnClickListener {
            val input = inputText.text.toString()
            val summarizedText = summarizeText(input)
            inputText.setText(summarizedText)
        }

        selectImageButton.setOnClickListener {
            val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(galleryIntent, SELECT_IMAGE_REQUEST)
        }

        captureImageButton.setOnClickListener {
            val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(captureIntent, CAPTURE_IMAGE_REQUEST)
        }
    }

//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//
//        if (resultCode == Activity.RESULT_OK) {
//            when (requestCode) {
//                SELECT_IMAGE_REQUEST -> {
//                    val selectedImageUri = data?.data
//                    imageView.setImageURI(selectedImageUri)
//                    selectedImageUri?.let {
//                        val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, selectedImageUri)
//                        //extractTextFromImage(bitmap)
//                    }
//                }
//                CAPTURE_IMAGE_REQUEST -> {
//                    val imageBitmap = data?.extras?.get("data") as Bitmap
//                    imageView.setImageBitmap(imageBitmap)
//                    //extractTextFromImage(imageBitmap)
//                }
//            }
//        }
//    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                SELECT_IMAGE_REQUEST -> {
                    val selectedImageUri = data?.data
                    selectedImageUri?.let {
                        startUCrop(selectedImageUri)
                    }
                }
                CAPTURE_IMAGE_REQUEST -> {
                    val imageBitmap = data?.extras?.get("data") as Bitmap
                    startUCrop(imageBitmap)
                }
                CROP_IMAGE_REQUEST -> {
                    val resultUri = UCrop.getOutput(data!!)
                    imageView.setImageURI(resultUri)
                    // 여기서 이미지를 서버에 업로드하고 OCR을 적용하는 코드를 추가할 수 있습니다.
                    resultUri?.let {
                        val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, it)
                        //uploadImageToServer(bitmap)
                        //밑에건 일단 해보고 오자
                        extractTextAndUploadImage(bitmap)
                    }
                }
            }
        }
    }

    private fun extractTextAndUploadImage(bitmap: Bitmap) {
        // Crop된 이미지를 OCR을 수행하여 텍스트를 추출합니다.
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

        val result: Task<Text> = recognizer.process(image)
        result.addOnSuccessListener { visionText ->
            val resultText = visionText.text
            inputText.setText(resultText)

            // Crop된 이미지를 API에 업로드합니다.
            //uploadImageToServer(bitmap)
        }.addOnFailureListener { e ->
            e.printStackTrace()
        }
    }

    private fun startUCrop(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(createImageFile())
        UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1F, 1F)
            .start(this, CROP_IMAGE_REQUEST)
    }

    private fun startUCrop(bitmap: Bitmap) {
        val destinationUri = Uri.fromFile(createImageFile())
        val sourceUri = getImageUriFromBitmap(bitmap)
        UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1F, 1F)
            .start(this, CROP_IMAGE_REQUEST)
    }

    private fun getImageUriFromBitmap(bitmap: Bitmap): Uri {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "Title", null)
        return Uri.parse(path)
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "JPEG_" + timeStamp + "_"
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val image = File.createTempFile(
            imageFileName,  /* prefix */
            ".jpg",         /* suffix */
            storageDir      /* directory */
        )
        // 파일의 경로를 반환
        Log.i("ImageFileName", "Image file name: $imageFileName")
        println("이런"+imageFileName)
        return image
    }

    data class ImageResponse(
        @SerializedName("success") val success: Boolean,
        @SerializedName("message") val message: String?,
        @SerializedName("imageUrl") val imageUrl: String?
    )

    data class Image(
        val format: String,
        val name: String,
        val data: ByteArray?, // 이미지 데이터
        val url: String
    )

    private fun summarizeText(text: String): String {
        // 텍스트 요약 로직을 추가
        return "요약된 텍스트"
    }

    //api에 이미지 전송 후 ocr 적용
//    private fun uploadImageToServer(bitmap: Bitmap) {
//        println("api접속?")
//
//        val imageView = ImageView(this)
//        imageView.setImageBitmap(bitmap)
//        //println("Bitmap 정보: Width=${bitmap.width}, Height=${bitmap.height}")
//        val outputStream = ByteArrayOutputStream()
//        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
//        val byteArray = outputStream.toByteArray()
//        val requestBody = RequestBody.create(MediaType.parse("image/jpeg"), byteArray)
//        println(requestBody)
//
//        val service = apiClient.create(ImageUploadAPI::class.java)
//        val call = service.uploadImage(requestBody)
//
//        call.enqueue(object : Callback<ImageResponse> {
//            override fun onResponse(call: Call<ImageResponse>, response: Response<ImageResponse>) {
//                if (response.isSuccessful) {
//                    println("얏호")
//                    val imageResponse = response.body()
//                    // 서버에서 반환한 이미지 URL 또는 다른 필요한 정보를 사용하여 OCR API를 호출하고 결과를 처리
//                } else {
//                    // 서버로부터 응답이 실패한 경우 처리
//                    println("오노우")
//                    Log.e("API Error", "Failed to upload image: ${response.code()}")
//                }
//            }
//
//            override fun onFailure(call: Call<ImageResponse>, t: Throwable) {
//                // 통신 실패 시 처리
//                println("이런 미띤")
//                Log.e("API Error", "Failed to upload image", t)
//            }
//        })
//    }
}

//postman 주소 연결
//object ApiClient {
//    private const val BASE_URL = "https://l24fiwe6s2.apigw.ntruss.com/custom/v1/29599/ec9ba461bdc586ea062462533b591567bb811be98192d90bd43dc52b083ae2b7/general/"
//    fun getApiClient(): Retrofit {
//        return Retrofit.Builder()
//            .baseUrl(BASE_URL)
//            .client(provideOkHttpClient(AppInterceptor()))
//            .addConverterFactory(GsonConverterFactory.create())
//            .build()
//    }
//
//    private fun provideOkHttpClient(interceptor: AppInterceptor): OkHttpClient
//            = OkHttpClient.Builder().run {
//        addInterceptor(interceptor)
//        build()
//    }
//
//    class AppInterceptor : Interceptor {
//        @Throws(IOException::class)
//        override fun intercept(chain: Interceptor.Chain) : okhttp3.Response = with(chain) {
//            val newRequest = request().newBuilder()
//                .addHeader("X-OCR-SECRET", "clVFQ1Z3d0pBcEJFVWpNcXlxTGVIUm90ekF0aFBVcnQ=")
//                .build()
//            proceed(newRequest)
//        }
//    }
//}
//
//interface ImageUploadAPI {
//    @POST("/api/upload_image")
//    fun uploadImage(@Body image: RequestBody): Call<MainActivity.ImageResponse>
//}
