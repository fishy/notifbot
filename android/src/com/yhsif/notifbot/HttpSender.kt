package com.yhsif.notifbot

import android.content.Context
import android.net.Uri
import android.os.AsyncTask
import android.os.Handler
import android.util.Log

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
    private const val TAG = "NOTIFBOT_CRONET_DEBUG"

    private const val KEY_LABEL = "label"
    private const val KEY_MSG = "msg"

    private val executor: Executor = Executors.newCachedThreadPool()

    private lateinit var context: Context
    private lateinit var uiHandler: Handler

    private val engine: CronetEngine by lazy {
      lateinit var builder: CronetEngine.Builder
      try {
        Tasks.await(CronetProviderInstaller.installProvider(context))
        builder = CronetEngine.Builder(context)
      } catch (e: ExecutionException) {
        Log.d(
          TAG,
          "Failed to init CronetEngine from gms",
          if (e.cause != null) { e.cause } else { e }
        )
        builder = JavaCronetProvider(context).createBuilder()
      }
      builder.enableHttp2(true).enableQuic(true).build()
    }

    fun initEngine(ctx: Context) {
      if (!::uiHandler.isInitialized) {
        context = ctx
        uiHandler = Handler()
      }
    }

    fun send(
      url: String,
      label: String,
      msg: String,
      onSuccess: () -> Unit,
      onFailure: () -> Unit,
      onNetFail: () -> Unit
    ) {
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
          Log.d(TAG, "started")
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
          Log.d(TAG, "started")
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
    Log.d(TAG, "onRedirectReceived: new url: $newUrl")
    // Never follow redirects, but treat it as success
    req.cancel()
    runCallbackOnUiThread(onSuccess)
  }

  override fun onResponseStarted(req: UrlRequest, info: UrlResponseInfo?) {
    val code = info?.getHttpStatusCode()
    Log.d(TAG, "onResponseStarted: code: $code")
    if (code != null && code >= 200 && code < 400) {
      runCallbackOnUiThread(onSuccess)
    } else {
      runCallbackOnUiThread(onFailure)
    }
    req.cancel()
  }

  override fun onFailed(req: UrlRequest, info: UrlResponseInfo?, e: CronetException) {
    Log.d(TAG, "onFailed", e)
    runCallbackOnUiThread(onNetFail)
  }

  // We don't care about the following functions
  override fun onReadCompleted(req: UrlRequest, info: UrlResponseInfo?, buf: ByteBuffer) {
    Log.d(TAG, "onReadCompleted")
  }

  override fun onSucceeded(req: UrlRequest, info: UrlResponseInfo?) {
    Log.d(TAG, "onSucceeded")
  }

  override fun onCanceled(req: UrlRequest, info: UrlResponseInfo?) {
    Log.d(TAG, "onCanceled")
  }
}
