package com.yhsif.notifbot

import android.content.Context
import android.net.Uri
import android.os.Handler
import com.google.android.gms.net.CronetProviderInstaller
import com.google.android.gms.tasks.Tasks
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UploadDataProviders
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import org.chromium.net.impl.JavaCronetProvider
import java.nio.ByteBuffer
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class HttpSender(
  val onSuccess: () -> Unit,
  val onFailure: () -> Unit,
  val onNetFail: () -> Unit
) : UrlRequest.Callback() {

  companion object {
    private const val KEY_LABEL = "label"
    private const val KEY_MSG = "msg"

    private val executor: Executor = Executors.newCachedThreadPool()

    private lateinit var context: Context
    private var uiHandler: Handler = Handler()

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
      onNetFail: () -> Unit
    ) {
      initEngine(ctx)
      executor.execute(
        Runnable() {
          val body = Uri.Builder()
            .appendQueryParameter(KEY_LABEL, label)
            .appendQueryParameter(KEY_MSG, msg)
            .build()
            .getEncodedQuery()!!

          val reqBuilder = engine.newUrlRequestBuilder(
            url,
            HttpSender(onSuccess, onFailure, onNetFail),
            executor
          )
          reqBuilder.setHttpMethod("POST")
          reqBuilder.addHeader("Content-Type", "application/x-www-form-urlencoded")
          reqBuilder.setUploadDataProvider(
            UploadDataProviders.create(body.toByteArray(), 0, body.length),
            executor
          )
          reqBuilder.build().start()
        }
      )
    }

    fun checkUrl(url: String, onFailure: () -> Unit) {
      executor.execute(
        Runnable() {
          engine.newUrlRequestBuilder(
            url,
            HttpSender({}, onFailure, {}),
            executor
          ).build().start()
        }
      )
    }

    private fun runCallbackOnUiThread(callback: () -> Unit) {
      uiHandler.post(
        Runnable() {
          callback()
        }
      )
    }
  }

  override fun onRedirectReceived(req: UrlRequest, info: UrlResponseInfo?, newUrl: String) {
    // Never follow redirects, but treat it as success
    req.cancel()
    runCallbackOnUiThread(onSuccess)
  }

  override fun onResponseStarted(req: UrlRequest, info: UrlResponseInfo?) {
    val code = info?.getHttpStatusCode()
    if (code != null && code >= 200 && code < 400) {
      runCallbackOnUiThread(onSuccess)
    } else {
      runCallbackOnUiThread(onFailure)
    }
    req.cancel()
  }

  override fun onFailed(req: UrlRequest, info: UrlResponseInfo?, e: CronetException) {
    runCallbackOnUiThread(onNetFail)
  }

  // We don't care about the following functions
  override fun onReadCompleted(req: UrlRequest, info: UrlResponseInfo?, buf: ByteBuffer) {}

  override fun onSucceeded(req: UrlRequest, info: UrlResponseInfo?) {}

  override fun onCanceled(req: UrlRequest, info: UrlResponseInfo?) {}
}
