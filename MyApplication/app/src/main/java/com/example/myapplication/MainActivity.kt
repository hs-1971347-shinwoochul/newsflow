package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.example.myapplication.MessageAdapter.Companion.VIEW_TYPE_IMAGE
import com.example.myapplication.MessageAdapter.Companion.VIEW_TYPE_SUMMARY
import com.example.myapplication.MessageAdapter.Companion.VIEW_TYPE_TEXT
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import java.io.*
import java.net.InetAddress
import java.net.Socket
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var inputText: EditText // 사용자 입력 텍스트
    private lateinit var recyclerView: RecyclerView // 메시지를 표시할 RecyclerView
    private lateinit var messageAdapter: MessageAdapter // RecyclerView 어댑터
    private val messages = mutableListOf<Message>() // 메시지 리스트

    private val SELECT_IMAGE_REQUEST = 1 // 이미지 선택 요청 코드
    private val CAPTURE_IMAGE_REQUEST = 2 // 이미지 캡처 요청 코드
    private val REQUEST_PERMISSIONS = 100 // 권한 요청 코드

    private var totalImages = 0 // 총 이미지 수
    private var processedImages = 0 // 처리된 이미지 수

    private var currentPhotoPath: String = "" // 현재 사진 경로

    // 이미지 자르기 계약 등록
    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val croppedUri = result.uriContent
            if (croppedUri != null) {
                addImageMessage(croppedUri) // 이미지 메시지 추가
                extractTextAndUploadImage(croppedUri) // 이미지에서 텍스트 추출 및 업로드
            }
        } else {
            Log.e("Crop", "Error cropping image: ${result.error}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inputText = findViewById(R.id.inputText) // 사용자 입력 텍스트 초기화
        recyclerView = findViewById(R.id.recyclerView) // RecyclerView 초기화
        val summarizeButton = findViewById<Button>(R.id.summarizeButton) // 요약 버튼 초기화
        val galleryButton = findViewById<ImageButton>(R.id.galleryButton) // 갤러리 버튼 초기화
        val captureButton = findViewById<ImageButton>(R.id.photoButton) // 사진 캡처 버튼 초기화
        val crawlButton = findViewById<ImageButton>(R.id.UrlButton) // URL 크롤 버튼 초기화

        messageAdapter = MessageAdapter(messages) // 메시지 어댑터 초기화
        recyclerView.layoutManager = LinearLayoutManager(this) // 레이아웃 매니저 설정
        recyclerView.adapter = messageAdapter // 어댑터 설정

        val callback = MessageTouchHelperCallback(messageAdapter) // 터치 헬퍼 콜백 초기화
        val itemTouchHelper = ItemTouchHelper(callback) // 아이템 터치 헬퍼 초기화
        itemTouchHelper.attachToRecyclerView(recyclerView) // RecyclerView에 아이템 터치 헬퍼 연결

        summarizeButton.setOnClickListener {
            val inputMessage = inputText.text.toString()
            if (inputMessage.isNotEmpty()) {
                addMessage(inputMessage, isSummary = false) // 메시지 추가
                sendDataToServer(inputMessage) // 서버로 데이터 전송
                inputText.text.clear() // 입력 텍스트 초기화
            }
        }

        galleryButton.setOnClickListener {
            println("사진 고르기")
            val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            startActivityForResult(galleryIntent, SELECT_IMAGE_REQUEST) // 갤러리 인텐트 시작
        }

        captureButton.setOnClickListener {
            println("사진 찍기")
            takePhotoAndSave() // 사진 찍기 및 저장
        }

        crawlButton.setOnClickListener {
            showUrlInputDialog() // URL 입력 다이얼로그 표시
        }

        if (!hasRequiredPermissions()) {
            requestRequiredPermissions() // 필수 권한 요청
        }
    }

    private fun showUrlInputDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("크롤링할 URL 입력")

        val inputView = layoutInflater.inflate(R.layout.dialog_url_input, null) // URL 입력 뷰 초기화
        val urlEditText = inputView.findViewById<EditText>(R.id.urlEditText) // URL 입력 텍스트 초기화
        builder.setView(inputView)

        builder.setPositiveButton("OK") { dialog, which ->
            val url = urlEditText.text.toString()
            if (url.isNotEmpty()) {
                startCrawling(url) // 크롤링 시작
            } else {
                Toast.makeText(this@MainActivity, "URL을 입력하세요.", Toast.LENGTH_SHORT).show() // URL 입력 요청
            }
        }

        builder.setNegativeButton("취소") { dialog, which ->
            dialog.dismiss() // 다이얼로그 닫기
        }

        val dialog = builder.create()
        dialog.show() // 다이얼로그 표시
    }

    private fun startCrawling(url: String) {
        FetchNewsTask().execute(url) // 뉴스 크롤링 작업 시작
    }

    private inner class FetchNewsTask : AsyncTask<String, Void, String>() {

        override fun doInBackground(vararg params: String): String? {
            val url = params[0]
            return try {
                val doc: Document = Jsoup.connect(url).get()
                val elements: Elements = doc.select("[id*=cont]")

                val contentBuilder = StringBuilder()
                for (element in elements) {
                    val pTags: Elements = element.select("p")
                    for (pTag in pTags) {
                        var text = pTag.text()

                        // 이메일 제거
                        text = text.replace(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"), "")

                        // 괄호 안의 내용 제거
                        text = text.replace(Regex("\\(.*?\\)"), "")
                        text = text.replace(Regex("\\[.*?\\]"), "")
                        text = text.replace(Regex("\\{.*?\\}"), "")

                        contentBuilder.append(text).append("\n") // 텍스트 추가
                    }
                }
                contentBuilder.toString() // 크롤링 결과 반환
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("News Fetch Error", "Failed to fetch news content")
                null // 크롤링 실패 시 null 반환
            }
        }
        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            if (result != null) {
                inputText.setText(result.toString()) // 크롤링 결과를 입력 텍스트에 설정
            } else {
                Toast.makeText(this@MainActivity, "뉴스 내용을 가져오지 못했습니다.", Toast.LENGTH_SHORT).show() // 실패 메시지 표시
            }
        }
    }

    private fun takePhotoAndSave() {
        val photoFile: File? = try {
            createImageFile() // 이미지 파일 생성
        } catch (ex: IOException) {
            ex.printStackTrace()
            null
        }
        photoFile?.also {
            val photoUri: Uri = FileProvider.getUriForFile(
                this,
                "com.example.myapplication.fileprovider",
                it
            )
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE) // 사진 촬영 인텐트 생성
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            startActivityForResult(takePictureIntent, CAPTURE_IMAGE_REQUEST) // 사진 촬영 인텐트 시작
        }
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date()) // 타임스탬프 생성
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES) // 저장 디렉토리 설정
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath // 현재 사진 경로 설정
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                SELECT_IMAGE_REQUEST -> {
                    val clipData = data?.clipData
                    totalImages = clipData?.itemCount ?: 0
                    processedImages = 0
                    if (clipData != null) {
                        for (i in 0 until clipData.itemCount) {
                            val imageUri = clipData.getItemAt(i).uri
                            cropImage.launch(
                                CropImageContractOptions(
                                    uri = imageUri,
                                    cropImageOptions = CropImageOptions(),
                                )
                            )
                        }
                    } else {
                        data?.data?.let {
                            totalImages = 1
                            cropImage.launch(
                                CropImageContractOptions(
                                    uri = it,
                                    cropImageOptions = CropImageOptions(),
                                )
                            )
                        }
                    }
                }

                CAPTURE_IMAGE_REQUEST -> {
                    val photoFile = File(currentPhotoPath)
                    val photoUri = Uri.fromFile(photoFile)
                    processedImages = 0
                    totalImages = 1
                    cropImage.launch(
                        CropImageContractOptions(
                            uri = photoUri,
                            cropImageOptions = CropImageOptions(),
                        )
                    )
                }
            }
        }
    }

    private val combinedText = StringBuilder() // 텍스트 결합을 위한 StringBuilder

    private fun extractTextAndUploadImage(uri: Uri) {
        val image = InputImage.fromFilePath(this, uri) // 이미지 파일 경로로부터 InputImage 생성
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()) // 텍스트 인식 클라이언트 생성

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                combinedText.append(visionText.text).append("\n") // 인식된 텍스트 추가
                processedImages++
                if (processedImages == totalImages) {
                    runOnUiThread {
                        addTextMessage(combinedText.toString()) // 텍스트 메시지 추가
                        sendDataToServer(combinedText.toString()) // 서버로 데이터 전송
                        combinedText.clear() // 텍스트 초기화
                    }
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                processedImages++
                if (processedImages == totalImages) {
                    runOnUiThread {
                        if (combinedText.isNotEmpty()) {
                            addTextMessage(combinedText.toString()) // 텍스트 메시지 추가
                            sendDataToServer(combinedText.toString()) // 서버로 데이터 전송
                        }
                        combinedText.clear() // 텍스트 초기화
                    }
                }
            }
    }

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRequiredPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE),
            REQUEST_PERMISSIONS
        )
    }

    private fun addMessage(content: String, isSummary: Boolean) {
        val viewType = if (isSummary) VIEW_TYPE_SUMMARY else VIEW_TYPE_TEXT
        messages.add(Message(textContent = content, isSummary = isSummary, viewType = viewType)) // 메시지 리스트에 추가
        messageAdapter.notifyItemInserted(messages.size - 1) // 어댑터에 변경사항 알림
        recyclerView.scrollToPosition(messages.size - 1) // 마지막 메시지로 스크롤
    }

    private fun addImageMessage(imageUri: Uri) {
        messages.add(Message(imageUri = imageUri, isSummary = false, viewType = VIEW_TYPE_IMAGE)) // 이미지 메시지 추가
        messageAdapter.notifyItemInserted(messages.size - 1) // 어댑터에 변경사항 알림
        recyclerView.scrollToPosition(messages.size - 1) // 마지막 메시지로 스크롤
    }

    private fun addTextMessage(text: String) {
        messages.add(Message(textContent = text, isSummary = false, viewType = VIEW_TYPE_TEXT)) // 텍스트 메시지 추가
        messageAdapter.notifyItemInserted(messages.size - 1) // 어댑터에 변경사항 알림
        recyclerView.scrollToPosition(messages.size - 1) // 마지막 메시지로 스크롤
    }

    private fun addSummaryMessage(summary: String) {
        messages.add(Message(textContent = summary, isSummary = true, viewType = VIEW_TYPE_SUMMARY)) // 요약 메시지 추가
        messageAdapter.notifyItemInserted(messages.size - 1) // 어댑터에 변경사항 알림
        recyclerView.scrollToPosition(messages.size - 1) // 마지막 메시지로 스크롤
    }

    private fun sendDataToServer(data: String?) {
        Log.d("NetworkCall", "Sending data to server with data: $data")
        val port = 5001
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val serverAddress = InetAddress.getByName("10.0.2.2")
                val socket = Socket(serverAddress, port)
                val outputStream = socket.getOutputStream()
                val writer = BufferedWriter(OutputStreamWriter(outputStream, "UTF-8"))

                val path = "/api/summary"
                val postData = "data=" + URLEncoder.encode(data, "UTF-8")
                val requestMessage = "POST $path HTTP/1.1\r\n" +
                        "Host: ${serverAddress.hostName}\r\n" +
                        "Content-Type: application/x-www-form-urlencoded\r\n" +
                        "Content-Length: ${postData.length}\r\n" +
                        "\r\n" +
                        postData

                writer.write(requestMessage)
                writer.flush()

                val inputStream = socket.getInputStream()
                val reader = BufferedReader(InputStreamReader(inputStream))
                var line: String?
                val response = StringBuilder()
                var contentStarted = false

                while (reader.readLine().also { line = it } != null) {
                    if (line.isNullOrBlank() && !contentStarted) {
                        contentStarted = true
                    } else if (contentStarted) {
                        response.append(line).append('\n')
                    }
                }

                withContext(Dispatchers.Main) {
                    addSummaryMessage(response.toString().trim()) // 서버 응답을 요약 메시지로 추가
                }

                socket.close() // 소켓 닫기
            } catch (e: IOException) {
                Log.e("NetworkCall", "Error in network call", e)
                e.printStackTrace()
            }
        }
    }
}
