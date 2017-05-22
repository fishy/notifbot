package main

import (
	"crypto/sha512"
	"encoding/base64"
	"fmt"
	"io/ioutil"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"time"

	"golang.org/x/net/context"
	"google.golang.org/appengine"
	"google.golang.org/appengine/datastore"
	"google.golang.org/appengine/log"
	"google.golang.org/appengine/memcache"
	"google.golang.org/appengine/runtime"
	"google.golang.org/appengine/urlfetch"
)

const (
	memcacheKeyPrefix = "token-"
	botKind           = "telebotkey"
	botID             = "AndroidNotificationBot"
	urlPrefix         = "https://api.telegram.org/bot"
	webhookMaxConn    = 5
)

var botToken *entityTelegramToken
var keyOnce sync.Once

type entityTelegramToken struct {
	Number int    `datastore:"Number,noindex"`
	Str    string `datastore:"String,noindex"`

	hashOnce   sync.Once
	hashPrefix string
}

func (e *entityTelegramToken) String() string {
	return fmt.Sprintf("%d:%s", e.Number, e.Str)
}

func (e *entityTelegramToken) getURL(endpoint string) string {
	return fmt.Sprintf("%s%s/%s", urlPrefix, e.String(), endpoint)
}

// PostRequest use POST method to send a request to telegram
func (e *entityTelegramToken) PostRequest(
	ctx context.Context, endpoint string, params url.Values,
) {
	runtime.RunInBackground(ctx, func(ctx context.Context) {
		start := time.Now()
		defer func() {
			log.Debugf(ctx, "http POST for %s took %v", endpoint, time.Now().Sub(start))
		}()
		client := urlfetch.Client(ctx)
		resp, err := client.PostForm(e.getURL(endpoint), params)
		if resp != nil && resp.Body != nil {
			defer DrainAndClose(resp.Body)
		}
		if err != nil {
			log.Errorf(ctx, "%s err: %v", endpoint, err)
			return
		}
		if resp.StatusCode != http.StatusOK {
			buf, _ := ioutil.ReadAll(resp.Body)
			log.Errorf(ctx, "%s failed: code = %d, body = %q", endpoint, resp.StatusCode, buf)
			return
		}
	})
}

// SendMessage sents a telegram messsage.
func (e *entityTelegramToken) SendMessage(
	ctx context.Context, id int64, msg string,
) {
	values := url.Values{}
	values.Add("chat_id", fmt.Sprintf("%d", id))
	values.Add("text", msg)
	e.PostRequest(ctx, "sendMessage", values)
}

func (e *entityTelegramToken) initHashPrefix(r *http.Request) {
	e.hashOnce.Do(func() {
		ctx := appengine.NewContext(r)

		hash := sha512.Sum512_224([]byte(e.String()))
		e.hashPrefix = webhookPrefix + base64.URLEncoding.EncodeToString(hash[:])
		log.Infof(ctx, "hashPrefix == %s", e.hashPrefix)
	})
}

func (e *entityTelegramToken) getWebhookURL(r *http.Request) string {
	e.initHashPrefix(r)
	return fmt.Sprintf("%s%s", globalURLPrefix, e.hashPrefix)
}

// ValidateWebhookURL validates whether requested URI in request matches hash
// path.
func (e *entityTelegramToken) ValidateWebhookURL(r *http.Request) bool {
	e.initHashPrefix(r)
	return r.URL.Path == e.hashPrefix
}

// SetWebhook sets webhook with telegram.
func (e *entityTelegramToken) SetWebhook(r *http.Request) {
	e.initHashPrefix(r)

	values := url.Values{}
	values.Add("url", e.getWebhookURL(r))
	values.Add("max_connections", fmt.Sprintf("%d", webhookMaxConn))
	e.PostRequest(appengine.NewContext(r), "setWebhook", values)
}

// InitBot initializes botToken.
func InitBot(r *http.Request) {
	keyOnce.Do(func() {
		ctx := appengine.NewContext(r)
		memKey := memcacheKeyPrefix + botID
		item, err := memcache.Get(ctx, memKey)
		if err == nil {
			entity, err := fromMemcacheValue(string(item.Value))
			if err == nil {
				botToken = entity
				return
			}
			log.Errorf(ctx, "invalid memcache data for key %s: %q", memKey, item.Value)
		}
		if err != memcache.ErrCacheMiss {
			log.Errorf(ctx, "failed to get memcache key %s: %v", memKey, err)
		}
		key := datastore.NewKey(ctx, botKind, botID, 0, nil)
		e := new(entityTelegramToken)
		if err := datastore.Get(ctx, key, e); err != nil {
			log.Errorf(ctx, "failed to get datastore key %v: %v", key, err)
			return
		}
		botToken = e
		item = &memcache.Item{
			Key:   memKey,
			Value: botToken.toMemcacheValue(),
		}
		if err := memcache.Set(ctx, item); err != nil {
			log.Errorf(ctx, "failed to set memcache key %s: %v", memKey, err)
		}
	})
}

func (e *entityTelegramToken) toMemcacheValue() []byte {
	return []byte(e.String())
}

func fromMemcacheValue(data string) (*entityTelegramToken, error) {
	index := strings.IndexAny(data, ":")
	if index < 0 {
		return nil, fmt.Errorf("invalid data: %q", data)
	}
	num, err := strconv.ParseInt(data[:index], 10, 32)
	if err != nil {
		return nil, err
	}
	return &entityTelegramToken{
		Number: int(num),
		Str:    data[index+1:],
	}, nil
}
