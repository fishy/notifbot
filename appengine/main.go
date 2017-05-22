package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"regexp"
	"strconv"

	"golang.org/x/net/context"
	"google.golang.org/appengine"
	"google.golang.org/appengine/log"
)

const (
	errNoToken = "no telebot token"

	globalURLPrefix = `https://notification-bot.appspot.com`
	webhookPrefix   = `/w/`
	clientPrefix    = `/c/`

	fieldLabel = "label"
	fieldMsg   = "msg"

	playURL        = `https://play.google.com/store/apps/details?id=com.yhsif.notifbot`
	unsupportedMsg = `unsupported message/command`
	downloadMsg    = `Download NotifBot Android app at: ` + playURL
	startMsg       = `Please open this URL in NotifBot app: `
	startErrMsg    = `Failed to generate token, please try again later.`
	stopMsg        = `Connection deleted.`
	stopErrMsg     = `You did not run /start command yet.`

	msgTemplate = "%s says:\n%s"
)

var urlRegexp = regexp.MustCompile(clientPrefix + `(\d+)/(.+)`)

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
	if !botToken.ValidateWebhookURL(r) {
		http.NotFound(w, r)
		return
	}
	ctx := appengine.NewContext(r)

	if r.Body == nil {
		log.Errorf(ctx, "empty webhook request body")
		http.NotFound(w, r)
		return
	}

	buf := new(bytes.Buffer)
	if _, err := io.Copy(buf, r.Body); err != nil {
		log.Errorf(ctx, "read webhook request err: %v", err)
		http.NotFound(w, r)
		return
	}

	update := new(Update)
	if err := json.NewDecoder(buf).Decode(update); err != nil {
		log.Errorf(ctx, "unable to decode json %q: %v", buf.String(), err)
		http.NotFound(w, r)
		return
	}

	switch update.Message.Text {
	default:
		replyMessage(ctx, w, update.Message, unsupportedMsg, true)
	case "/download":
		replyMessage(ctx, w, update.Message, downloadMsg, false)
	case "/start":
		chat := NewChat(ctx, update.Message.Chat.ID)
		if chat == nil {
			replyMessage(ctx, w, update.Message, startErrMsg, true)
			return
		}
		replyMessage(ctx, w, update.Message, startMsg+chat.GetURL(), false)
	case "/stop":
		chat := GetChat(ctx, update.Message.Chat.ID)
		if chat == nil {
			replyMessage(ctx, w, update.Message, stopErrMsg, true)
			return
		}
		chat.Delete(ctx)
		replyMessage(ctx, w, update.Message, stopMsg, false)
	}
}

func clientHandler(w http.ResponseWriter, r *http.Request) {
	groups := urlRegexp.FindStringSubmatch(r.URL.Path)
	if groups == nil || len(groups) == 0 {
		http.NotFound(w, r)
		return
	}
	id, err := strconv.ParseInt(groups[1], 10, 64)
	if err != nil {
		http.NotFound(w, r)
		return
	}

	ctx := appengine.NewContext(r)
	chat := GetChat(ctx, id)
	if chat == nil || chat.Token != groups[2] {
		http.NotFound(w, r)
		return
	}

	if r.Method != http.MethodPost {
		w.Header().Set("Location", playURL)
		w.WriteHeader(http.StatusSeeOther)
		return
	}

	label := r.FormValue(fieldLabel)
	msg := r.FormValue(fieldMsg)
	if label == "" || msg == "" {
		http.NotFound(w, r)
		return
	}
	botToken.SendMessage(ctx, id, fmt.Sprintf(msgTemplate, label, msg))
	w.WriteHeader(http.StatusOK)
}

func rootHandler(w http.ResponseWriter, r *http.Request) {
	http.NotFound(w, r)
}

func replyMessage(
	ctx context.Context, w http.ResponseWriter, orig Message, msg string, quote bool,
) {
	reply := ReplyMessage{
		Method: "sendMessage",
		ChatID: orig.Chat.ID,
		Text:   msg,
	}
	if quote {
		reply.ReplyTo = orig.ID
	}
	w.Header().Add("Content-Type", "application/json")
	json.NewEncoder(w).Encode(reply)
}
