package com.yhsif.notifbot

import android.os.AsyncTask

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

object HttpSender {
  val KeyLabel = "label"
  val KeyMsg = "msg"

  private val client = new OkHttpClient()

  def send(
      url: String,
      label: String,
      msg: String,
      onSuccess: () => Unit,
      onFailure: () => Unit): Unit = {
    val body = new FormBody.Builder()
      .add(KeyLabel, label)
      .add(KeyMsg, msg)
      .build()
    val request = new Request.Builder()
      .url(url)
      .post(body)
      .build()

    new AsyncTask[AnyRef, AnyRef, AnyRef]() {
      override def doInBackground(unused: AnyRef*): AnyRef = {
        client.newCall(request).execute()
      }

      override def onPostExecute(response: AnyRef): Unit = {
        val code = response.asInstanceOf[Response].code()
        if (code >= 200 && code < 400) {
          onSuccess()
        } else {
          onFailure()
        }
      }
    }.execute()
  }
}
