package main

import (
	"context"
	"fmt"
	"net/http"
	"regexp"
	"strconv"
	"strings"

	"cloud.google.com/go/datastore"
	"google.golang.org/appengine/v2"
)

const (
	globalURLPrefix = `https://notifbot.fishy.me`
	clientPrefix    = `/c/`

	fieldLabel = "label"
	fieldMsg   = "msg"

	playURL = `https://play.google.com/store/apps/details?id=com.yhsif.notifbot`

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

var urlRegexp = regexp.MustCompile(clientPrefix + `(\-?\d+)/(.+)`)

var dsClient *datastore.Client

func main() {
	initLogger()

	ctx := context.Background()
	if err := initDatastoreClient(ctx); err != nil {
		l(ctx).Fatalw(
			"Failed to get data store client",
			"err", err,
		)
	}
	initBot(ctx)

	http.HandleFunc("/", rootHandler)
	http.HandleFunc(clientPrefix, clientHandler)
	http.HandleFunc("/_ah/health", healthCheckHandler)
	http.HandleFunc("/.well-known/assetlinks.json", verifyHandler)
	go chatCounterMetricsLoop()

	appengine.Main()
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

func clientHandler(w http.ResponseWriter, r *http.Request) {
	ctx := logContext(r)

	groups := urlRegexp.FindStringSubmatch(r.URL.Path)
	if len(groups) == 0 {
		http.NotFound(w, r)
		return
	}
	id, err := strconv.ParseInt(groups[1], 10, 64)
	if err != nil {
		http.NotFound(w, r)
		return
	}
	ctx = logContextWith(ctx, "chat-id", id)

	chatCounter.Inc(id)

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
	if getToken().SendMessage(ctx, id, msg) == http.StatusUnauthorized {
		l(ctx).Info("Invalidating secret cache and retrying...")
		initBot(ctx)
		getToken().SendMessage(ctx, id, msg)
	}
	w.WriteHeader(http.StatusOK)
}

func rootHandler(w http.ResponseWriter, r *http.Request) {
	http.NotFound(w, r)
}
