package com.yhsif.notifbot

import android.os.AsyncTask

import java.io.IOException

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

object HttpSender {
  val KeyLabel = "label"
  val KeyMsg = "msg"

  private val client =
    new OkHttpClient.Builder().followRedirects(false).build()

  def send(
      url: String,
      label: String,
      msg: String,
      onSuccess: () => Unit,
      onFailure: () => Unit,
      onNetFail: () => Unit): Unit = {
    val body =
      new FormBody.Builder().add(KeyLabel, label).add(KeyMsg, msg).build()
    val request = new Request.Builder().url(url).post(body).build()

    new HttpSender(onSuccess, onFailure, onNetFail).execute(request)
  }

  def checkUrl(url: String, onFailure: () => Unit): Unit = {
    val request = new Request.Builder().url(url).get().build()
    new HttpSender(() => {}, onFailure, () => {}).execute(request)
  }
}

class HttpSender(
    val onSuccess: () => Unit,
    val onFailure: () => Unit,
    val onNetFail: () => Unit)
    extends AsyncTask[AnyRef, AnyRef, AnyRef] {

  override def doInBackground(reqs: AnyRef*): AnyRef = {
    reqs.foreach { req =>
      // Only handle the first req
      try {
        return HttpSender.client.newCall(req.asInstanceOf[Request]).execute()
      } catch {
        case _: IOException => {
          return null
        }
      }
    }
    // Empty reqs
    return new Response.Builder().code(404).build()
  }

  override def onPostExecute(response: AnyRef): Unit = {
    if (response == null) {
      onNetFail()
      return
    }
    val res = response.asInstanceOf[Response]
    val code = res.code()
    res.close()
    if (code >= 200 && code < 400) {
      onSuccess()
    } else {
      onFailure()
    }
  }
}
