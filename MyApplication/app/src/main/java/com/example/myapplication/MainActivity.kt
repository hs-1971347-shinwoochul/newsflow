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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
import retrofit2.http.GET
import retrofit2.http.POST
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import javax.net.ssl.*
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var inputText: EditText

    private val SELECT_IMAGE_REQUEST = 1
    private val CAPTURE_IMAGE_REQUEST = 2
    private val CROP_IMAGE_REQUEST = 3

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://192.168.123.114:443/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        inputText = findViewById(R.id.inputText)

        //sendDataToServer()

        val summarizeButton = findViewById<Button>(R.id.summarizeButton)
        val selectImageButton = findViewById<Button>(R.id.selectImageButton)
        val captureImageButton = findViewById<Button>(R.id.captureImageButton)

        summarizeButton.setOnClickListener {
//            ignoreSSLCertificate { success ->
//                if (success) {
//                    val input = inputText.text.toString()
//                    val summarizedText = summarizeText(input)
//                    inputText.setText(summarizedText)
//                    connect()
//                } else {
//                    // SSL 인증서 검증에 실패한 경우에 대한 처리
//                    // 예를 들어 사용자에게 알림을 표시하거나 적절한 조치를 취할 수 있습니다.
//                    println("노우 안도ㅓㅐㅆ더")
//                }
//            }

            val input = inputText.text.toString()
            sendDataToServer(input)
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

    private fun sendDataToServer(data: String? = null) {

        val port = 5000 // HTTP 포트 80
        CoroutineScope(Dispatchers.IO).launch {
            val serverAddress = InetAddress.getByName("192.168.123.110")
            val socket = Socket(serverAddress, port)
            val outputStream = socket.getOutputStream()
            val writer = BufferedWriter(OutputStreamWriter(outputStream))

            val path = "/api/summary" // POST 요청을 받는 서버의 경로
            val postData = data ?: "default data" // POST 요청 본문 데이터

            // POST 요청 메시지 작성
            val requestMessage = """
        POST $path HTTP/1.1
        Host: ${serverAddress.hostName}
        Content-Type: application/x-www-form-urlencoded
        Content-Length: ${postData.length}
        
        $postData
    """.trimIndent()

            // POST 요청 메시지를 서버로 전송
            writer.write(requestMessage)
            writer.flush()

            val inputStream = socket.getInputStream()
            val reader = BufferedReader(InputStreamReader(inputStream))
            val response = StringBuilder()

            // 서버로부터의 응답을 읽어옴
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line).append('\n')
            }


            // UI 업데이트
            withContext(Dispatchers.Main) {
                inputText.setText(response.toString())
            }

            // 소켓 닫기
            socket.close()
        }
    }

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

    fun ignoreSSLCertificate(callback: (Boolean) -> Unit) {
        println("나 호출됐어")
        try {
            val trustAllCerts: Array<TrustManager> = arrayOf(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate?>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<X509Certificate?>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    println("나 해결됐어")
                    return arrayOf()
                }
            })

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }

            callback(true)
        } catch (e: Exception) {
            Log.e("SSS", "ignoreSSLCertificate error: ${e.message}", e)
            callback(false)
        }
    }

    fun getUnsafeOkHttpClient(): OkHttpClient.Builder {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {

            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {

            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return arrayOf()
            }
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())

        val sslSocketFactory = sslContext.socketFactory

        val builder = OkHttpClient.Builder()
        builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
        builder.hostnameVerifier { hostname, session -> true }

        return builder
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
            //.withAspectRatio(16F, 9F)
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


    data class MyRequestData(
        val key1: String
        //val key2: Int
    )


    private fun summarizeText(text: String): String {
        // 텍스트 요약 로직을 추가
        return "요약된 텍스트"
    }

    interface MyApiService {
        //@GET("/api/summary") // 실제 엔드포인트 URL을 여기에 작성
        @POST("api/summary")
        fun postDataToServer(@Body requestData: MyRequestData): Call<Void> // 반환 유형은 서버 응답을 나타냅니다. 이 경우에는 응답이 없음을 의미하는 Void 사용
    }

    private fun connect(){
        // Retrofit 서비스 인스턴스 생성
        println("뭐냐이거?")
        val myApiService = retrofit.create(MyApiService::class.java)

        // 요청 데이터 생성
        val requestData = MyRequestData("value1")

        val call = myApiService.postDataToServer(requestData)

        call.enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    println("연결 성공")
                    // 성공적으로 요청을 보냈습니다.
                    // 서버 응답을 처리할 수도 있습니다.
                } else {
                    println("연결 실패")
                    // 서버가 실패 응답을 반환했습니다.
                    // 실패 처리 코드를 여기에 작성합니다.
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                // 통신 실패 처리 코드를 여기에 작성합니다.
                println("실패가 됐다고잉?")
                Log.e("YMC", "stringToJson2: ${t.message}", t)

            }
        })
    }
}


