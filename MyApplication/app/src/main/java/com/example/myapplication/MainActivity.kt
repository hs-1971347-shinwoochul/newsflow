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
    private lateinit var inputText: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private val messages = mutableListOf<Message>()

    private val SELECT_IMAGE_REQUEST = 1
    private val CAPTURE_IMAGE_REQUEST = 2
    private val REQUEST_PERMISSIONS = 100

    private var totalImages = 0
    private var processedImages = 0

    private var currentPhotoPath: String = ""

    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val croppedUri = result.uriContent
            if (croppedUri != null) {
                addImageMessage(croppedUri)
                extractTextAndUploadImage(croppedUri) // 이미지 자르기 후 OCR 시작
            }
        } else {
            Log.e("Crop", "Error cropping image: ${result.error}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inputText = findViewById(R.id.inputText)
        recyclerView = findViewById(R.id.recyclerView)
        val summarizeButton = findViewById<Button>(R.id.summarizeButton)
        val galleryButton = findViewById<ImageButton>(R.id.galleryButton)
        val captureButton = findViewById<ImageButton>(R.id.photoButton)
        val crawlButton = findViewById<ImageButton>(R.id.UrlButton)

        messageAdapter = MessageAdapter(messages)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = messageAdapter

        val callback = MessageTouchHelperCallback(messageAdapter)
        val itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(recyclerView)

        summarizeButton.setOnClickListener {
            val inputMessage = inputText.text.toString()
            if (inputMessage.isNotEmpty()) {
                addMessage(inputMessage, isSummary = false)
                sendDataToServer(inputMessage)
                inputText.text.clear()
            }
        }

        galleryButton.setOnClickListener {
            println("사진 고르기")
            val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            startActivityForResult(galleryIntent, SELECT_IMAGE_REQUEST)
        }

        captureButton.setOnClickListener {
            println("사진 찍기")
            takePhotoAndSave()
        }

        crawlButton.setOnClickListener {
            showUrlInputDialog()
        }

        if (!hasRequiredPermissions()) {
            requestRequiredPermissions()
        }
    }

    private fun showUrlInputDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("크롤링할 URL 입력")

        val inputView = layoutInflater.inflate(R.layout.dialog_url_input, null)
        val urlEditText = inputView.findViewById<EditText>(R.id.urlEditText)
        builder.setView(inputView)

        builder.setPositiveButton("OK") { dialog, which ->
            val url = urlEditText.text.toString()
            if (url.isNotEmpty()) {
                startCrawling(url)
            } else {
                Toast.makeText(this@MainActivity, "URL을 입력하세요.", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("취소") { dialog, which ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }

    private fun startCrawling(url: String) {
        FetchNewsTask().execute(url)
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

                        contentBuilder.append(text).append("\n")
                    }
                }
                contentBuilder.toString()
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("News Fetch Error", "Failed to fetch news content")
                null
            }
        }
        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            if (result != null) {
                // 크롤링한 데이터를 inputText에 설정
                inputText.setText(result.toString())
            } else {
                Toast.makeText(this@MainActivity, "뉴스 내용을 가져오지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun takePhotoAndSave() {
        val photoFile: File? = try {
            createImageFile()
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
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            startActivityForResult(takePictureIntent, CAPTURE_IMAGE_REQUEST)
        }
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
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

    private val combinedText = StringBuilder()

    private fun extractTextAndUploadImage(uri: Uri) {
        val image = InputImage.fromFilePath(this, uri)
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                combinedText.append(visionText.text).append("\n")
                processedImages++
                println("pro" + processedImages + "total" + totalImages)
                if (processedImages == totalImages) {
                    runOnUiThread {
                        addTextMessage(combinedText.toString())
                        sendDataToServer(combinedText.toString())
                        combinedText.clear()  // 성공 후 텍스트 초기화
                    }
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                processedImages++
                if (processedImages == totalImages) {
                    runOnUiThread {
                        if (combinedText.isNotEmpty()) {
                            addTextMessage(combinedText.toString())
                            sendDataToServer(combinedText.toString())
                        }
                        combinedText.clear()  // 실패 후에도 텍스트 초기화
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
        messages.add(Message(textContent = content, isSummary = isSummary, viewType = viewType))
        messageAdapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)
    }
    private fun addImageMessage(imageUri: Uri) {
        messages.add(Message(imageUri = imageUri, isSummary = false, viewType = VIEW_TYPE_IMAGE))
        messageAdapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)
    }
    private fun addTextMessage(text: String) {
        messages.add(Message(textContent = text, isSummary = false, viewType = VIEW_TYPE_TEXT))
        messageAdapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)
    }
    private fun addSummaryMessage(summary: String) {
        messages.add(Message(textContent = summary, isSummary = true, viewType = VIEW_TYPE_SUMMARY))
        messageAdapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)
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
                    addSummaryMessage(response.toString().trim())
                }

                socket.close()
            } catch (e: IOException) {
                Log.e("NetworkCall", "Error in network call", e)
                e.printStackTrace()
            }
        }
    }
}
