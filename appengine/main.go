package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"regexp"
	"strconv"
	"strings"

	"cloud.google.com/go/datastore"
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
	startMsg       = `Please open this URL in NotifBot app (or copy paste this whole message into the Magic Box of the app): `
	startErrMsg    = `Failed to generate token, please try again later.`
	stopMsg        = `Connection deleted.`
	stopErrMsg     = `You did not run /start command yet.`

	msgTemplate = "From %s:\n%s"

	verifyContent = `[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "com.yhsif.notifbot",
    "sha256_cert_fingerprints": ["FB:36:90:89:B8:AA:02:69:EF:D6:6D:D6:C7:48:7E:D8:1A:26:6D:A5:07:18:62:04:3D:C7:70:8D:75:6A:2C:22"]
  }
}]`
)

var urlRegexp = regexp.MustCompile(clientPrefix + `(\d+)/(.+)`)

var dsClient *datastore.Client

// AppEngine log will auto add date and time, so there's no need to double log
// them in our own logger.
var (
	infoLog  = log.New(os.Stdout, "I ", log.Lshortfile)
	warnLog  = log.New(os.Stderr, "W ", log.Lshortfile)
	errorLog = log.New(os.Stderr, "E ", log.Lshortfile)
)

func main() {
	ctx := context.Background()
	if err := initDatastoreClient(ctx); err != nil {
		errorLog.Fatalf("Failed to get data store client: %v", err)
	}
	if err := initBot(ctx); err != nil {
		errorLog.Fatalf("Failed to init bot: %v", err)
	}
	if err := initRedis(ctx); err != nil {
		warnLog.Printf("Failed to init redis client: %v", err)
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/", rootHandler)
	mux.HandleFunc(webhookPrefix, webhookHandler)
	mux.HandleFunc(clientPrefix, clientHandler)
	mux.HandleFunc("/_ah/health", healthCheckHandler)
	mux.HandleFunc("/.well-known/assetlinks.json", verifyHandler)

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
		infoLog.Printf("Defaulting to port %s", port)
	}
	infoLog.Printf("Listening on port %s", port)
	go chatCounterMetricsLoop()
	infoLog.Fatal(http.ListenAndServe(fmt.Sprintf(":%s", port), mux))
}

func initDatastoreClient(ctx context.Context) error {
	var err error
	dsClient, err = datastore.NewClient(ctx, getProjectID())
	return err
}

func healthCheckHandler(w http.ResponseWriter, r *http.Request) {
	fmt.Fprint(w, "healthy")
}

func verifyHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	fmt.Fprint(w, verifyContent)
}

func webhookHandler(w http.ResponseWriter, r *http.Request) {
	if !botToken.ValidateWebhookURL(r) {
		http.NotFound(w, r)
		return
	}
	ctx := context.Background()

	if r.Body == nil {
		errorLog.Print("Empty webhook request body")
		http.NotFound(w, r)
		return
	}

	update := new(Update)
	if err := json.NewDecoder(r.Body).Decode(update); err != nil {
		errorLog.Printf("Unable to decode json: %v", err)
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

	chatCounter.Inc(id)

	ctx := context.Background()
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
	if !strings.HasPrefix(strings.TrimSpace(msg), strings.TrimSpace(label)) {
		// For some apps, the notification title will just be app name, so only add
		// label when that's not the case to avoid repetition.
		msg = fmt.Sprintf(msgTemplate, label, msg)
	}
	botToken.SendMessage(ctx, id, msg)
	w.WriteHeader(http.StatusOK)
}

func rootHandler(w http.ResponseWriter, r *http.Request) {
	http.NotFound(w, r)
}

func replyMessage(
	ctx context.Context,
	w http.ResponseWriter,
	orig Message,
	msg string,
	quote bool,
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
