package main

import (
	"fmt"
	"io/ioutil"
	"net/http"

	"google.golang.org/appengine"
	"google.golang.org/appengine/log"
)

const (
	noTokenErr = "no telebot token"
)

func main() {
	http.HandleFunc("/", handle)
	http.HandleFunc("/_ah/health", healthCheckHandler)
	http.HandleFunc("/_ah/start", initHandler)
	appengine.Main()
}

func initHandler(w http.ResponseWriter, r *http.Request) {
	if InitBot(r); botToken == nil {
		http.Error(w, noTokenErr, 500)
		return
	}
	botToken.SetWebhook(r)
	http.NotFound(w, r)
}

func healthCheckHandler(w http.ResponseWriter, r *http.Request) {
	fmt.Fprint(w, "healthy")
}

func handle(w http.ResponseWriter, r *http.Request) {
	switch r.Method {
	case http.MethodGet:
		log.Debugf(appengine.NewContext(r), "get: %v", r.URL.Query())
	case http.MethodPost:
		if r.Body != nil {
			buf, err := ioutil.ReadAll(r.Body)
			if err != nil {
				log.Errorf(appengine.NewContext(r), "read body err: %v", err)
			} else {
				log.Debugf(appengine.NewContext(r), "body = %q", buf)
			}
		}
	}
	if botToken.ValidateWebhookURL(r) {
		// TODO
		w.WriteHeader(http.StatusOK)
		return
	}
	http.NotFound(w, r)
}
