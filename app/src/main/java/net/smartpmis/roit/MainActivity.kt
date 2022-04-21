package net.smartpmis.roit

import net.smartpmis.roit.databinding.ActivityMainBinding
import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.webkit.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import java.io.File
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.graphics.Bitmap
import android.net.*
import android.os.*
import android.text.TextUtils
import android.webkit.PermissionRequest
import java.lang.Exception
import java.net.URLDecoder
import android.webkit.WebSettings

import android.os.Build
import android.webkit.CookieSyncManager
import android.widget.Toast
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

import java.net.URL


//--------------------------------------------------------------------------------//
//     #A-1. 당겨서 리프레시, Backward.
//     #A-2-2. 자바스크립트 windows.open
//     #A-3. 오프라인 처리.
//     #A-4. Microphone 처리
//     #A-5. Camera/Gallery  //  추가수정 요!!  Use the MediaStore APIs directly and render your own image selector, or

//     #B-1. 상용/개발
//     #C-1. FCM푸시  - 토큰
//     #C-2. 포그라운드 푸시  MyFirebaseMessagingService.kt 파일에 있음.
//     #C-3. URL 변경 감지.
//     #C-4. 쿠키 접근
//     #C-5. Get 전송.

//     #Z-0. 웹뷰 기본설정.
//     #Z-1. 기기 권한 설정(카메라/음성/파일 접근 등)
//     #Z-2. 웹 콘솔창 디버그용.

//     #Z-99. 다운로드 리스너.

//--------------------------------------------------------------------------------//

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding  //등록하면 별도의 id 설정 없이 가능하다.
    val TAG = "vm11"




    //     #B-1. 상용/개발
    var Main_URL = when{
        BuildConfig.IsDev -> "http://roit.smartpmis.net"//로이 스마트조인 URL
//        BuildConfig.IsDev -> "http://roit.smartjoin.net"//로이 스마트조인 URL
//        BuildConfig.IsDev -> "https://gymcoding.github.io/vuetify-admin-template/#/"//vuetify 시연 URL
//        BuildConfig.IsDev -> "https://roitech1.github.io/fastvue/index.html"
//        BuildConfig.IsDev -> "https://roitech1.github.io/Record_page.html"//녹음 테스트용 URL.
    else-> "http://roit.smartpmis.net"}

    var ThisDeviceToken:String = ""
    var SaveTokenOnce:Boolean = false

    val AfterLogin_URL_STR="main"//roit 기준
//    val AfterLogin_URL_STR="Service.do"//coremsys 기준
    val Cookie_UserID_STR = "UserID="
    val Cookie_UserNAME_STR = "UserName="


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        val window = window//statusbar 사라지게. apple처럼 secure zone 설정할 수 없어 주석처리.
//        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        requestPermissions()
        GetFCMToken()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)





//     #Z-0. 웹뷰 기본설정.
        binding.webview.run { //객체 생성 후 설정.
            settings.javaScriptEnabled = true
            settings.setSupportMultipleWindows(true) // 새창띄우기 허용여부
            settings.javaScriptCanOpenWindowsAutomatically = true // 자바스크립트 새창뛰우기 (멀티뷰) 허용여부
            settings.loadWithOverviewMode = true //메타태크 허용여부
            settings.useWideViewPort = true //화면 사이즈 맞추기 허용여부
            settings.setSupportZoom(true) //화면 줌 허용여부
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.setDomStorageEnabled(true)
            settings.databaseEnabled = true
            settings.allowContentAccess=true
            settings.allowFileAccess=true
            settings.defaultTextEncodingName = "UTF-8"
            settings.setAppCacheEnabled(true)
            settings.builtInZoomControls = true //화면 확대 축소 허용여부
            webViewClient = WebViewClientClass()
            webChromeClient = WebChromeClientClass()
            viewTreeObserver.addOnScrollChangedListener { binding.pullToRefresh.isEnabled = (binding.webview.scrollY == 0) } //스크롤뷰
        }
//  ------------------------------------ #Z-0. 웹뷰 기본설정. 끝 -----------------------------------------  //


        binding.webview.loadUrl(Main_URL)

//     #Z-99. 다운로드 리스너.
        binding.webview.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            val request = (DownloadManager.Request(Uri.parse(url))).apply {
                val  contentDisposition = URLDecoder.decode(contentDisposition,"UTF-8");
                setMimeType(mimeType)
                val cookies = CookieManager.getInstance().getCookie(url)//쿠기를 넣어줘야, 팝업 다운로드가 가능하다.
                addRequestHeader("cookie", cookies)
                addRequestHeader("User-Agent", userAgent)
                setDescription("Downloading file...")
                setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType))
                allowScanningByMediaScanner()
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    URLUtil.guessFileName(url, contentDisposition, mimeType))
            }
            val dm = this.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(applicationContext, "Downloading File", Toast.LENGTH_LONG).show()
        })




//     #A-1. 당겨서 리프레시, Backward.
        binding.pullToRefresh.setOnRefreshListener {
            binding.webview.reload()
            binding.pullToRefresh.isRefreshing = false
        }
        binding.webview.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && binding.webview.canGoBack()) {
                binding.webview.goBack()
                true
            } else {
                false
            }
        }
        // ---------------------------------------------------//

    }
    public fun update_url(url: String)
    {
        binding.webview.loadUrl(url)
    }

    inner class WebChromeClientClass : WebChromeClient() {

        //     #Z-2. 웹 콘솔창 디버그용.
        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            consoleMessage?.apply {
                Log.d(TAG,"web)  "+"${message()} -- From line ${lineNumber()} of ${sourceId()}")
            }
            return true
        }

        //     #A-2-2. 자바스크립트 windows.open
        override fun onCreateWindow(view: WebView?,isDialog: Boolean,isUserGesture: Boolean,resultMsg: Message?
        ): Boolean {

        //새창의 경로중 download가 담겨있으면, 다운로드를 발생시킨다.
        val mWebViewPop = WebView(this@MainActivity).apply {//apply 선언과 동시에 초기화할 때 사용한다.
                settings.javaScriptEnabled = true
                settings.javaScriptCanOpenWindowsAutomatically = true // 자바스크립트 새창뛰우기 (멀티뷰) 허용여부
                settings.loadWithOverviewMode = true //메타태크 허용여부
                settings.useWideViewPort = true //화면 사이즈 맞추기 허용여부
                settings.setSupportZoom(true) //화면 줌 허용여부
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.setDomStorageEnabled(true)
                settings.databaseEnabled = true
                settings.allowContentAccess=true
                settings.allowFileAccess=true
                settings.defaultTextEncodingName = "UTF-8"
            }

            mWebViewPop.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                Log.d("$TAG[]", "URL다운로드 시작.: " + url)
                val request = (DownloadManager.Request(Uri.parse(url))).apply {
//                    setMimeType(mimeType)
                    val  contentDisposition = URLDecoder.decode(contentDisposition,"UTF-8");
                    Log.d(TAG,contentDisposition)
                    //파일 확장자 추출.-> mimType을 Coremsys 서버에서 지정해주지 못해 앱에서 적용해준다.
                    val j = contentDisposition.lastIndexOf(".")
                    val extn = contentDisposition.substring(j + 1, contentDisposition.length - 1)
                    val Roi_mimeType = "application/$extn"
                    setMimeType(Roi_mimeType)
                    val cookies = CookieManager.getInstance().getCookie(url)//쿠기를 넣어줘야, 팝업 다운로드가 가능하다.

                    addRequestHeader("cookie", cookies)
                    addRequestHeader("User-Agent", userAgent)
                    setDescription("Downloading file...")
                    setTitle(URLUtil.guessFileName(url, contentDisposition, Roi_mimeType))
                    allowScanningByMediaScanner()
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        URLUtil.guessFileName(url, contentDisposition, Roi_mimeType))
                }
                val dm = this@MainActivity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(applicationContext, "Downloading File", Toast.LENGTH_LONG).show()
            })

            mWebViewPop.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?,request: WebResourceRequest?): Boolean {

                    var url = view!!.url.toString()
                    if(!url.lowercase().contains("download") && (url.contains("http://") || url.contains("https://")))
                    {//다운로드용 팝업이 아닌데 경로를 지정하는 경우 크롬에서 새 창을 열어준다.
                        Intent(Intent.ACTION_VIEW).let {
                            it.setData(Uri.parse(url))
                            startActivity(it)
                        }
                        return true;
                    }
                    else
                        return super.shouldOverrideUrlLoading(view, request)
                }
            }
            mWebViewPop.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.apply {
                        Log.d(TAG,"inner_Web)  " + "${message()} -- From line ${lineNumber()} of ${sourceId()}")
                    }
                    return true
                }
            }

            (resultMsg?.obj as WebView.WebViewTransport).webView = mWebViewPop //새 창등록.?
            resultMsg.sendToTarget()

            return true
        }


        //     #A-4. Microphone 처리   - 웹의 미디어 사용 요청 승인.
        override fun onPermissionRequest(request: PermissionRequest) {
            request.grant(request.resources)
        }

        //     #A-5. Camera/Gallery
        override fun onShowFileChooser(webView: WebView?,filePathCallback: ValueCallback<Array<Uri>>?,fileChooserParams: FileChooserParams?): Boolean {

            var isCapture = fileChooserParams?.isCaptureEnabled ?: false//
            selectImage(filePathCallback, isCapture)
            return true
        }
    }

    inner class WebViewClientClass : WebViewClient() {
//     #C-3. URL 변경 감지.
        override fun shouldOverrideUrlLoading(view: WebView?,url: String): Boolean {

            Log.d(TAG, url)
            if(url.contains("logout") || url.contains("login"))//로그아웃 때 또는 index. 또는 index가 없을때.

                SaveTokenOnce=false
            if(url.contains(AfterLogin_URL_STR) && !SaveTokenOnce)//main 창이 불러질때.
            {
                var cookies= CookieManager.getInstance().getCookie(url).split("; ")
                var UserID = (cookies.first() {x -> x.contains(Cookie_UserID_STR)}).replace(Cookie_UserID_STR,"")
                var UserName = (cookies.first() {x -> x.contains(Cookie_UserNAME_STR)}).replace(Cookie_UserNAME_STR,"")
                UserName = URLDecoder.decode(UserName, "EUC-KR");
                Log.d(TAG,"쿠키 정보: "+UserID+UserName)
                val user = hashMapOf(
                    "UserName" to UserName,
                    "FCMToken" to ThisDeviceToken
                )
                db.collection("users").document(UserID).set(user)

                val ex_ID = getPreferences(MODE_PRIVATE).getString("Ex_User_ID", "").toString()
                if(ex_ID!=UserID && ex_ID!="")//이전 ID가 있으면, 토큰 삭제.
                {
                    db.collection("users").document(ex_ID).delete()
                    Log.d(TAG, ex_ID+"] 토큰 삭제.")
                }
                //로컬에 현재 ID를 이전ID로 저장.
                val editor = getPreferences(MODE_PRIVATE).edit()
                editor.putString("Ex_User_ID", UserID)
                editor.commit()

                SaveTokenOnce=true// 나중에는 response를 기준으로 처리해야함.
            }
            return super.shouldOverrideUrlLoading(view, url)

        }
//        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
//            Glide.with(this@MainActivity).load(R.drawable.img_loading).into(loader)
//        }
        override fun onPageFinished(view: WebView?, url: String?) {
//            loader.setImageBitmap(null)
            Log.d(TAG,"쿠키flush")
            CookieManager.getInstance().flush();
        }
    }


    val db = Firebase.firestore
    //     #C-1. FCM푸시  - 토큰
    fun GetFCMToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (task.isSuccessful)
            {
                ThisDeviceToken = task.result.toString()
                Log.d(TAG,"토큰: "+ThisDeviceToken)
            }
            else
                return@OnCompleteListener
        })
    }

    //     #C-4. 쿠키 접근
//    fun GetUserId_from_KCC_Cookie(url: String): String
//    {
//        var cookies= CookieManager.getInstance().getCookie(url).split("; ")
//        var KCC_COOKIE_CODE:String = "write="
//        var userid = (cookies.first() {x -> x.contains(KCC_COOKIE_CODE)}).replace(KCC_COOKIE_CODE,"") // null이면, 서버문제.
//        return userid
//    }
    //     #C-5. Get 전송.
    fun Get_Request(url: String) {
        val queue = Volley.newRequestQueue(this)
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            Response.Listener<String> { response ->Log.d(TAG, "Response is: ${response}")},
            Response.ErrorListener { Log.d(TAG, "That didn't work!") })
        queue.add(stringRequest)
    }

    //  ------------------------------------ #A-5. Camera/Gallery ----------------------------------------  //
    var cameraImagePath=""
    val REQ_CAMERA = 501
    val REQ_SELECT_IMAGE = 2001
    var filePathCallback: ValueCallback<Array<Uri>>? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQ_CAMERA -> {
                if (resultCode == Activity.RESULT_OK) {
                    filePathCallback?.let {
                        var imageData = data
                        if (imageData == null) {
                            imageData = Intent()
                            imageData?.data = Uri.fromFile(File(cameraImagePath))
                        }
                        if (imageData?.data == null) {
                            imageData?.data = Uri.fromFile(File(cameraImagePath))
                        }
                        it.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, imageData))
                        filePathCallback = null
                    }
                }else{
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = null
                }
            }

            REQ_SELECT_IMAGE -> {
                if (resultCode == Activity.RESULT_OK) {
                    filePathCallback?.let {
                        var imageData = data
                        if (imageData == null) {
                            imageData = Intent()
                            imageData?.data = Uri.fromFile(File(cameraImagePath))
                        }
                        if (imageData?.data == null) {//다중파일 선택시
                            //@@ 더 줄여보기..  +  startActivityForResult변경하기.
                            val clipData = data!!.clipData
                            if (clipData != null) {
                                val uris = arrayOfNulls<Uri>(clipData.itemCount)
                                for (i in 0 until clipData.itemCount) {
                                    val item = clipData.getItemAt(i)
                                    val uri = item.uri
                                    uris[i] = uri
                                }
                                it.onReceiveValue(uris as Array<Uri>)
                                filePathCallback = null
                            }
//                            imageData?.data = Uri.fromFile(File(cameraImagePath))
                        }
                        else//단일파일 선택시
                        {
                            it.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, imageData))
                            filePathCallback = null
                        }

                    }
                }else{
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = null
                }
            }
        }
    }
    private fun createImageFile(): File { // 사진이 저장될 폴더 있는지 체크
        var imageName = "plantdiary_${System.currentTimeMillis()}"
        var file: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(imageName,".jpg", file).apply {
            cameraImagePath = absolutePath
        }
    }
    fun selectImage(filePathCallback: ValueCallback<Array<Uri>>?, IsCapture : Boolean) {
        this.filePathCallback?.onReceiveValue(null)
        this.filePathCallback = filePathCallback

        if(IsCapture)
        {
            var cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            cameraIntent.resolveActivity(packageManager)?.let {
                createImageFile()?.let {
                    var photoUri = FileProvider.getUriForFile(this,
                        BuildConfig.APPLICATION_ID + ".provider",it)
                    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                }
            }
            startActivityForResult(cameraIntent, REQ_CAMERA)
        }
        else {

//            var intent = Intent(Intent.ACTION_PICK).apply {
            var intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = MediaStore.Images.Media.CONTENT_TYPE//미디어는 동영상도 포함됨?
//                type = MediaStore.Images.Media.ALBUM//화면 보기 종류.
                data = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
//                setType("image/*")//이거하면 이미지만 선택됨.
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            startActivityForResult(intent, REQ_SELECT_IMAGE)
        }
    }
    //  ----------------------------------  #A-5. Camera/Gallery End -------------------------------------  //


    //
    //  -----------------------------  #Z-1. 기기 권한 설정(카메라/음성/파일 접근 등) ---------------------------  //
    private val multiplePermissionsCode = 100
    fun requestPermissions(): Boolean {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
            return true
        }

        val permissions: Array<String> = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE)

        ActivityCompat.requestPermissions(this, permissions, multiplePermissionsCode)
        return false
    }
    override fun onRequestPermissionsResult(requestCode: Int,permissions: Array<out String>,grantResults: IntArray)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode){
            multiplePermissionsCode -> {
                if (grantResults.isNotEmpty()){
                    var isAllGranted = true
                    // 요청한 권한 허용/거부 상태 한번에 체크
                    for (grant in grantResults) {
                        if (grant != PackageManager.PERMISSION_GRANTED) {
                            isAllGranted = false
                            break;
                        }
                    }

                    // 요청한 권한을 모두 허용했음.
                    if (isAllGranted) {
                        // 다음 step으로 ~
                    }
                    // 허용하지 않은 권한이 있음. 필수권한/선택권한 여부에 따라서 별도 처리를 해주어야 함.
                    else {
                        if(!ActivityCompat.shouldShowRequestPermissionRationale(this,
                                Manifest.permission.READ_EXTERNAL_STORAGE)
                            || !ActivityCompat.shouldShowRequestPermissionRationale(this,
                                Manifest.permission.CAMERA)
                            || !ActivityCompat.shouldShowRequestPermissionRationale(this,
                                Manifest.permission.RECORD_AUDIO)
                        ){
                            // 다시 묻지 않기 체크하면서 권한 거부 되었음.
                        } else {
                            // 접근 권한 거부하였음.
                        }
                    }
                }
            }
        }
    }

    //  -----------------------------  #Z-1. 기기 권한 설정(카메라/음성/파일 접근 등) 끝 ------------------------  //



    //  ------------------------------------ #A-3. 오프라인 처리 -------------------------------------------  //
    val handler: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            if(msg.obj.toString().equals("Online"))
            {
                binding.viewOffline.visibility=View.GONE
                binding.webview.visibility = View.VISIBLE
            }
            else
            {
                binding.viewOffline.visibility=View.VISIBLE
                binding.webview.visibility = View.GONE
            }
        }
    }


    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val msg = handler.obtainMessage()
            msg.obj="Online"
            handler.sendMessage(msg)
        }
        override fun onLost(network: Network) {
            val msg = handler.obtainMessage()
            msg.obj="Offline"
            handler.sendMessage(msg)
            super.onLost(network)
        }
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val wifiNetworkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        cm.registerNetworkCallback(wifiNetworkRequest, networkCallback)
    }

    private fun unregisterNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java)
        cm.unregisterNetworkCallback(networkCallback)
    }

    override fun onResume() {

        var push_url:String=""
        if(intent.hasExtra("push_url"))
            push_url = intent.getStringExtra("push_url")+""
        Log.d(TAG, "onResume")
        Log.d(TAG,"push_url: "+push_url)
        binding.webview.loadUrl("http://"+push_url)

        super.onResume()
        registerNetworkCallback()
    }

    override fun onStop() {
        super.onStop()
//        unregisterNetworkCallback()// 팝업창 등으로 인해 외부에서 offline이 될 경우 404에러 페이지가 나타날 수 있다.
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
        unregisterNetworkCallback()
    }

    //  ------------------------------------ #A-3. 오프라인 처리 끝 -----------------------------------------  //

}