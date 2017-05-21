package main

import (
	"fmt"
	"net/http"

	"google.golang.org/appengine"
)

const (
	errNoToken = "no telebot token"

	globalURLPrefix = "https://notification-bot.appspot.com"
	webhookPrefix   = "/w/"
	clientPrefix    = "/c/"
)

func main() {
	http.HandleFunc("/", rootHandler)
	http.HandleFunc(webhookPrefix, webhookHandler)
	http.HandleFunc(clientPrefix, clientHandler)
	http.HandleFunc("/_ah/health", healthCheckHandler)
	http.HandleFunc("/_ah/start", initHandler)
	appengine.Main()
}

func initHandler(w http.ResponseWriter, r *http.Request) {
	if InitBot(r); botToken == nil {
		http.Error(w, errNoToken, 500)
		return
	}
	botToken.SetWebhook(r)
	http.NotFound(w, r)
}

func healthCheckHandler(w http.ResponseWriter, r *http.Request) {
	fmt.Fprint(w, "healthy")
}

func webhookHandler(w http.ResponseWriter, r *http.Request) {
	if botToken.ValidateWebhookURL(r) {
		// TODO
		w.WriteHeader(http.StatusOK)
		return
	}
	http.NotFound(w, r)
}

func clientHandler(w http.ResponseWriter, r *http.Request) {
	// TODO
	http.NotFound(w, r)
}

func rootHandler(w http.ResponseWriter, r *http.Request) {
	http.NotFound(w, r)
}
