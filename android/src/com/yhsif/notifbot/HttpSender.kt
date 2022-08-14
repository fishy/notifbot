package com.yhsif.notifbot

import android.content.Context
import android.net.Uri
import com.google.android.gms.net.CronetProviderInstaller
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UploadDataProviders
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import org.chromium.net.impl.JavaCronetProvider
import java.nio.ByteBuffer
import java.util.concurrent.ExecutionException

class HttpSender(
  val onSuccess: () -> Unit,
  val onFailure: () -> Unit,
  val onNetFail: () -> Unit,
) : UrlRequest.Callback() {

  companion object {
    private const val KEY_LABEL = "label"
    private const val KEY_MSG = "msg"

    private lateinit var context: Context

    private val engine: CronetEngine by lazy {
      lateinit var builder: CronetEngine.Builder
      try {
        Tasks.await(CronetProviderInstaller.installProvider(context))
        builder = CronetEngine.Builder(context)
      } catch (e: ExecutionException) {
        builder = JavaCronetProvider(context).createBuilder()
      }
      builder.enableHttp2(true).enableQuic(true).build()
    }

    fun initEngine(ctx: Context) {
      if (!::context.isInitialized) {
        context = ctx
      }
    }

    fun send(
      ctx: Context,
      url: String,
      label: String,
      msg: String,
      onSuccess: () -> Unit,
      onFailure: () -> Unit,
      onNetFail: () -> Unit,
    ) {
      initEngine(ctx)
      GlobalScope.launch(Dispatchers.IO) {
        val body = Uri.Builder()
          .appendQueryParameter(KEY_LABEL, label)
          .appendQueryParameter(KEY_MSG, msg)
          .build()
          .getEncodedQuery()!!

        val reqBuilder = engine.newUrlRequestBuilder(
          url,
          HttpSender(onSuccess, onFailure, onNetFail),
          Dispatchers.IO.asExecutor(),
        )
        reqBuilder.setHttpMethod("POST")
        reqBuilder.addHeader("Content-Type", "application/x-www-form-urlencoded")
        reqBuilder.setUploadDataProvider(
          UploadDataProviders.create(body.toByteArray(), 0, body.length),
          Dispatchers.IO.asExecutor(),
        )
        reqBuilder.build().start()
      }
    }

    fun checkUrl(url: String, onSuccess: () -> Unit, onFailure: () -> Unit) {
      GlobalScope.launch(Dispatchers.IO) {
        engine.newUrlRequestBuilder(
          url,
          HttpSender(
            onSuccess,
            onFailure,
            {},
          ),
          Dispatchers.IO.asExecutor(),
        ).build().start()
      }
    }
  }

  override fun onRedirectReceived(req: UrlRequest, info: UrlResponseInfo?, newUrl: String) {
    // Never follow redirects, but treat it as success
    req.cancel()
    GlobalScope.launch(Dispatchers.Main) {
      onSuccess()
    }
  }

  override fun onResponseStarted(req: UrlRequest, info: UrlResponseInfo?) {
    val code = info?.getHttpStatusCode()
    GlobalScope.launch(Dispatchers.Main) {
      if (code != null && code >= 200 && code < 400) {
        onSuccess()
      } else {
        onFailure()
      }
      withContext(Dispatchers.IO) {
        req.cancel()
      }
    }
  }

  override fun onFailed(req: UrlRequest, info: UrlResponseInfo?, e: CronetException) {
    GlobalScope.launch(Dispatchers.Main) {
      onNetFail()
    }
  }

  // We don't care about the following functions
  override fun onReadCompleted(req: UrlRequest, info: UrlResponseInfo?, buf: ByteBuffer) {}

  override fun onSucceeded(req: UrlRequest, info: UrlResponseInfo?) {}

  override fun onCanceled(req: UrlRequest, info: UrlResponseInfo?) {}
}
