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
  val CodeNetFail: Int = -1

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
        val res = HttpSender.client.newCall(req.asInstanceOf[Request]).execute()
        if (res == null) {
          return HttpSender.CodeNetFail.asInstanceOf[AnyRef]
        }
        val code = res.code().asInstanceOf[AnyRef]
        res.close()
        return code
      } catch {
        case _: IOException => {
          return HttpSender.CodeNetFail.asInstanceOf[AnyRef]
        }
      }
    }
    // Empty reqs
    return 404.asInstanceOf[AnyRef]
  }

  override def onPostExecute(c: AnyRef): Unit = {
    val code = c.asInstanceOf[Int]
    if (code == HttpSender.CodeNetFail) {
      onNetFail()
      return
    }
    if (code >= 200 && code < 400) {
      onSuccess()
    } else {
      onFailure()
    }
  }
}
